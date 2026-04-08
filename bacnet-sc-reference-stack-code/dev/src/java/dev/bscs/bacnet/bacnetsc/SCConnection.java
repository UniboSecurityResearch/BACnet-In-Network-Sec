// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.bacnetsc;

import dev.bscs.bacnet.stack.constants.ErrorClass;
import dev.bscs.bacnet.stack.constants.ErrorCode;
import dev.bscs.common.Formatting;
import dev.bscs.common.Shell;
import dev.bscs.common.Timer;
import dev.bscs.events.EventListener;
import dev.bscs.events.EventLoop;
import dev.bscs.events.EventType;
import dev.bscs.websocket.BSWebSocket;

import java.net.SocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.*;

/**
 * The heart of BACnet/SC: This implements the initiating and accepting state machines in figure YY-11 and YY-12.
 * A connection is used in a variety of places and is thus "owned" by a higher level function, like {@link SCHubConnector},
 * {@link SCDirectConnector}, and {@link SCServer} which is the common super class for {@link SCHubFunction} and {@link SCNodeSwitch}.
 * These higher level functions implement the {@link SCConnectionOwner} interface, which is how the SCConnection calls back to its
 * owner with various events and state changes. Even though all the interactions with the owner are in the same thread, to
 * keep the state machine clean and all in one place, calls to methods like {@link #initiateWebSocket} actually fire events themselves
 * that the state machine processes from its one entry point.  This keeps "side effects" out of the state machine and all transitions,
 * that are designed to match the standard as much as possible, can be seen in one place.
 * @author drobin
 */
public  class SCConnection implements EventListener {

    private static SCLog log = new SCLog(SCConnection.class);
    public  String            name;
    public  int               number;
    public  SCNode            node;
    public  SCProperties      properties;
    public  boolean           direct;
    public  SCConnectionOwner owner;
    private BSWebSocket       socket;
    public  boolean           initiated;
    public  URI               uri;
    public  String            remoteAddress;
    public  String            failure="";

    public  State             state;
    public  enum State  { IDLE, AWAITING_WEBSOCKET, AWAITING_REQUEST, AWAITING_ACCEPT, CONNECTED, DISCONNECTING }

    // these are used internally to turn asynchronous method calls into queued state machine events
    private static final EventType EVENT_LOCAL_CLOSE        = new EventType("connection_close");
    private static final EventType EVENT_LOCAL_INITIATE     = new EventType("connection_initiate");
    private static final EventType EVENT_LOCAL_ACCEPT       = new EventType("connection_accept");
    private static final EventType EVENT_LOCAL_DISCONNECT   = new EventType("connection_disconnect");
    private static final EventType EVENT_LOCAL_STATE_CHANGE = new EventType("connection_state_change");

    public SCVMAC  peerVMAC;       // set by connect request or accept
    public UUID    peerUUID;       // set by connect request or accept
    public int     peerMaxBVLC;    // set by connect request or accept
    public int     peerMaxNPDU;    // set by connect request or accept

    public Timer keepAliveTimer;
    public Timer timer;

    private static int nextNumber = 1;
    private int        expectedConnectAcceptMessageID;
    private int        expectedDisconnectMessageID;
    private int        expectedHeartbeatMessageID;

    public SCConnection(SCConnectionOwner owner, String purpose, SCNode node, SCProperties properties) {
        this.node                  = node;
        this.owner                 = owner;
        this.properties            = properties;
        this.peerVMAC              = null;
        this.peerUUID              = new UUID(0L,0L);
        this.peerMaxNPDU           = SCProperties.DEFAULT_MAX_NPDU;
        this.peerMaxBVLC           = SCProperties.DEFAULT_MAX_BVLC;
        this.keepAliveTimer        = new Timer();
        this.timer                 = new Timer();
        this.number                = nextNumber++;
        this.name                  = purpose+"#" +number;
        this.state                 = State.IDLE;
    }

    public State   getState()    { return state; }

    public boolean isConnected() { return state == State.CONNECTED; }

    public boolean isClosed()    { return state == State.IDLE; }

    public void initiateWebSocket(URI uri, boolean direct) {
        log.info(this,"initiateWebSocket("+uri+","+direct+")");
        EventLoop.emit(this,this,EVENT_LOCAL_INITIATE,uri,direct);
    }

    public void acceptWebSocket(BSWebSocket acceptedSocket, boolean direct) {
        log.info(this,"acceptWebSocket("+acceptedSocket.name+","+direct+")");
        // this is an immediate call so as to not miss a clientMessage event queued immediately after the serverOpen event
        handleEvent(this,EVENT_LOCAL_ACCEPT,acceptedSocket,direct);
    }

    public void disconnect() {  // cause "local Disconnection" per YY.6.2.2 with Disconnect/Disconnect-ACK messages
        log.debug(this,"disconnect() called, emitting event");
        EventLoop.emit(this, this,EVENT_LOCAL_DISCONNECT);
    }

