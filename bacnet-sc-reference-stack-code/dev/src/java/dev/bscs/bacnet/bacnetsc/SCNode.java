// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.bacnetsc;

import dev.bscs.bacnet.stack.Datalink;
import dev.bscs.bacnet.stack.Device;
import dev.bscs.bacnet.stack.constants.ErrorClass;
import dev.bscs.bacnet.stack.constants.ErrorCode;
import dev.bscs.common.Shell;
import dev.bscs.common.Timer;
import dev.bscs.events.EventListener;
import dev.bscs.events.EventLoop;
import dev.bscs.events.EventType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * SCNode is where all the pieces of an SC datalink come together.  This holds and manages the required hub connector,
 * the optional hub function and optional node switch. In addition, it manages the TLS context used by all of those.
 * It holds a link to a {@link SCDatalink} which is really just a wrapper to provide the {@link Datalink} interface to
 * the network layer since all the real work of an SC datalink is done here.
 * This state machine is responsible for starting up the node by starting the optional hub function and node switch servers,
 * then starting the hub connector.  It can also shut everything down and restart in the case that its VMAC needs to change.
 * Link other state machines, even though all the interactions with the node are in the same thread, to
 * keep the state machine clean and all in one place, calls like start(), stop(), restartWithNewVMAC(), actually fire events
 * themselves that the state machine processes from its one entry point.  This keeps "side effects" out of the state machine
 * and all transitions that can be seen in one place.
 * @author drobin
 */
public class SCNode implements EventListener {

    private static SCLog log = new SCLog(SCNode.class);

    public  String          name;
    public  int             number;
    public  Device          device;
    public  SCProperties    properties;
    public  SCNodeSwitch    nodeSwitch;
    public  SCHubConnector  hubConnector;
    public  SCHubFunction   hubFunction;
    public  SCDatalink      datalink;
    public  SCTLSManager    tlsManager;
    public  Timer           timer = new Timer();
    public  State           state = State.IDLE;
    public  enum            State { IDLE,  STARTING, HC_START_DELAY, STARTED, FAILURE_DELAY }
    public  List<SCAddressResolution> addressResolutions = new ArrayList<>();
    public  Tap             tap;
    public static class Tap { // used by manual commands to "tap into" responses
        public void addressResolutionAck(SCConnection connection, SCMessage message, SCPayloadAddressResolutionAck ack){}
        public void bvlcResult(SCConnection connection, SCMessage message, SCPayloadBVLCResult result) {}
        public void advertisement(SCConnection connection, SCMessage message, SCPayloadAdvertisement advertisement) {}
    }

    // these are used internally to turn method calls into queued state machine events
    private static final EventType EVENT_LOCAL_START        = new EventType("node_start");
    private static final EventType EVENT_LOCAL_STOP         = new EventType("node_stop");
    private static final EventType EVENT_LOCAL_NEW_MAC      = new EventType("node_new_mac");
    private static final EventType EVENT_LOCAL_STATE_CHANGE = new EventType("node_state_change");

    private static int nextNumber = 1;

    public SCNode(String name, SCProperties properties, SCDatalink datalink) {
        this.name         = name;
        this.properties   = properties;
        this.datalink     = datalink;
        this.device       = datalink.device;
        this.tlsManager   = new SCTLSManager(properties); // can be replaced later, but this makes sure one is always avaliable for getTLSManager()
        this.number       = nextNumber++;
        this.hubConnector = new SCHubConnector(name + "-HC", properties, this);
    }

    public void                      setTap(Tap tap) { this.tap = tap;}
    public void                      clearTap()      { this.tap = null;}
    public UUID                      getUUID()       { return datalink.getNetworkLayer().device.deviceObject.properties.uuid; }
    public SCAddressResolution       getAddressResolution(SCVMAC vmac) { for (SCAddressResolution r : addressResolutions) { if (r.vmac.equals(vmac)) return r; } return null; }
    public List<SCAddressResolution> getAddressResolutions() { return addressResolutions; }

