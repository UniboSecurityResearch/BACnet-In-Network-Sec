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
 * An {@link SCConnectionOwner} that is used to connect between a node and a hub.  It's a state machine whose job is to
 * switch back and forth between primary and failover hubs as needed.  It has two {@link SCConnection} instances because
 * even while it is actively connected to the failover, it needs to attempt to reconnect with the primary without first
 * disconnecting the failover. Its behavior is all driven by the properties in the SCProperties instance.
 * @author drobin
 */
public class SCHubConnector implements SCConnectionOwner, EventListener {

    private static final SCLog log = new SCLog(SCHubConnector.class);

    public String       name;
    public SCNode       node;
    public Device       device;
    public SCProperties properties;
    public State        state = State.IDLE;
    public Timer        timer = new Timer();
    public SCConnection primaryConnection;
    public SCConnection failoverConnection;
    public enum State { IDLE, TRY_PRIMARY, WAIT_PRIMARY, CONNECTED_PRIMARY, TRY_FAILOVER, WAIT_FAILOVER, CONNECTED_FAILOVER, RETRY_PRIMARY, REWAIT_PRIMARY, DELAY, DELAYING }
    public URI          primaryURI;
    public URI          failoverURI;
    public int          reconnectTimeout;

    // these are used internally to turn asynchronous method calls into queued state machine events
    private static final EventType EVENT_LOCAL_START = new EventType("connector_start");
    private static final EventType EVENT_LOCAL_RESTART = new EventType("connector_restart");
    private static final EventType EVENT_LOCAL_STOP = new EventType("connector_stop");
    private static final EventType EVENT_LOCAL_STATE_CHANGE = new EventType("connector_state_change");
    private static final EventType EVENT_LOCAL_ESTABLISHED = new EventType("connector_established");
    private static final EventType EVENT_LOCAL_CLOSED = new EventType("connector_closed");

    public SCHubConnector(String name, SCProperties properties, SCNode node) {
        this.name = name;
        this.node = node;
        this.device = node.device;
        this.properties = properties;
        reconnectTimeout   = properties.minimumReconnectTime;   // initial value, increases with failure;
        primaryConnection  = new SCConnection(this,"HCP",node,properties); // not connected yet
        failoverConnection = new SCConnection(this,"HCF",node,properties); // not connected yet
    }

    public void start()  {
        log.debug(device,name,"start() called, emitting event");
        EventLoop.emit(this,this,EVENT_LOCAL_START);
    }

    public void restart()  { // for testing only - abandons existing connections!
        log.debug(device,name,"restart() called, emitting event");
        EventLoop.emit(this,this,EVENT_LOCAL_RESTART);
    }

    public void stop()  {
        log.debug(device,name,"stop() called, emitting event");
        EventLoop.emit(this,this,EVENT_LOCAL_STOP);
    }

    public void close() {  // rude device shutdown
        log.debug(device,name,"close() called, emitting event");
        primaryConnection.close();
        failoverConnection.close();
    }

    public void  setState(State state, String reason) {
        setState(state,0,reason);
    }

    public void  setState(State state, int timeout, String reason) {
        log.info(device,name,"changing state to "+state+(timeout!=0?" for "+timeout:"")+" because: "+reason);
        this.state = state;
        if (timeout != 0) timer.start(timeout); else timer.clear();
        EventLoop.emit(this,this,EVENT_LOCAL_STATE_CHANGE);
        if (state == State.IDLE) EventLoop.removeMaintenance(this);
        else                     EventLoop.addMaintenance(this);
    }