    public void close() { // abrupt termination without "disconnect" phase - used for duplicate UUID, etc.
        log.debug(this,"close() called, emitting event");
        EventLoop.cancelEventsFrom(this); // be sure to cancel any queue of outstanding things that could get out of sync, like "CONNECTED"!
        EventLoop.cancelEventsTo(this);   // a call to close preempts anything pending
        EventLoop.emit(this,this,EVENT_LOCAL_CLOSE);
    }

    @Override public void handleEvent(Object source, EventType eventType, Object... args) {
        // This is for both the initiating and accepting state machines, as shown in figures YY-11 and YY-12.
        // These are so similar it makes sense to do them together.
        // The initiateWebSocket() and acceptWebSocket() calls, and thus the 'initiated' flag, will determine which mode it operates in.

        // First check things that are not based on state...
        // YY-11 and YY-12 "WebSocket Failure" transition says if our web socket is closed unexpectedly, return to IDLE
        if (eventType == BSWebSocket.EVENT_SOCKET_CLOSE) {
            String reason = socket == null? "EVENT_SOCKET_CLOSE on null socket" : "EVENT_SOCKET_CLOSE WebSocket socket "+socket.hashCode()+" with "+socket.getRemoteAddress()+" closed with " + (socket.isClosedRemotely()? "remote" : "local") + " code " + socket.getCloseCode() + " reason " + socket.getCloseReason();
            setState(State.IDLE, reason);
            return;
        }
        if (socket != null && socket.isClosed()) { // poll as a back up in case missed event
            String reason = "WebSocket socket "+socket.hashCode()+" with "+socket.getRemoteAddress()+" was detected closed with " + (socket.isClosedRemotely()? "remote" : "local") + " code " + socket.getCloseCode() + " reason \"" + socket.getCloseReason()+"\"";
            setState(State.IDLE, reason);
            return;
        }
        if (eventType == EVENT_LOCAL_CLOSE) { // check for instructions to abruptly "close connection" without DISCONNECTING phase
            closeChannel();
            setState(State.IDLE, "local close() event handled");
            return;
        }
        // just a bit of event flow sanity checking here... more needed?
        if (eventType == EVENT_LOCAL_INITIATE && state != State.IDLE) log.implementation(this, "initiateWebSocket() called in non-IDLE state!");
        if (eventType == EVENT_LOCAL_ACCEPT && state != State.IDLE) log.implementation(this, "acceptWebSocket() called in non-IDLE state!");
        // several states want to check messages, so we'll do it here up front.
        // and, this also gives us a single place to check for error injections.
        SCMessage message = null;
        if (eventType == BSWebSocket.EVENT_SOCKET_MESSAGE) {
            message = parseMessage((ByteBuffer) args[0]); // will send NAKs for badly formatted messages
            message = SCErrorInjection.checkIncoming(this,message); // check for message rejection or dropping
        }
        // now run the state machine
        switch(state) {

            case IDLE:
                if (peerVMAC == null) { // this should really be done *on the way* to IDLE, but but I didn't want to bury this in setState().
                    peerUUID              = new UUID(0L,0L);
                    peerMaxNPDU           = SCProperties.DEFAULT_MAX_NPDU;
                    peerMaxBVLC           = SCProperties.DEFAULT_MAX_BVLC;
                }
                if (eventType == EVENT_LOCAL_INITIATE) { // YY.6.2.2 "Initiating a WebSocket" transition
                    this.uri    = (URI)args[0];
                    this.direct = (Boolean)args[1];
                    failure   = "";    // optimist until proven otherwise
                    initiated = true;  // indicates "direction" of connection
                    socket = BSWebSocket.newInstance(name,this);
                    log.debug("Made new socket "+socket.hashCode()+ " for " + name);
                    // only allow "wss", or non-standard "ws" and properties.noValidation
                    if (uri.getScheme().equalsIgnoreCase("wss")) socket.setSSLOptions(node.getTLSManager().getSSLContext(),!properties.noValidation,new String[]{properties.tlsVersion},null);
                    else if (uri.getScheme().equalsIgnoreCase("ws") && properties.allowPlain) log.info(this,"Using non-standard plain WebSocket URI '"+uri+"'");
                    else { log.configuration(this,"Incorrect scheme in URI '"+uri+"'"); break; } // do not change state
                    String subprotocol = direct ? "dc.bsc.bacnet.org" : "hub.bsc.bacnet.org";
                    subprotocol = SCErrorInjection.checkSubProtocol(subprotocol); // give error injection a chance to mess it up
                    socket.connect(uri, subprotocol);
                    setState(State.AWAITING_WEBSOCKET, properties.connectionWaitTimeout, "initiateWebSocket via socket "+socket.hashCode());
                    break;
                }
                if (eventType == EVENT_LOCAL_ACCEPT) { // YY.6.2.3 "WebSocket Accepted" transition
                    socket = (BSWebSocket)args[0];
                    socket.setListener(this);
                    direct  = (Boolean)args[1];
                    failure   = "";    // acceptance always starts out well
                    initiated = false; // indicates "direction" of connection
                    SocketAddress remoteSocketAddress = socket.getRemoteAddress();
                    remoteAddress = remoteSocketAddress != null? remoteSocketAddress.toString() : "Unknown";
                    setState(State.AWAITING_REQUEST, properties.connectionWaitTimeout, "acceptWebSocket with socket "+socket.hashCode()+" from "+remoteAddress);
                    break;
                }
                break;

            case AWAITING_WEBSOCKET:
                // EXTENSION: not in YY-11 but should be: "Connection Wait Timeout Expired" transition
                if (timer.expired()) {
                    socket.close();
                    setState(State.IDLE, "Connection with socket "+socket.hashCode()+" gave up waiting for WebSocket connection in state AWAITING_WEBSOCKET");
                    break;
                }
                // check YY.6.2.2 "WebSocket established" transition
                if (socket.isConnected()) {
                    SocketAddress remoteSocketAddress = socket.getRemoteAddress();
                    if (remoteAddress != null) remoteAddress = remoteSocketAddress.toString();
                    else remoteAddress = "Unknown";
                    expectedConnectAcceptMessageID = sendConnectRequest(properties.vmac, node.getUUID(), properties.maxBVLCLengthAccepted, properties.maxNPDULengthAccepted).id;
                    setState(State.AWAITING_ACCEPT, properties.connectionWaitTimeout, "WebSocket Established to "+remoteAddress + " with socket "+socket.hashCode());
                }
                break;

            case AWAITING_ACCEPT:
                // check YY-11 "Connection Wait Timeout Expired" transition
                if (timer.expired()) {
                    socket.close();
                    setState(State.IDLE, "Connection gave up on socket "+socket.hashCode()+" waiting for Connect-Accept in state AWAITING_ACCEPT");
                    break;
                }
                // any messages?
                if (message == null) break; // no message = nothing else to do
                switch (message.function) {
                    case SCMessage.CONNECT_ACCEPT:
                        if (message.id != expectedConnectAcceptMessageID) {
                            protocolViolationLogOnly(message, ErrorCode.INVALID_MESSAGE_ID, "Message ID in Connect-Accept does not match Connect-Request");
                            break;
                        }
                        // parse payload
                        SCPayloadConnectAccept accept = new SCPayloadConnectAccept(message.payload);
                        peerVMAC = accept.vmac; // TODO what happens if this VMAC is a duplicate?
                        peerUUID = accept.uuid; // TODO what happens if this UUID is a duplicate?
                        peerMaxNPDU = accept.maximumNPDULength;
                        peerMaxBVLC = accept.maximumBVLCLength;
                        keepAliveTimer.start(properties.initiatingHeartbeatTimeout);
                        // now take the "Connect-Accept received" transition.  Yay, progress!
                        setState(State.CONNECTED, "Connect-Accept received");
                        break;
                    case SCMessage.BVLC_RESULT:
                        // parse payload
                        SCPayloadBVLCResult result = new SCPayloadBVLCResult(message.payload);
                        // check for transition "BVLC message received other than a Disconnect-Request, a Disconnect-ACK, or a response to the Connect-Request initiated" - discard message
                        if (result.forFunction != SCMessage.CONNECT_REQUEST) {
                            protocolViolationLogOnly(message, "BVLC-Result forFunction code " + result.forFunction + " is unexpected in state WAITING_ACCEPT");
                            break;
                        }
                        // If ID is wrong, we can say it's a violation because there are no other outstanding messages in this state
                        if (message.id != expectedConnectAcceptMessageID) {
                            protocolViolationLogOnly(message, ErrorCode.INVALID_MESSAGE_ID, "Message ID in BVLC-Result does not match Connect-Request");
                            break;
                        }
                        // check for transition "BVLC-Result NAK, VMAC collision"
                        if (result.errorCode == ErrorCode.NODE_DUPLICATE_VMAC) {
                            if (direct) {
                                // YY.6.2.2 requires that: "...BACnet/SC node shall choose a new Random-48 VMAC before a reconnection is attempted."
                                // This could potentially be really bad if a buggy DC acceptor says this repeatedly, causing us to constantly drop
                                // all connections in order to start up with a new VMAC (and invalidate all previous I-Am bindings!)
                                // So... in this implementation, we treat *hub* connections as trustworthy and will change if the hub says so,
                                // but since direct connections are optional anyway, if a DC peer says to change we will just drop the connection.
                                // Also, since every node will have to connect to the hub anyway, it's up the *those* connections so sort
                                // out all the conflicts. The direct connections will then work at a later time.
                                log.warning(this, "Connect-Request rejected as duplicate VMAC for *direct* connection; aborting connection without change");
                                // we can't send a NAK to NAK, but we can include a message in the WebSocket close()
                                closeChannel(BSWebSocket.CLOSE_CODE_CLOSED_BY_PEER, "Changing VMACs is not supported for Direct Connections... We'll let the hub sort it out.");
                                setState(State.IDLE, "Rejected as Duplicate VMAC");
                            } else {
                                log.warning(this, "Connect-Request rejected as duplicate VMAC; picking new VMAC");
                                // we can't send a NAK to NAK, but we can include a message in the WebSocket close()
                                closeChannel(BSWebSocket.CLOSE_CODE_CLOSED_BY_PEER, "VMAC changing... I'll be back");
                                setState(State.IDLE, "Rejected as Duplicate VMAC");
                                node.restartWithNewVMAC(); // this will cause the entire datalink to stop and restart with a new VMAC
                            }
                            break;
                        }
                        // we don't expect any other kind of NAK, but can't send an error response, so just log it
                        protocolViolationLogOnly(message, "Unexpected BVLC_RESULT error code of " + result.errorCode + " in WAITING_ACCEPT state");
                        break; // discard message

                    case SCMessage.DISCONNECT_REQUEST:
                        // how rude! we haven't even connected yet
                        closeChannel(BSWebSocket.CLOSE_CODE_CLOSED_BY_PEER, "Disconnect-Request received");
                        setState(State.IDLE, "Disconnect-Request received");
                        break;

                    case SCMessage.DISCONNECT_ACK:
                        // we certainly weren't expecting this in this state so log it as an error
                        protocolViolationLogOnly(message, "Received Disconnect-Ack in AWAITING_ACCEPT state");
                        // there's no point in checking message id - we didn't initiate a Disconnect-Request!
                        closeChannel(BSWebSocket.CLOSE_CODE_PROTOCOL_ERROR, "Received Disconnect-Ack in AWAITING_ACCEPT state");
                        setState(State.IDLE, "Disconnect-ACK received");
                        break;

                    default:  // transition "BVLC message received other than a Disconnect-Request, a Disconnect-ACK, or a response to the Connect-Request initiated"
                        protocolViolationLogOnly(message, "Unexpected message " + message.function + " received in WAITING_ACCEPT state");
                        break; // discard message

                }
                break;

            case CONNECTED:
                if (keepAliveTimer.expired()) {
                    if (initiated) {
                        initiateHeartbeat();
                        keepAliveTimer.restart();
                    } else {
                        String reason = "Keep-alive expired at accepting socket";
                        socket.close(BSWebSocket.CLOSE_CODE_PROTOCOL_ERROR,reason);
                        setState(State.IDLE, reason);
                        break;
                    }
                }
                if (eventType == EVENT_LOCAL_DISCONNECT) { // check transition "local Disconnection"
                    expectedDisconnectMessageID = sendDisconnectRequest().id;
                    setState(State.DISCONNECTING, properties.disconnectWaitTimeout, "Local disconnection");
                    break;
                }
                if (message == null) break; // no message = nothing else to do
                switch (message.function) {
                    case SCMessage.DISCONNECT_REQUEST: // "Disconnect-Request" received transition
                        sendDisconnectAck(message.id);
                        closeChannel(BSWebSocket.CLOSE_CODE_CLOSED_BY_PEER, "Disconnect-Request received");
                        setState(State.IDLE,"Disconnect-Request received");
                        break;
                    case SCMessage.DISCONNECT_ACK: // what?
                        // This is unexpected! We can't NAK it if it's bad so we'll just log a complaint
                        // YY.6.2.2 CONNECTED state doesn't have a transition for this situation.
                        // We'll assume that the peer is confused and thought we sent a Disconnect-Request so we'll
                        // close and hope that it clears itself up
                        protocolViolationLogOnly(message,"Unexpected Disconnect-ACK received");
                        closeChannel(BSWebSocket.CLOSE_CODE_CLOSED_BY_PEER, "Unexpected Disconnect-ACK received");
                        setState(State.IDLE,"Unexpected Disconnect-ACK received");
                        break;
                    case SCMessage.ENCAPSULATED_NPDU:
                        if (owner != null) owner.incoming(this,message);
                        break;
                    case SCMessage.HEARTBEAT_REQUEST:
                        sendHeartbeatAck(message.id);
                        break;
                    case SCMessage.HEARTBEAT_ACK:
                        // We already reset keepAliveTimer in parseMessage() so nothing really more to do there.
                        // We can't NAK it if it's bad so we'll just log a complaint
                        if (message.id != expectedHeartbeatMessageID) protocolViolationLogOnly(message,"Message ID of their Heartbeat-ACK does not match our Heartbeat-Request");
                        break;
                    case SCMessage.BVLC_RESULT:
                        if (message.destination == null && message.originating == null) {
                            // No addresses, so it's for us at the connection level (not for our node or from another node).
                            // But we're not expecting any BVLC-Result messages in this state so we'll just log this as a
                            // curiosity because we don't know what to do to "fix" the problem anyway.
                            protocolViolationLogOnly(message,"Unexpected connection-level BVLC-Result received in connected state");
                            break;
                        }
                        // It's contains 'destination' or 'originating' so we'll let the owner figure out what to do with it.
                        // e.g., a Hub Connector or Node Switch will check to see if the destination is this node or discard,
                        // and a Hub Function will attempt to switch the result to the right destination.
                        if (owner != null) owner.incoming(this,message);
                        break;
                    case SCMessage.ADDRESS_RESOLUTION:
                    case SCMessage.ADDRESS_RESOLUTION_ACK:
                    case SCMessage.ADVERTISEMENT_SOLICITATION:
                    case SCMessage.ADVERTISEMENT:
                        if (owner != null) owner.incoming(this,message);
                        break;
                    default:
                        protocolViolationLogOnly(message,"Unexpected function code " + message.function + " received in CONNECTED state - ignoring");
                        break;
                }
                break;

            case DISCONNECTING:
                // check YY-11 "Disconnect Wait Timeout expired" transition
                if (timer.expired()) {
                    socket.close();
                    setState(State.IDLE, "Connection gave up on socket "+socket.hashCode()+" waiting for Disconnect-ACK in state DISCONNECTING");
                    break;
                }
                if (message == null) break; // no message = nothing else to do
                switch (message.function) {
                    case SCMessage.DISCONNECT_ACK:  // "Disconnect-ACK received" transition
                        if (message.id != expectedDisconnectMessageID) {
                            protocolViolationLogOnly(message,"Message ID in their Disconnect-ACK does not match our Disconnect-Request");
                            closeChannel(BSWebSocket.CLOSE_CODE_PROTOCOL_ERROR,"Message ID in your Disconnect-ACK did not match our Disconnect-Request");
                        }
                        else {
                            closeChannel(BSWebSocket.CLOSE_CODE_CLOSED_BY_PEER,"Disconnect-ACK received");
                        }
                        setState(State.IDLE, "Disconnect-ACK received");
                        break;
                    case SCMessage.BVLC_RESULT: // check for "BVLC-Result NAK received"
                        // this is kind of dumb, but...
                        // to be faithful to the spec, we are supposed to check for a NAK and ignore ACKs.
                        // so parse payload
                        SCPayloadBVLCResult result = new SCPayloadBVLCResult(message.payload);
                        // check for transition "On receipt of a Result-NAK response to the Disconnect-Request"
                        if (result.forFunction == SCMessage.DISCONNECT_REQUEST && result.isNak()) {
                            closeChannel();
                            setState(State.IDLE, "BVLC-Result NAK received in DISCONNECTING state");
                        }
                        // theoretically, in this state we are allowed to be completing our "initiations" of messages but not accepting
                        // anything new.  So a BVLC-Result other than to the Disconnect-Request *might* be for an outstanding initiated
                        // message, so we'll dutifully deliver it to the owner for it to (likely) ignore.
                        if (owner != null) owner.incoming(this,message);
                        break;
                    default:  // all other incoming messages are ignored
                        break;
                }
                break;

            case AWAITING_REQUEST:
                // YY.6.2.3 "Connect Wait Timeout expired" transition
                if (timer.expired()) {
                    socket.close();
                    setState(State.IDLE, "Connection gave up waiting on socket "+socket.hashCode()+" for Connection-Request in state AWAITING_REQUEST");
                    break;
                }
                if (message == null) break; // no message = nothing else to do
                switch (message.function) {
                    case SCMessage.CONNECT_REQUEST:
                        // three "Connect-Request received, ..." transitions checked below
                        SCPayloadConnectRequest request = new SCPayloadConnectRequest(message.payload);
                        // check for "Connect-Request received, known Device UUID"
                        SCConnection existing = owner!=null?  owner.findConnectionFor(request.uuid) : null;
                        if (existing != null) {
                            log.info(this, "Accepting connection from known uuid " + request.uuid + " with vmac "+  request.vmac+ " and closing existing connection "+  existing.name);
                            // this is the order dictated by the spec: it says to accept the new then "disconnect and close" the old
                            sendConnectAccept(message.id);
                            existing.disconnect(); // spec says "disconnect" rather then "close" so this will be a little overlap
                            keepAliveTimer.start(properties.acceptingHeartbeatTimeout);
                            peerVMAC = request.vmac;
                            peerUUID = request.uuid;
                            peerMaxNPDU = request.maxNPDU;
                            peerMaxBVLC = request.maxBVLC;
                            setState(SCConnection.State.CONNECTED,"Connect-Request accepted from known UUID");
                            break;
                        }
                        // check "Connect-Request received, new Device UUID, VMAC collision" transition
                        existing = owner != null? owner.findConnectionFor(request.vmac) : null;
                        // it's a duplicate if...
                        // you have an existing connection from that VMAC...
                        if ( existing != null) {
                            log.warning(this, "Rejecting connection of duplicate vmac " + request.vmac + " from uuid " + request.uuid + ", vmac is in use by uuid "+existing.peerUUID);
                            sendError(null, null, message.function, 0, ErrorClass.COMMUNICATION, ErrorCode.NODE_DUPLICATE_VMAC, "vmac is in use by uuid "+existing.peerUUID, message.id);
                            closeChannel();
                            break;
                        }
                        // ...OR, the the requested vmac is *yours* (even if you're not yet connected) AND it's not *you* making the request
                        if (request.vmac.equals(properties.vmac) && !request.uuid.equals(node.getUUID())) {
                            log.warning(this, "Rejecting connection of a duplicate of this port's vmac " + request.vmac + " from uuid " + request.uuid);
                            sendError(null, null, message.function, 0, ErrorClass.COMMUNICATION, ErrorCode.NODE_DUPLICATE_VMAC, "vmac is, or will be, in use by this port", message.id);
                            closeChannel();
                            break;
                        }
                        // else this is the "Connect-Request received, new Device UUID, no VMAC collision" transition
                        log.info(this, "Accepting connection from new uuid " + request.uuid + " with vmac " +request.vmac);
                        peerVMAC = request.vmac;
                        peerUUID = request.uuid;
                        peerMaxNPDU = request.maxNPDU;
                        peerMaxBVLC = request.maxBVLC;
                        sendConnectAccept(message.id);
                        keepAliveTimer.start(properties.acceptingHeartbeatTimeout);
                        setState(SCConnection.State.CONNECTED,"connected");
                        break;

                    default:
                        // spec doesn't say what to do with other messages, so we'll just log an anomaly and ignore it
                        protocolViolationLogOnly(message, "Unexpected message in AWAITING_REQUEST state");
                        break;
                }
                break;
        }
    }

