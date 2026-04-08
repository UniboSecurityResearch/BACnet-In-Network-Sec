// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.bacnetsc;

import dev.bscs.bacnet.stack.Device;
import dev.bscs.common.Shell;
import dev.bscs.common.Timer;
import dev.bscs.events.EventListener;
import dev.bscs.events.EventLoop;
import dev.bscs.events.EventType;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.UUID;

/**
 * An {@link SCConnectionOwner} that is used to connect between node switches.  It's a state machine whose job is to
 * resolve the direct connection addresses and try them, in order, to establish a Direct Connection from one
 * {@link SCNodeSwitch} switch to another.  An SCNodeSwitch will maintain a pool of these and will make new ones as
 * needed by the application.  These can be reused for different destinations because the {@link #connect} method takes
 * the destination VMAC as an argument, and after a {@link #disconnect}, a SCDirectConnector instance can be used to
 * connect to a different VMAC.
 * @author drobin
 */
public class SCDirectConnector implements SCConnectionOwner, EventListener {

    public static final SCLog log = new SCLog(SCDirectConnector.class);

    public String       name;
    public SCNode       node;
    public Device       device;
    public SCProperties properties;
    public State        state = State.IDLE;
    public SCConnection connection;
    public SCVMAC       peerVMAC;
    public enum State   { IDLE, TRY_RESOLUTION, WAIT_RESOLUTION, WAIT_REFRESH, TRY_CONNECTION, WAIT_CONNECTION, TRY_NEXT, CONNECTED, DELAYING, RETRY_DELAY }
    public String[]     urls;
    public int          urlIndex;
    public boolean      disconnect;
    public Timer        timer = new Timer();

    // these are used internally to turn asynchronous method calls into queued state machine events
    private static final EventType EVENT_LOCAL_CONNECT      = new EventType("connector_connect");
    private static final EventType EVENT_LOCAL_DISCONNECT   = new EventType("connector_disconnect");
    private static final EventType EVENT_LOCAL_CLOSE        = new EventType("connector_close");
    private static final EventType EVENT_LOCAL_STATE_CHANGE = new EventType("connector_state_change");
    private static final EventType EVENT_LOCAL_ESTABLISHED  = new EventType("connector_established");
    private static final EventType EVENT_LOCAL_CLOSED       = new EventType("connector_closed");

    public SCDirectConnector(String name, SCProperties properties, SCNode node) {
        this.name = name;
        this.node = node;
        this.properties = properties;
        this.connection = new SCConnection(this,"DC",node,properties);
        this.device = node.device;
    }

    public void connect(SCVMAC peerVMAC) {
        log.debug(device,name,"connect("+peerVMAC+") called, emitting event");
        this.peerVMAC = peerVMAC;
        this.urls = null;
        EventLoop.emit(this,this, EVENT_LOCAL_CONNECT);
    }
    public void connect(SCVMAC peerVMAC, String[] urls) {
        log.debug(device,name,"connect("+peerVMAC+",urls) called, emitting event");
        this.peerVMAC = peerVMAC;
        this.urls = urls;
        this.urlIndex = 0;
        EventLoop.emit(this,this, EVENT_LOCAL_CONNECT);
    }

    public void close()  {
        log.debug(device,name,"close() called, emitting event");
        EventLoop.emit(this,this, EVENT_LOCAL_CLOSE);
    }

    public void disconnect()  {
        log.debug(device,name,"disconnect() called, emitting event");
        EventLoop.emit(this,this,EVENT_LOCAL_DISCONNECT);
    }

    public boolean isConnected() { return state == State.CONNECTED; }

    public boolean isIdle()      { return state == State.IDLE; }

    private void  setState(State state, String reason) {
        setState(state,0,reason);
    }

    private void  setState(State state, int timeout, String reason) {
        log.info(device,name,"changing state to "+state+(timeout!=0?" for "+timeout:"")+" because: "+reason);
        this.state = state;
        if (timeout != 0) timer.start(timeout); else timer.clear();
        EventLoop.emit(this,this,EVENT_LOCAL_STATE_CHANGE);
        if (state == State.IDLE) EventLoop.removeMaintenance(this);
        else                     EventLoop.addMaintenance(this);
    }