    @Override public void handleEvent(Object source, EventType eventType, Object... args) {
        // first deal with rude interruptions to the state machine that are not based on current state
        if (eventType == EVENT_LOCAL_STOP) {
            hubConnector.stop();
            if (hubFunction != null) hubFunction.stop();
            if (nodeSwitch != null)  nodeSwitch.stop();
            setState(State.IDLE, "stop() event handled");
            return;
        }
        // now run the normal state machine
        switch (state) {

            case IDLE:
                if (eventType == EVENT_LOCAL_START) {
                    log.info(device,name,String.format("%s Node starting with vmac %s and uuid %s and isHub %b", name, properties.vmac.toString(),
                            datalink.getNetworkLayer().device.deviceObject.properties.uuid.toString(),
                            properties.hubFunctionEnable));
                    tlsManager = new SCTLSManager(properties);  // sets tlsErrorXxxx, if you care to look
                    if (properties.tlsError) log.configuration(device,name,String.format("FATAL TLS configuration error: class %d code %d reason %s", properties.tlsErrorClass, properties.tlsErrorCode, properties.tlsErrorReason));
                    // TODO what to do if there is a TLS error? how to continue? how to report?

                    // we *might* be a hub
                    if (properties.hubFunctionEnable) {
                        hubFunction = new SCHubFunction(name + "-HF", device, properties, this); // won't activate if properties sc.isHub is false
                        hubFunction.start();
                    }
                    else hubFunction = null;

                    // we *might* have a node switch
                    if (properties.directConnectEnable) {
                        nodeSwitch = new SCNodeSwitch(name + "-NS", device, properties, this);
                        nodeSwitch.start();
                    }
                    else nodeSwitch = null;
                    // good luck...
                    setState(State.STARTING, properties.serverStartupTimeout, "start() event handled");
                }
                break;

            case STARTING:
                // TODO rework this so that even if the servers don't start, the hub connector will still try
                if (timer.expired()) {
                    // well, this is not going well... the servers did not start
                    // this happens a lot in debugging because crashes leave ports open, but at normal runtime? hopefully not.
                    stop();
                    setState(State.FAILURE_DELAY, properties.startupFailureRetryDelay, "timeout trying to start/bind");
                }
                else if ((nodeSwitch == null || nodeSwitch.isStarted()) && (hubFunction == null || hubFunction.isStarted())) {
                    // OK, the servers are started, now, do we have a node? we'd better unless we're running a debug mode
                    if (properties.nodeEnable) {
                        // after the hub function and node switch "say" they are ready, there is actually a non-zero time
                        // that is needed before attempting to connect to them, so the internal node needs to be delayed
                        setState(State.HC_START_DELAY, properties.serverStartupDelay, "delaying internal hub connector");
                    }
                    else {
                        log.warn(device,name,"WARNING!! Starting without an active node!");
                        setState(State.STARTED,"no node!");
                    }
                }
                break;

            case HC_START_DELAY:
                    if (timer.expired()) {
                        hubConnector.start();
                        setState(State.STARTED,"Startup node delay complete");
                    }
                    break;

            case STARTED:
                if (eventType == EVENT_LOCAL_NEW_MAC) {
                    SCVMAC vmac = SCVMAC.makeRandom();
                    log.info(device,name,"RESTARTING: Setting new vmac to " + vmac);
                    properties.vmac = vmac;
                    stop();  // send event to ourselves to stop te state machine back to idle
                    start(); // send event to ourselves to kick us out of idle
                }
                break;

            case FAILURE_DELAY:
                if (timer.expired()) {
                    log.info(device,name,"Restarting after failure delay");
                    start();
                }
                break;
        }
    }

    public boolean start() {
        log.debug(device,name,"start() called, emitting event");
        EventLoop.emit(this,this,EVENT_LOCAL_START);
        return true;  // true here doesn't mean everything is great, just nothing immediately fatal detected
    }

    public void stop()  {
        log.debug(device,name,"stop() called, emitting event");
        EventLoop.emit(this,this,EVENT_LOCAL_STOP);
    }

    public void close() {  // rude device shutdown
        log.debug(device,name,"close() called, emitting event");
        hubConnector.close();
        if (hubFunction != null) hubFunction.close();
        if (nodeSwitch != null)  nodeSwitch.close();
    }