    ///////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////

    private void   setState(State state, String reason) {
        setState(state,0,reason);
    }

    private void   setState(State state, int timeout, String reason) {
        log.info(this," is changing state to "+state+(timeout!=0?" for "+timeout:"")+" because: "+reason);
        this.state = state;
        EventLoop.emit(this,this,EVENT_LOCAL_STATE_CHANGE);
        if (state == State.IDLE) {
            EventLoop.removeMaintenance(this);
            if (owner != null) owner.connectionClosed(this);  // TODO I don't like this buried here
        }
        else {
            EventLoop.addMaintenance(this);
            if (state == State.CONNECTED && owner != null) owner.connectionEstablished(this); // TODO I don't like this buried here
        }
        if (timeout != 0) timer.start(timeout); else timer.clear();

    }

    private void   closeChannel() {
        if (socket != null && !socket.isClosed()) socket.close();
        socket = null;
    }

    private void   closeChannel(int code, String reason) {
        if (socket != null && !socket.isClosed()) socket.close(code,reason);
        socket = null;
    }

    private SCMessage parseMessage(ByteBuffer buf) {
        SCMessage message = new SCMessage(buf);
        // parsing can return partial failure (does not throw exception)
        if (message.parseError) {
            // not really sure if we can trust message.originating as the destination of this response, but, ...
            protocolViolationLogAndSend(message, message.originating, null, message.parseErrorCode, message.parseErrorReason); // will send NAK if allowed
            return null;
        }
        // We CAN'T check options here because the standard says "destination options" are checked by the
        // "destination BACnet/SC node", i.e., NOT the connection peer. An earlier draft had "peer options" that was
        // removed in favor of using new function codes if additions to connection-level messages are absolutely needed.
        // But we can check that data options is *not* present for non-NPDU messages
        if (message.function != SCMessage.ENCAPSULATED_NPDU && message.dataOptions != null) {
            protocolViolationLogAndSend(message, message.originating, null, ErrorCode.INCONSISTENT_PARAMETERS, "Data options present with non-NPDU"); // will send NAK if allowed
            return null;
        }
        keepAliveTimer.restart(); // every valid message received, regardless of meaningfulness, resets keepalive timer for initiating peer
        log.info(this,"--> parseMessage " + message + " ["+Formatting.toHex(buf.array())+"]");
        switch (message.function) {

            // non-switchable messages - no addresses present
            case SCMessage.DISCONNECT_ACK:
            case SCMessage.DISCONNECT_REQUEST:
            case SCMessage.HEARTBEAT_REQUEST:
            case SCMessage.HEARTBEAT_ACK:
            case SCMessage.CONNECT_REQUEST:
            case SCMessage.CONNECT_ACCEPT:
                // check addresses
                if (message.destination != null) { protocolViolationLogAndSend(message, null, null, ErrorCode.HEADER_ENCODING_ERROR, "destination field must be absent"); return null; }
                if (message.originating != null) { protocolViolationLogAndSend(message, null, null, ErrorCode.HEADER_ENCODING_ERROR, "originating field must be absent"); return null; }
                // check payload
                if (message.function == SCMessage.CONNECT_REQUEST || message.function == SCMessage.CONNECT_ACCEPT) {
                    // YY.3.1.5 Common Error Situations "If a BVLC message is received for which a payload is required, but no payload is present..."
                    if (message.payload == null) { protocolViolationLogAndSend(message, null, null, ErrorCode.PAYLOAD_EXPECTED, "payload must be present"); return null; }
                }
                else {
                    if (message.payload != null) { protocolViolationLogAndSend(message, null, null, ErrorCode.UNEXPECTED_DATA, "payload must be absent"); return null; }
                }
                return message;

            // switchable messages - address requirements are dependent on context and direction
            case SCMessage.ENCAPSULATED_NPDU:
            case SCMessage.ADVERTISEMENT:
            case SCMessage.ADDRESS_RESOLUTION_ACK:
            case SCMessage.BVLC_RESULT:
            case SCMessage.ADDRESS_RESOLUTION:
            case SCMessage.ADVERTISEMENT_SOLICITATION:
                // check addresses
                if (direct) { // direct connection - both must be absent always, per YY.2.1 (unless the leniency of YY.4.2.2 is allowed)
                    if (message.destination != null) {
                        if (properties.allowYY422destination) {
                            if (!message.destination.equals(properties.vmac)) { protocolViolationLogAndSend(message, null, null, ErrorCode.HEADER_ENCODING_ERROR, "Optional(YY.4.2.2) destination field in Direct Connection does not match this node"); return null; }
                        }
                        else { protocolViolationLogAndSend(message, null, null, ErrorCode.HEADER_ENCODING_ERROR, "destination field must be absent in Direct Connection"); return null; }
                    }
                    if (message.originating != null) { protocolViolationLogAndSend(message, null, null, ErrorCode.HEADER_ENCODING_ERROR, "originating field must be absent in Direct Connection"); return null; }
                }
                else if (initiated) { // hub connector receiving from hub... destination is absent unless broadcast and originating is present (for most functions)
                    // this is a little yucky! some result messages are from the hub itself and some are from other nodes, so we'll let results be either for originating
                    if (message.originating == null && message.function != SCMessage.BVLC_RESULT) { protocolViolationLogAndSend(message, null, null, ErrorCode.HEADER_ENCODING_ERROR, "originating field must be present in initiated Hub Connection"); return null; }
                    if (message.destination != null && !message.destination.isBroadcast())        { protocolViolationLogAndSend(message, null, null, ErrorCode.HEADER_ENCODING_ERROR, "destination field must be absent in initiated Hub Connection"); return null; }
                }
                else { // hub function receiving from node... destination is present and originating is absent
                    if (message.destination == null) { protocolViolationLogAndSend(message, null, null, ErrorCode.HEADER_ENCODING_ERROR, "destination field must be present in accepted Hub Connection"); return null; }
                    if (message.originating != null) { protocolViolationLogAndSend(message, null, null, ErrorCode.HEADER_ENCODING_ERROR, "originating field must be absent in accepted Hub Connection"); return null; }
                }
                // check payload
                if (message.function == SCMessage.ADDRESS_RESOLUTION || message.function == SCMessage.ADVERTISEMENT_SOLICITATION) {
                    // This is not a standard error situation - there is no code for UNEXPECTED_PAYLOAD so we'll use INCONSISTENT_PARAMETERS
                    if (message.payload != null) { protocolViolationLogAndSend(message, message.originating, null, ErrorCode.INCONSISTENT_PARAMETERS, "payload must be absent"); return null; }
                }
                else {
                    // YY.3.1.5 Common Error Situations "If a BVLC message is received for which a payload is required, but no payload is present..."
                    // note that ADDRESS_RESOLUTION_ACK is quirky because it has a defined payload, but the payload can be zero bytes!
                    if (message.payload == null && message.function != SCMessage.ADDRESS_RESOLUTION_ACK) { protocolViolationLogAndSend(message, message.originating, null, ErrorCode.PAYLOAD_EXPECTED, "payload must be present"); return null; }
                }
                return message;

            // proprietary messages not supported (Yet)
            case SCMessage.PROPRIETARY_MESSAGE:
                // we don't support *any* proprietary functions so there is no need to look inside the payload at actual proprietary function code
                if (properties.nakUnknownProprietaryFunctions) { // YY.2.16 says we can drop or nak
                    protocolViolationLogAndSend(message, message.originating, null, ErrorCode.BVLC_PROPRIETARY_FUNCTION_UNKNOWN, "Proprietary function is unknown");
                }
                else if (properties.logUnknownProprietaryFunctions) {
                    protocolViolationLogOnly(message, "Proprietary function is unknown");
                }
                return null; // always return null - nobody wants these (in the current stack, that is)

            // everything else is really unknown and we're supposed to NAK it
            default:
                // YY.3.1.5 Common Error Situations "If a BVLC message is received that is an unknown BVLC function..."
                protocolViolationLogAndSend(message, message.originating, null, ErrorCode.BVLC_FUNCTION_UNKNOWN, "Function code is unknown");
                return null;
        }
    }