    @Override public void handleEvent(Object source, EventType eventType, Object... args) {

        if (eventType == EVENT_LOCAL_CLOSE) {
            connection.close();
            setState(State.IDLE,"stop() event handled");
        }
        else if (eventType == EVENT_LOCAL_DISCONNECT) {
            connection.disconnect();
            setState(State.IDLE,"disconnect() event handled");
        }
        else switch (state) {

                case IDLE:
                    if (eventType == EVENT_LOCAL_CONNECT) {
                        if (urls != null) setState(State.TRY_CONNECTION,"start() event handled with provided urls");
                        else              setState(State.TRY_RESOLUTION,"start() event handled");
                    }
                    break;

                case TRY_RESOLUTION:
                    SCAddressResolution resolution = node.getAddressResolution(peerVMAC);
                    if (resolution != null) {
                        if (resolution.freshness.expired()) {
                            // if stale, send a new request and wait for refresh
                            node.sendAddressResolution(peerVMAC);
                            setState(State.WAIT_REFRESH, properties.addressResolutionTimeout, "sent resolution *refresh* request");
                        }
                    }
                    else node.sendAddressResolution(peerVMAC);
                    setState(State.WAIT_RESOLUTION,properties.addressResolutionTimeout,"sent resolution request");
                    break;

                case WAIT_REFRESH:
                    if (timer.expired()) {  // if we didn't get a refresh, we'll just continue with the old stale resolution
                        setState(State.WAIT_RESOLUTION,properties.addressResolutionTimeout,"gave up waiting for resolution refresh");
                        break;
                    }
                    resolution = node.getAddressResolution(peerVMAC);
                    if (resolution != null && !resolution.freshness.expired()) { // if it became fresh we'll use it
                        setState(State.WAIT_RESOLUTION,properties.addressResolutionTimeout,"resoluton was made fresh");
                        break;
                    }
                    break;

                case WAIT_RESOLUTION:
                    if (timer.expired()) {
                        setState(State.DELAYING, properties.addressResolutionDelay, "gave up waiting for resolution");
                        break;
                    }
                    resolution = node.getAddressResolution(peerVMAC);
                    if (resolution != null) {
                        urls = resolution.urls;
                        if (urls.length > 0) {
                            urlIndex = 0;
                            setState(State.TRY_CONNECTION,"resolved; starting with index 0");
                            break;
                        }
                        else {
                            log.info(device,name,"No DC URLs received in resolution ACK for "+peerVMAC);
                            setState(State.DELAYING,properties.addressResolutionDelay,"No URLs received");
                            break;
                        }
                    }
                    break;

                case TRY_CONNECTION:
                    try {
                        if (urls == null || urls.length == 0) setState(State.TRY_RESOLUTION,"What happened to my URL list?");
                        if (urlIndex >= urls.length) urlIndex = 0; // sanity check
                        URI uri = new URI(urls[urlIndex]);
                        log.info(device,name,"Attempting DC to "+peerVMAC+" at '"+urls[urlIndex]+"'");
                        connection.initiateWebSocket(uri,true); // true == direct connect
                        setState(State.WAIT_CONNECTION,properties.connectionWaitTimeout,"initiated");
                    }
                    catch (URISyntaxException e) {
                        log.error(device,name,"Skipping  DC to "+peerVMAC+" at MALFORMED URL '"+urls[urlIndex]+"'");
                        setState(State.TRY_NEXT,"malformed url");
                    }
                    break;

                case WAIT_CONNECTION:
                    if (timer.expired()) {
                        setState(State.TRY_NEXT,"timeout on index "+urlIndex);
                        break;
                    }
                    if (connection.isConnected()) {
                        log.info(device,name,"Connected DC to "+peerVMAC+" at '"+urls[urlIndex]+"'");
                        disconnect = false;
                        setState(State.CONNECTED,"connected");
                        break;
                    }
                    break;

                case TRY_NEXT:
                    urlIndex++;
                    if (urlIndex >= urls.length) setState(State.DELAYING,properties.addressResolutionDelay,"delaying because ran out of urls to try");
                    else                         setState(State.TRY_CONNECTION,"trying index "+urlIndex);
                    break;

                case CONNECTED:
                    if (disconnect) {
                        log.info(device,name,"Disconnecting DC to "+peerVMAC+" at '"+urls[urlIndex]+"'");
                        close();
                        break;
                    }
                    if (!connection.isConnected()) {
                        log.info(device,name,"DC connection got closed to "+peerVMAC+" at '"+urls[urlIndex]+"'");
                        setState(State.DELAYING,properties.minimumReconnectTime,"DC connection got dropped");
                    }
                    break;

                case DELAYING:
                    if (timer.expired()) {
                        if (urls != null && urls.length > 0) { urlIndex = 0; setState(State.TRY_CONNECTION,"Delay complete, back to TRY_CONNECTION"); }
                        else setState(State.TRY_RESOLUTION,"Delay complete, back to TRY_RESOLUTION");
                        break;
                    }
                    break;
            }
    }

    public void sendMessage(SCMessage message) {
        if (connection != null) connection.sendMessage(message);
    }

    //////////// Owner interface ///////////////

    @Override public void incoming(SCConnection connection, SCMessage message) {
        log.info(device,name,"--> Direct Connector to Node Switch "+message);
        message.originating = connection.peerVMAC;
        node.incoming(connection,message);
    }

    @Override public SCConnection findConnectionFor(UUID uuid) {
        // this is an initiator-only so this question has no meaning
        return null;
    }
    @Override public SCConnection findConnectionFor(SCVMAC vmac) {
        // this is an initiator-only so this question has no meaning
        return null;
    }

    @Override public void connectionEstablished(SCConnection connection) {
        log.debug(device,name,"connectionEstablished() called, emitting event");
        if (this.connection == connection) EventLoop.emit(this,this,EVENT_LOCAL_ESTABLISHED);
        else log.implementation(device,name,"connectionEstablished() from ZOMBIE connection");
    }

    @Override public void connectionClosed(SCConnection connection) {
        log.debug(device,name,"connectionClosed() called, emitting event");
        if (this.connection == connection) EventLoop.emit(this,this,EVENT_LOCAL_CLOSED);
        else log.implementation(device,name,"connectionCLOSED() from ZOMBIE connection");
    }

    //////////////////////////////////////////////////////////////

    public void dump(String prefix) {
        Shell.println(prefix+"--- Direct Connector ---");
        Shell.println(prefix+"name:\""+name+"\" state:"+state+" timer:"+timer+"");
        Shell.println(prefix+"peer:"+peerVMAC+" index:"+urlIndex+" urls:["+String.join(" ",urls)+"]");
        connection.dump(prefix+"   ");
    }

    public String toString() {
        return "SCDirectConnector "+name;
    }

}