    public void restartWithNewVMAC() {
        log.debug(device,name,"restartWithNewVMAC() called, emitting event");
        EventLoop.emit(this,this,EVENT_LOCAL_NEW_MAC);
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

    public void  sendMessage(SCMessage message) {
        if (nodeSwitch != null) nodeSwitch.sendMessage(message);
        else hubConnector.sendMessage(message);
    }

    public void  sendAdvertisementSolicitation(SCVMAC destination) {
        SCMessage message = new SCMessage(null, destination, SCMessage.ADVERTISEMENT_SOLICITATION);
        sendMessage(message);
    }


    public void  sendAdvertisement(SCVMAC destination, int connectionStatus, boolean acceptConnections, int maximumBVLCLength, int maximumNPDULength) {
        SCMessage message = new SCMessage(null, destination, SCMessage.ADVERTISEMENT, new SCPayloadAdvertisement(connectionStatus, acceptConnections, maximumBVLCLength, maximumNPDULength).generate());
        sendMessage(message);
    }

    public int   sendAddressResolution(SCVMAC destination) {
        SCMessage message = new SCMessage(null, destination, SCMessage.ADDRESS_RESOLUTION);
        sendMessage(message);
        return message.id;
    }

    public void  sendAddressResolutionAck(SCVMAC destination, String[] urls, int id) {
        SCMessage message = new SCMessage(null, destination, SCMessage.ADDRESS_RESOLUTION_ACK, new SCPayloadAddressResolutionAck(urls).generate(),id);
        sendMessage(message);
    }

    public void sendErrorResponse(SCMessage message, int headerMarker, int errorClass, int errorCode, String errorDetails) {
        sendError(message.destination, message.originating, message.function, headerMarker, errorClass, errorCode, errorDetails, message.id);
    }
    public void sendErrorResponse(SCMessage message, int errorClass, int errorCode, String errorDetails) {
        sendError(message.destination, message.originating, message.function, 0, errorClass, errorCode, errorDetails, message.id);
    }
    public void   sendError(SCVMAC source, SCVMAC destination, int forFunction, int headerMarker, int errorClass, int errorCode, String errorDetails, int id) {
        SCPayloadBVLCResult result = new SCPayloadBVLCResult(forFunction, headerMarker, errorClass, errorCode, errorDetails);
        sendMessage(new SCMessage(source, destination, SCMessage.BVLC_RESULT, result.generate(),id));
    }

    public void incoming(SCConnection connection, SCMessage message) {

        log.info(device,name,"-->incoming() from " + connection.name + " " + message);

        // This is the only place that destination options are checked because YY.3.1.4 says
        // that the "destination BACnet/SC node shall process header options".
        // Note that this only applies to "destination options" - the "data options" are NOT processed by the node/datalink
        if (message.destOptions != null) for (SCOption option : message.destOptions) {
            // we don't understand *any* must-understands for now, so everything is an error
            if (option.mustUnderstand) {
                // if this was unicast, then send a NAK, otherwise, just return
                log.error("Rejecting must-understand destination option "+option);
                if (message.isUnicastRequest()) sendErrorResponse(message, option.marker, ErrorClass.COMMUNICATION, ErrorCode.HEADER_NOT_UNDERSTOOD, " 'Must Understand' option not understood");
                return;
            }
        }

        // These are messages that are handled "by the node", i.e., not by the connections or the hub connector, hub function, or hub switch
        switch (message.function) {

            case SCMessage.ADDRESS_RESOLUTION:
                if (properties.directConnectEnable) {
                    log.info(device,name,"Node responding to Address Resolution from "+connection.name);
                    sendAddressResolutionAck(message.originating, properties.directConnectAcceptURIs.split(" "), message.id);
                } else {
                    log.info(device,name,"Node rejecting Address Resolution from "+connection.name);
                    sendErrorResponse(message, ErrorClass.COMMUNICATION, ErrorCode.OPTIONAL_FUNCTIONALITY_NOT_SUPPORTED, "This node does not accept direct connections");
                }
                break;

            case SCMessage.ADDRESS_RESOLUTION_ACK:
                SCPayloadAddressResolutionAck ack = new SCPayloadAddressResolutionAck(message.payload);  // parse the payload to get the string array
                SCAddressResolution existing = getAddressResolution(message.originating);  // see if we have an existing mapping
                if (existing != null) { existing.urls = ack.urls;   existing.freshness.restart(); } // if existing, replace
                else addressResolutions.add(new SCAddressResolution(message.originating, ack.urls, properties.addressResolutionFreshnessLimit));
                if (tap != null) tap.addressResolutionAck(connection,message,ack);
                break;

            case SCMessage.BVLC_RESULT:
                SCPayloadBVLCResult result = new SCPayloadBVLCResult(message.payload);
                if (result.forFunction == SCMessage.ADDRESS_RESOLUTION) {
                    // this must be a NAK since an ACK would have used ADDRESS_RESOLUTION_ACK
                    log.info(device,name,"Node received a NAK for address resolution from "+message.originating+" via "+connection.name);
                    existing = getAddressResolution(message.originating); // see if we have an existing mapping
                    if (existing != null) { existing.urls = new String[0]; existing.freshness.restart(); }   // if existing, replace
                    else addressResolutions.add(new SCAddressResolution(message.originating, new String[0], properties.addressResolutionFreshnessLimit));
                    // TODO should we have a longer refresh for nak? indefinite?
                }
                else {
                    log.protocol(device,name,"Node received an unexpected BVLC_RESULT from "+connection.name+" "+message);
                }
                if (tap != null) tap.bvlcResult(connection,message,result);
                break;

            case SCMessage.ADVERTISEMENT_SOLICITATION:
                log.info(device,name,"Node Responding to Advertisement Solicitation");
                sendAdvertisement(message.originating, hubConnector.getStateAsInt(), properties.directConnectEnable, properties.maxBVLCLengthAccepted, properties.maxNPDULengthAccepted);
                break;

            case SCMessage.ADVERTISEMENT:
                // TODO do we care? I feel like we will want this to be remembered on a per-destination basis eventually
                SCPayloadAdvertisement advertisement = new SCPayloadAdvertisement(message.payload);
                if (tap != null) tap.advertisement(connection,message,advertisement);
                break;

            case SCMessage.ENCAPSULATED_NPDU:
                datalink.onIncomingNPDU(message);
                break;

            default:
                log.implementation(connection,"Node received unexpected message function "+message);
        }
    }

    @Override public String toString() {
        return "SCNode "+name;
    }

    //////// accessors /////////

    public SCTLSManager  getTLSManager()           { return tlsManager; }

    public void dump(String prefix) {
        Shell.println(prefix+"--- Node ---");
        Shell.println(prefix+"name:\""+name+"\" state:"+state+" timer:"+timer+"");
        hubConnector.dump(prefix+"   ");
        if (hubFunction != null) hubFunction.dump(prefix+"   ");
        if (nodeSwitch != null)  nodeSwitch.dump(prefix+"   ");
    }

}