    private void protocolViolationLogOnly(SCMessage message, String errorDetails) {
        log.protocol(this, message, errorDetails);
    }
    private void protocolViolationLogOnly(SCMessage message, int errorCode, String errorDetails) {
        log.protocol(this, message, ErrorCode.toString(errorCode)+": "+errorDetails);
    }
    private void protocolViolationLogAndSend(SCMessage message, SCVMAC source, SCVMAC destination, int errorCode, String errorDetails) {
        log.protocol(this, message, ErrorCode.toString(errorCode)+": "+errorDetails);
        if (message.isUnicastRequest()) sendError(source, destination, message.function, 0, ErrorClass.COMMUNICATION, errorCode, errorDetails, message.id);
    }
    private void protocolViolationLogAndSend(SCMessage message, SCVMAC source, SCVMAC destination, int forFunction, int headerMarker, int errorClass, int errorCode, String errorDetails, int id) {
        log.protocol(this,message,errorDetails);
        if (message.isUnicastRequest()) sendError(source, destination, forFunction, headerMarker, errorClass, errorCode, errorDetails, id);
    }

    ///////////////////////////////////////////////////////////
    //////////////////// Message Senders //////////////////////
    ///////////////////////////////////////////////////////////

    // everything should be funnelled through sendMessage() to check for error injection

