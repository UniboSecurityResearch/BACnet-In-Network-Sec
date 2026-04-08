// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.bacnetsc;

import dev.bscs.bacnet.bacnetip.IPDatalink;
import dev.bscs.bacnet.stack.Device;
import dev.bscs.common.Timer;
import dev.bscs.events.EventListener;
import dev.bscs.events.EventLoop;
import dev.bscs.events.EventType;
import dev.bscs.websocket.BSWebSocket;
import dev.bscs.websocket.BSWebSocketServer;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * The base class for both {@link SCHubFunction} and {@link SCNodeSwitch} that listens for new WebSocket connections and
 * creates new {@link SCConnection} instance for eacho of them.  The connections will then do their own thing attempting
 * to do the Connect-Request/Connect-Accept dance while this server is just acting as their {@link SCConnectionOwner} to
 * provide the "connection context" for queries about existing VMACs and UUIDs.  Other than managing the creation and
 * culling of incoming connections, this base class does not know what to *do* with incoming messages destined for higher
 * layers, so the subclasses have to override the {@link #incoming} method of this "owner" interface for all the
 * connections.
 * @author drobin
 */
public abstract class SCServer implements SCConnectionOwner, EventListener {

    private static final SCLog   log = new SCLog(SCServer.class);

    public  String             name;
    public  int                number;
    public  Device             device;
    public  String             initials;  // "HF", "NS", etc. used to make connection names
    public  SCProperties       properties;
    public  SCNode             node;
    public  BSWebSocketServer  server;
    public  String             failure="";
    public  List<SCConnection> connections = new ArrayList<>();
    public  String             bindURI;
    public  String             subprotocol; //  "dc.bsc.bacnet.org" or  "hub.bsc.bacnet.org"
    public  boolean            direct;
    public  enum               State { IDLE, START, STARTING, STARTED, FAILURE_DELAY }
    public  State              state = State.IDLE;
    public  Timer              timer = new Timer();

    // these are used internally to turn asynchronous method calls into queued state machine events
    private static final EventType EVENT_LOCAL_START        = new EventType("server_start");
    private static final EventType EVENT_LOCAL_STOP         = new EventType("server_stop");
    private static final EventType EVENT_LOCAL_STATE_CHANGE = new EventType("server_state_change");

    private static int nextNumber = 1;  // mostly just to give them ordinals for selecting with manual commands

    public SCServer(String name, Device device, String initials, SCProperties properties, SCNode node, String bindURI, String subprotocol, boolean direct) {
        this.name       = name;
        this.device     = device;
        this.initials   = initials;
        this.node       = node;
        this.properties = properties;
        this.bindURI    = bindURI;
        this.subprotocol= subprotocol;
        this.direct     = direct;
        this.number     = nextNumber++;
    }

    public boolean isStarted() {
        return state == State.STARTED;
    }

    @Override public void handleEvent(Object source, EventType eventType, Object... args) {

        if (eventType == EVENT_LOCAL_STOP) {
            for (SCConnection connection : new HashSet<>(connections)) connection.close();  // iterate over copy to avoid concurrent mod by connectionStateChanged callback
            connections.clear();  // should be emptied by connectionStateChanged callback, but make sure anyway
            if (server != null) server.stop();
            setState(State.IDLE,"stop() event handled");
        }
        else switch (state) {

            case IDLE:
                if (eventType == EVENT_LOCAL_START) setState(State.START, "start() event handled");
                break;

            case START:
                server = BSWebSocketServer.newInstance("WS-" + name, this);
                try {
                    URI uri = new URI(bindURI);
                    if (!uri.getPath().isEmpty()) { log.configuration("SCServer does not support a path component in bind URI"); break; }
                    if (uri.getScheme().equalsIgnoreCase("wss")) server.setSSLOptions(node.getTLSManager().getSSLContext(), !properties.noValidation , new String[]{properties.tlsVersion}, null);
                    server.start(IPDatalink.getSocketAddress(uri.getHost(),uri.getPort()), subprotocol);
                    setState(State.STARTING, properties.serverBindTimeout, "start() event handled");
                } catch (Exception e) {
                    String error = "Can't construct bind address from " + bindURI +": " + e;
                    log.configuration(error);
                    setState(State.FAILURE_DELAY, properties.serverBindTimeout, "bad bind address");
                }
                break;

            case STARTING:
                if (eventType == BSWebSocketServer.EVENT_SERVER_STARTED || server.isStarted()) {
                    log.info(name," started, listening on "+bindURI);
                    failure = "";
                    setState(State.STARTED,"isBound() == true");
                }
                else if (timer.expired()) {
                    failure = name+" NOT STARTED. Could not bind to "+bindURI;
                    log.error(name+" "+failure);
                    setState(State.FAILURE_DELAY, properties.serverBindTimeout,"could not bind");
                }
                break;

            case STARTED:
                if (eventType == BSWebSocketServer.EVENT_SERVER_SOCKET_ERROR && args[0] == null) { // null means error without socket, so server error
                    log.error("SCServer encountered error: ", ((Throwable) args[1]).getLocalizedMessage());
                }
                else if (server.isStopped()) {
                    failure = "Server stopped unexpectedly";
                    setState(State.FAILURE_DELAY, properties.serverBindTimeout,"Server stopped unexpectedly");
                }
                else if (eventType == BSWebSocketServer.EVENT_SERVER_SOCKET_OPEN) {
                    BSWebSocket socket = (BSWebSocket)args[0];
                    SCConnection connection = new SCConnection(this, initials, node, properties);
                    socket.setAttachment(connection);
                    connection.acceptWebSocket(socket, direct);
                    connections.add(connection);
                }
                else if (eventType == BSWebSocketServer.EVENT_SERVER_SOCKET_MESSAGE) {
                    BSWebSocket socket = (BSWebSocket)args[0];
                    SCConnection connection = ((SCConnection)socket.getAttachment());
                    if (connection != null) connection.handleEvent(socket, BSWebSocket.EVENT_SOCKET_MESSAGE,args[1]);
                }
                else if (eventType == BSWebSocketServer.EVENT_SERVER_SOCKET_ERROR) {
                    BSWebSocket socket = (BSWebSocket)args[0];
                    SCConnection connection = (SCConnection)socket.getAttachment();
                    if (connection != null) connection.handleEvent(socket, BSWebSocket.EVENT_SOCKET_ERROR,args[1]);
                }
                else if (eventType == BSWebSocketServer.EVENT_SERVER_SOCKET_CLOSE) {
                    BSWebSocket socket = (BSWebSocket)args[0];
                    SCConnection connection = (SCConnection)socket.getAttachment();
                    if (connection != null) connection.handleEvent(socket, BSWebSocket.EVENT_SOCKET_CLOSE);
                }
                else if (eventType == EventLoop.EVENT_MAINTENANCE) {
                    // run clean up on dead connections
                    connections.removeIf(SCConnection::isClosed);
                }
                break;

            case FAILURE_DELAY:
                if (timer.expired()) {
                    setState(State.START, "server failure delay complete, retrying bind");
                }
                break;
        }
    }

    public void  setState(State state, String reason) {
        setState(state,0,reason);
    }

    public void  setState(State state, int timeout, String reason) {
        log.info(name+" changing state to "+state+(timeout!=0?" for "+timeout:"")+" because: "+reason);
        this.state = state;
        if (timeout != 0) timer.start(timeout); else timer.clear();
        EventLoop.emit(this,this,EVENT_LOCAL_STATE_CHANGE);
        if (state == State.IDLE) EventLoop.removeMaintenance(this);
        else                     EventLoop.addMaintenance(this);
    }

    public void start()  {
        log.debug(name+" start() called, emitting event");
        EventLoop.emit(this,this,EVENT_LOCAL_START);
    }

    public void stop()  {
        log.debug(name+" stop() called, emitting event");
        EventLoop.emit(this,this,EVENT_LOCAL_STOP);
    }

    public void close() {  // rude device shutdown
        log.info("close() called, directly stopping");
        if (server != null) server.stop();
    }

    ////////////// SCConnection Owner interface //////////////

    // incoming() remains abstract - it's the main difference between SCNodeSwitch and SCHubFunction
    // public void incoming(SCConnection connection, SCMessage message)

    @Override public SCConnection findConnectionFor(UUID uuid) {
        for (SCConnection connection : connections) if (connection.peerUUID.equals(uuid) && connection.isConnected()) return connection;
        return null;
    }

    @Override public SCConnection findConnectionFor(SCVMAC vmac) {
        for (SCConnection connection : connections) if (connection.peerVMAC!=null && connection.peerVMAC.equals(vmac) && connection.isConnected()) return connection;
        return null;
    }

    // There doesn't seem to be a reason for the server to care about this. All we care about at this level is incoming()
    // If the connection does or does not get fully established is none of our concern. If it goes idle it will get cleaned up
    @Override public void connectionEstablished(SCConnection connection) { }

    // There doesn't seem to be a reason for the server to care about this here. Closed connections will get cleaned up at maintenance time
    @Override public void connectionClosed(SCConnection connection) { }
}