    @Override public void handleEvent(Object source, EventType eventType, Object... args) {
        // first check for local stop that trumps all other transitions
        if (eventType == EVENT_LOCAL_STOP) {
            primaryConnection.close();
            failoverConnection.close();
            setState(State.IDLE,"stop() event handled");
            return;
        }
        if (eventType == EVENT_LOCAL_RESTART) { // for testing only!
            log.warn(device,name,"Making new connections, abandoning old ones. I hope this is a test!");
            if (primaryConnection  != null) primaryConnection.owner  = null; // properly orphan them so they don't complain to use when they die
            if (failoverConnection != null) failoverConnection.owner = null;
            primaryConnection  = new SCConnection(this,"HCP",node,properties); // make new ones, the old ones are now isolated to their own fate
            failoverConnection = new SCConnection(this,"HCF",node,properties);
            setState(State.IDLE,"restart() event handled");
            eventType = EVENT_LOCAL_START; // changes event type and fall through to start from IDLE with new connectors
        }
        // then run the normal state machine
        switch (state) {
            case IDLE:
                if (eventType == EVENT_LOCAL_START) {
                    primaryURI = failoverURI = null;
                    if (properties.primaryHubURI != null && !properties.primaryHubURI.isEmpty()) {
                        try { primaryURI = new URI(properties.primaryHubURI); }
                        catch (URISyntaxException e) { log.configuration(device,name,"Bad syntax in primary hub URI: " + properties.primaryHubURI); }
                    }
                    else {
                        log.configuration(device,name,"No primary hub URI configured!" + properties.primaryHubURI);
                    }
                    if (properties.failoverHubURI != null && !properties.failoverHubURI.isEmpty()) {
                        try { failoverURI = new URI(properties.failoverHubURI); }
                        catch (URISyntaxException e) { log.configuration(device,name,"Bad URI syntax in " + properties.failoverHubURI); }
                    }
                    setState(State.TRY_PRIMARY,"start");
                    break;
                }
                break;

            case TRY_PRIMARY:
                if (primaryURI != null) {
                    log.info(device,name,"Attempting to connect to primary hub " + properties.primaryHubURI);
                    if (!primaryConnection.isClosed()) primaryConnection.close();
                    primaryConnection.initiateWebSocket(primaryURI,false); // false = not direct connect
                    setState(State.WAIT_PRIMARY,properties.connectionWaitTimeout,"initiated to primary" );
                    break;
                }
                setState(State.TRY_FAILOVER,"No primary hub configured ");
                break;

            case WAIT_PRIMARY:
                if (timer.expired()) {
                    log.warn(device,name,"Timeout - Failed to connect to primary hub " + properties.primaryHubURI);
                    primaryConnection.close();
                    setState(State.TRY_FAILOVER,"Timeout - Failed to connect to primary hub");
                    break;
                }
                if (primaryConnection.isConnected()) {
                    reconnectTimeout = properties.minimumReconnectTime; // reset increasing delays
                    log.info(device,name,"Connected to primary hub");
                    setState(State.CONNECTED_PRIMARY,"connected");
                    node.datalink.device.networkLayer.sendIAmRouterToNetworkForDirectlyConnectedNetworks(node.datalink);
                    node.datalink.device.networkLayer.sendNetworkNumberIs(node.datalink);
                    break;
                }
                break;

            case CONNECTED_PRIMARY:
                if (primaryConnection.isClosed()) {
                    log.warn(device,name,"Lost connection to primary hub " + properties.primaryHubURI + ". Retrying.");
                    setState(State.TRY_PRIMARY,"Lost connection"); // go *back* to try primary, don't try failover just because primary died
                    break;
                }
                break;

            case TRY_FAILOVER:
                if (failoverURI != null) {
                    log.info(device,name,"Attempting to connect to failover hub " + properties.failoverHubURI);
                    if (!failoverConnection.isClosed()) failoverConnection.close();
                    failoverConnection.initiateWebSocket(failoverURI,false); // false = not direct connect
                    setState(State.WAIT_FAILOVER, properties.connectionWaitTimeout, "initiated to failover" );
                    break;
                }
                setState(State.DELAY,"No failover hub configured ");
                break;

            case WAIT_FAILOVER:
                if (timer.expired()) {
                    log.warn(device,name,"Timeout - Failed to connect to failover hub " + properties.failoverHubURI);
                    failoverConnection.close();
                    setState(State.DELAY, "Failed to connect to failover");
                    break;
                }
                if (failoverConnection.isConnected()) {
                    reconnectTimeout = properties.minimumReconnectTime; // reset the delay for retrying primary
                    log.info(device,name,"Connected to failover hub");
                    setState(State.CONNECTED_FAILOVER, reconnectTimeout, "connected");
                    break;
                }
                break;

            case CONNECTED_FAILOVER:
                if (failoverConnection.isClosed()) {
                    log.info(device,name,"Lost connection to failover hub " + properties.failoverHubURI);
                    setState(State.TRY_PRIMARY, "lost connection"); // in this case, we don't retry failover because we always prefer primary unless it doesn't connect
                    break;
                }
                if (timer.expired()) { // periodically re-try PRIMARY with increasing timeouts
                    setState(State.RETRY_PRIMARY,"retry primary");
                    break;
                }
                break;

            case RETRY_PRIMARY:
                // this is a substate of CONNECTED_FAILOVER, so also do the same things as CONNECTED_FAILOVER
                if (failoverConnection.isClosed()) {
                    log.info(device,name,"Lost connection to failover hub " + properties.failoverHubURI + ". TRY_PRIMARY");
                    setState(State.TRY_PRIMARY, "failover lost connection");
                    break;
                }
                if (primaryURI != null) {
                    if (!primaryConnection.isClosed()) primaryConnection.close();
                    primaryConnection.initiateWebSocket(primaryURI,false); // false = not direct connect
                    setState(State.REWAIT_PRIMARY,properties.connectionWaitTimeout,"primary retry initiated");
                    break;
                }
                setState(State.CONNECTED_FAILOVER,properties.maximumReconnectTime,"no primary configured, so no retry");
                break;

            case REWAIT_PRIMARY:
                // this is a substate of CONNECTED_FAILOVER, so also do the same things as CONNECTED_FAILOVER
                if (failoverConnection.isClosed()) {
                    log.info(device,name,"Lost connection to failover hub " + properties.failoverHubURI + ". TRY_PRIMARY");
                    setState(State.TRY_PRIMARY, "failover lost connection");
                    break;
                }
                if (timer.expired()) {
                    log.info(device,name,"Timeout - failed to re-connect to primary hub " + properties.primaryHubURI);
                    primaryConnection.close();
                    reconnectTimeout *= 2;
                    if (reconnectTimeout > properties.maximumReconnectTime) reconnectTimeout = properties.maximumReconnectTime;
                    setState(State.CONNECTED_FAILOVER,reconnectTimeout,"primary retry failed");
                    break;
                }
                if (primaryConnection.isConnected()) {
                    reconnectTimeout = properties.minimumReconnectTime; // reset increasing delays
                    log.info(device,name,"Re-connected to primary hub, disconnecting from failover");
                    failoverConnection.disconnect();
                    setState(State.CONNECTED_PRIMARY,"connected");
                    break;
                }
                break;

            case DELAY:
                reconnectTimeout *= 2;
                if (reconnectTimeout > properties.maximumReconnectTime) reconnectTimeout = properties.maximumReconnectTime;
                setState(State.DELAYING,reconnectTimeout,"delay before retry of primary");
                break;

            case DELAYING:
                if (!primaryConnection.isClosed())  primaryConnection.close();
                if (!failoverConnection.isClosed()) failoverConnection.close();
                if (timer.expired()) {
                    log.info(device,name,"Delay complete, back to TRY_PRIMARY");
                    setState(State.TRY_PRIMARY,"reconnect delay complete");
                }
                break;
        }
    }