    public void  sendMessage(SCMessage message) {
        // every message sent from accepting peer should reset the timer since it is assumed that the initiating peer receives it and will reset its timer
        if (!initiated) keepAliveTimer.restart();
        message = SCErrorInjection.checkOutgoing(this,message); // check for message manipulation or dropping
        if (message != null) write(message.generate(),"<-- sendMessage " + message + " ["+Formatting.toHex(message.generate())+"]");
    }

    public void initiateHeartbeat() {
        expectedHeartbeatMessageID = sendHeartbeatRequest().id;
    }

    public SCMessage sendHeartbeatRequest() {
        SCMessage message = new SCMessage(null, null, SCMessage.HEARTBEAT_REQUEST);
        sendMessage(message);
        return message;
    }


    public SCMessage sendConnectRequest(SCVMAC vmac, UUID uuid, int maximumBVLCLength, int maximumNPDULength) {
        SCMessage message = new SCMessage(null, null, SCMessage.CONNECT_REQUEST, new SCPayloadConnectRequest(vmac, uuid, maximumBVLCLength, maximumNPDULength).generate());
        sendMessage(message);
        return message;
    }

    public SCMessage sendDisconnectRequest() {
        SCMessage message = new SCMessage(null, null, SCMessage.DISCONNECT_REQUEST);
        sendMessage(message);
        return message;
    }

    public SCMessage sendDisconnectAck(int id) {
        SCMessage message = new SCMessage(null, null, SCMessage.DISCONNECT_ACK, id);
        sendMessage(message);
        return message;
    }

    public SCMessage  sendHeartbeatAck(int id) {
        SCMessage message = new SCMessage(null, null, SCMessage.HEARTBEAT_ACK, id);
        sendMessage(message);
        return message;
    }

    public SCMessage  sendConnectAccept(int id) {
        SCMessage message = new SCMessage(null, null, SCMessage.CONNECT_ACCEPT, new SCPayloadConnectAccept(properties.vmac, node.getUUID(), properties.maxBVLCLengthAccepted, properties.maxNPDULengthAccepted).generate(), id);
        sendMessage(message);
        return message;
    }

    public SCMessage sendError(SCMessage message, int errorCode, String errorDetails) {
        return sendError(null, null, message.function, 0, ErrorClass.COMMUNICATION, errorCode, errorDetails, message.id);
    }

    public SCMessage sendError(SCVMAC source, SCVMAC destination, int forFunction, int headerMarker, int errorClass, int errorCode, String errorDetails, int id) {
        SCPayloadBVLCResult result = new SCPayloadBVLCResult(forFunction, headerMarker, errorClass, errorCode, errorDetails);
        SCMessage message = new SCMessage(source, destination, SCMessage.BVLC_RESULT, result.generate(),id);
        if (source != null && source.isBroadcast() || destination != null && destination.isBroadcast()) { // last minute sanity check
            log.implementation(this,"Oops: trying to send error to/from broadcast: "+message);
            return message;
        }
        write(message.generate(),"<-- Result NAK " + message + " [" + Formatting.toHex(message.generate()) + "]");
        return message;
    }

    private void write(byte[] bytes, String info) {
        if (SCErrorInjection.checkWrite(this, socket, bytes, info)) return; // give error injection a crack at it first
        if (socket != null && !socket.isClosed()) { log.info(this,info); socket.write(bytes);  }
        else log.info(this,"(SEND DROPPED) "+info);
    }

    public void dump(String prefix) {
        Shell.println(prefix+"--- Connection ---");
        Shell.println(prefix+"name:\""+name+"\" state:"+state+" timer:"+timer+" keepAlive:"+keepAliveTimer+" failure:\""+failure+"\"");
        Shell.println(prefix+"direct:"+direct+" initiated:"+initiated+" uri:\""+uri+"\"");
        Shell.println(prefix+"vmac:"+peerVMAC+" uuid:"+peerUUID+" bvlc:"+peerMaxBVLC+" npdu:"+peerMaxNPDU+"");
    }

    @Override public String toString() {
        return "SCConnection "+name;
    }

}