    public void sendMessage(SCMessage message) {
        if      (state == State.CONNECTED_PRIMARY)  primaryConnection.sendMessage(message);
        else if (state == State.CONNECTED_FAILOVER) failoverConnection.sendMessage(message);
        // else drop, sorry!
    }

    public int getStateAsInt() {
        return  state == State.CONNECTED_PRIMARY?
                SCPayloadAdvertisement.CONN_STAT_PRIMARY :
                state == State.CONNECTED_FAILOVER? SCPayloadAdvertisement.CONN_STAT_FAILOVER :
                        SCPayloadAdvertisement.CONN_STAT_NONE;
    }

    //////////////// Owner Interface ////////////////////////

    @Override public void incoming(SCConnection connection, SCMessage message) {
        log.info(device,name,"--> Hub Connector to Node "+message);
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
            if (connection == this.primaryConnection || connection == this.failoverConnection) EventLoop.emit(this,this,EVENT_LOCAL_ESTABLISHED);
            else log.implementation(device,name,"connectionEstablished() from ZOMBIE connection");
    }

    @Override public void connectionClosed(SCConnection connection) {
        log.debug(device,name,"connectionClosed() called, emitting event");
        if (connection == this.primaryConnection || connection == this.failoverConnection) EventLoop.emit(this,this,EVENT_LOCAL_CLOSED);
        else log.implementation(device,name,"connectionClosed() from ZOMBIE connection");
    }

//////////////////////////////////////////////////////////////

    public SCConnection getActiveConnection() { // for manual commands (only?)
        switch (state) {
            case CONNECTED_PRIMARY:  return primaryConnection;
            case CONNECTED_FAILOVER: return failoverConnection;
            default: return null;
        }
    }

    public void dump(String prefix) {
        Shell.println(prefix+"--- Hub Connector ---");
        Shell.println(prefix+"name:\""+name+"\" state:"+state+" timer:"+timer+" reconnect:"+reconnectTimeout);
        primaryConnection.dump(prefix+"   ");
        failoverConnection.dump(prefix+"   ");
    }

    public String toString() {
        return "SCHubConnector "+name;
    }


}
