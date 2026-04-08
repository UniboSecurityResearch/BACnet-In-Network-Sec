// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.stack;

import dev.bscs.bacnet.bacnetsc.SCOption;
import dev.bscs.bacnet.stack.constants.*;
import dev.bscs.bacnet.stack.data.BACnetAddress;
import dev.bscs.bacnet.stack.services.*;
import dev.bscs.common.Application;
import dev.bscs.common.Formatting;
import dev.bscs.common.Timer;

import java.util.HashMap;
import java.util.Map;

/**
 * Simple application layer: one outstanding request and no segmentation. Timeouts and retries are the user's
 * responsibility. It tries to be as lean as possible, but it *is* sophisticated enough to support the concept of
 * "bindings" with a collection of {@link Binding} that it learns from I-Am messages, and maintains separate lists
 * for those it 'wants' to bind to and those it just overhears (in a bounded list).
 * @author drobin
 */
public class ApplicationLayer {

    private static final BACnetLog log = new BACnetLog(ApplicationLayer.class);

    public Device                   device;
    public Map<Integer, Binding>    requestedBindings;  // I-Am's of devices we want to bind to
    public Map<Integer, Binding>    discoveredBindings; // I-Am's we didn't specifically ask for
    public int                      discoveryLimit;     // limits the size of discoveredBindings
    public ConfirmedServiceClient   confirmedClient;       // the recipient of our one outstanding request.  (make this a list someday)
    public UnconfirmedServiceClient unconfirmedClient;     // a "listener" for unconfirmed services, like I-Am. (make this a list someday)
    public int                      dccMode = DCCMode.ENABLE;
    public Timer                    dccTimer = new Timer();

    public int                      invokeID = 1;

    public ApplicationLayer(Device device) {
        this.device        = device;
        requestedBindings  = new HashMap<>(); // I-Am's of devices we want to bind to
        discoveredBindings = new HashMap<>(); // used for discovery - I-Am's we didn't specifically ask for
        discoveryLimit     = Application.configuration.getInteger("stack.discoveryLimit",100);
    }

    public boolean isDisabled() {
        return getDCCMode() == DCCMode.DISABLE;
    }
    public boolean isInitiationDisabled() {
        return getDCCMode() == DCCMode.DISABLE_INITIATION;
    }
    public int getDCCMode() {
        if (dccTimer.expired()) { dccMode = DCCMode.ENABLE; dccTimer.clear(); }
        return dccMode;
    }
    public void setDCCMode(int mode, long timeout) {  // timeout == -1 for indefinite
        dccMode = mode;
        if (timeout>=0) dccTimer.start(timeout); else dccTimer.clear();  // a "cleared" timer is never "expired"
    }


    public int  newInvokeID() { invokeID++; if (invokeID > 255) invokeID = 0; return invokeID; }

    public void addConfirmedClient(ConfirmedServiceClient client)      { confirmedClient = client; }
    public void addUnconfirmedClient(UnconfirmedServiceClient client)  { unconfirmedClient = client; }

    public void removeClient(ConfirmedServiceClient client)   { if ( confirmedClient == client)   confirmedClient = null; }
    public void removeClient(UnconfirmedServiceClient client) { if ( unconfirmedClient == client) unconfirmedClient = null; }

    public void confirmedRequest(ConfirmedServiceClient client, int dnet, byte[] dadr, APDU apdu) {
        addConfirmedClient(client);
        sendAPDU(dnet,dadr,apdu);
    }
    public void unconfirmedRequest(int dnet, byte[] dadr, APDU apdu) {
        sendAPDU(dnet,dadr,apdu);
    }
    public void unconfirmedRequest(UnconfirmedServiceClient client, int dnet, byte[] dadr, APDU apdu) {
        addUnconfirmedClient(client);
        sendAPDU(dnet,dadr,apdu);
    }

    public void addAddressBinding(Binding binding, AuthData auth) { addDeviceBinding(binding); }

    public BACnetAddress wantDeviceAddress(int instance) {
        Binding binding = getOrMakeDeviceBinding(instance);
        // if we have address information, return it
        if (binding.dnet != -1 && binding.dadr != null) return new BACnetAddress(binding.dnet,binding.dadr);
        // otherwise, issue a Who-Is (every so often) to find it...
        if (binding.whoIsPacingTimer.expired()) {
            WhoIsService.request(device,instance,instance);
            binding.whoIsPacingTimer.start(device.configProperites.getInteger("app.whoIsPacingDelay",10000));
        }
        // ...but in the mean time, return null
        return null;
    }

    public Binding getBinding(int instance) {
        Binding result = requestedBindings.get(instance);
        if (result != null) return result;
        return discoveredBindings.get(instance);
    }

    public Map<Integer,Binding> getRequestedBindings() {
        return new HashMap<>(requestedBindings);
    }
    public Map<Integer,Binding> getDiscoveredBindings() {
        return new HashMap<>(discoveredBindings);
    }
    public Map<Integer,Binding> getAllBindings() {
        Map<Integer,Binding> combined = new HashMap<>(requestedBindings);
        combined.putAll(discoveredBindings);
        return combined;
    }

    protected Binding getOrMakeDeviceBinding(int instance) {
        Binding binding = requestedBindings.get(instance);
        if (binding == null) {
            binding = new Binding();
            addDeviceBinding(new Binding());
        }
        return binding;
    }

    protected void addDeviceBinding(Binding binding) {  // adds device binding to one of the two maps (requested or discovered)
        // did we ask for this information?  An entry (possibly empty) should be in requestedDeviceBindings if we did
        Binding entry = requestedBindings.get(binding.instance);
        if (entry != null) {
            requestedBindings.put(binding.instance,binding); // replace old binding with new
        }
        else {   // if we didn't ask for it, then see if it's already in the discovered map
            entry = discoveredBindings.get(binding.instance);
            if (entry != null) {
                discoveredBindings.put(binding.instance,binding); // we've already seen this one, so replace it
            }
            else {  // if there's nothing there either...  then check if there's room in discovery table
                if (discoveredBindings.size() <= discoveryLimit) {
                    discoveredBindings.put(binding.instance, binding);
                }
            }
        }
    }

    public void sendError(int dnet, byte[] dadr, int serviceChoice, int invoke, int errorClass, int errorCode) {
        try {
            ASNBuffer asn = new ASNBuffer(10);
            asn.writeEnumerated(errorClass);
            asn.writeEnumerated(errorCode);
            asn.flip();
            APDU apdu = new APDU(APDU.ERROR,serviceChoice,invoke,asn);
            sendAPDU(dnet,dadr,apdu);
        }
        catch (Failure e) {
            log.implementation(device,"AL","sendError failed! "+e);
        }
    }

    public void sendAck(int dnet, byte[] dadr, int serviceChoice, int invoke) {
        APDU apdu = new APDU(APDU.SIMPLE_ACK,serviceChoice,invoke,null);
        sendAPDU(dnet,dadr,apdu);
    }

    public void sendAbort(int dnet, byte[] dadr, int invoke, int abortReason, boolean server) {
        APDU apdu = new APDU(APDU.ABORT,invoke,abortReason,server);
        sendAPDU(dnet,dadr,apdu);
    }

    public void sendReject(int dnet, byte[] dadr, int invoke, int reason) {
        log.info(device,"AL","sendReject()<--  dnet="+dnet+" dadr="+ Formatting.toMac(dadr)+" reason="+reason);
        APDU apdu = new APDU(APDU.REJECT,invoke,reason);
        sendAPDU(dnet,dadr,apdu);
    }

    public void sendAPDU(int dnet, byte[] dadr, APDU apdu) {
        boolean der = apdu.pduType == APDU.CONF_REQ;
        sendAPDU(dnet,dadr,apdu,0,der, AuthData.makeSecurePath());
    }

    public void sendAPDU(int dnet, byte[] dadr, APDU apdu, int priority, boolean der, AuthData auth) {
        if (!canSend(apdu.pduType,apdu.serviceChoice)) {
            log.info(device,"AL","sendAPDU()<-- Disabled, can't send "+apdu);
            return;
        }
        log.info(device,"AL","sendAPDU()<-- dnet="+dnet+" dadr="+ Formatting.toMac(dadr)+" apdu={"+apdu+"}");
        NPDU npdu = new NPDU();
        npdu.der = der;
        npdu.priority = priority;
        npdu.payload = apdu.generate();
        npdu.isAPDU = true;
        npdu.hopCount = 255;
        device.networkLayer.sendNPDU(dnet, dadr, npdu, auth);
    }

    public boolean canSend(int pduType, int serviceChoice) {
        if (isDisabled()) { // if we are disabled, the only thing that can get through is an answer to DCC or RD
            return (pduType == APDU.SIMPLE_ACK || pduType == APDU.ERROR) && (serviceChoice == ConfirmedServiceChoice.DCC || serviceChoice == ConfirmedServiceChoice.RD);
        }
        if (isInitiationDisabled()) { // if we are initiation disabled, the only thing that can get through is answers or an I-Am
            return pduType != APDU.CONF_REQ && (pduType != APDU.UNCONF_REQ || serviceChoice == UnconfirmedServiceChoice.IA);
        }
        return true;
    }

    /////////////// From Network Layer /////////////////////

    // From the Network Layer to us
    public void nUnitdataIndication(int snet, byte[] sadr, boolean broadcast, byte[] data, int priority, boolean der, AuthData auth) {
        log.info(device,"AL","-->nUnitdataIndication() from "+Formatting.toNetMac(snet,sadr)+" data="+ Formatting.toHex(data));
        if (data == null || data.length == 0) { log.error(device,"AL","-->nUnitdataIndication() given zero length APDU!"); return; }
        APDU apdu = new APDU(data);
        if (apdu.pduType == -1) {
            log.error(device,"AL","Received malformed APDU from "+Formatting.toNetMac(snet,sadr)+(apdu.failure!=null?apdu.failure:""));
            return;
        }
        // if we are in DISABLE state, then only RD and DCC is allowed through
        if (isDisabled() && !(apdu.pduType == APDU.CONF_REQ && (apdu.serviceChoice == ConfirmedServiceChoice.RD) || apdu.serviceChoice == ConfirmedServiceChoice.DCC)) return;
        // here is where we check the auth data, which likely originated as "data options" in SC
        // the only thing we allow is TYPE_SECURE_PATH with no data
        if (auth != null) for (SCOption option : auth.getOptions()) {
            if (option.mustUnderstand && option.type != SCOption.TYPE_SECURE_PATH) {
                log.error(device,"AL","Received unknown 'must understand' option from "+Formatting.toNetMac(snet,sadr)+" "+apdu);
                if (apdu.pduType == APDU.CONF_REQ && !broadcast) {
                    sendError(snet, sadr, apdu.serviceChoice, apdu.inv, ErrorClass.COMMUNICATION, ErrorCode.HEADER_NOT_UNDERSTOOD);
                }
                return; // bad option, always return, even if didn't send error
            }
            else if (option.type == SCOption.TYPE_SECURE_PATH && option.data != null) {
                log.error(device,"AL","Received unexpected payload with TYPE_SECURE_PATH from "+Formatting.toNetMac(snet,sadr)+" "+apdu);
                if (apdu.pduType == APDU.CONF_REQ && !broadcast) {
                    // STANDARD: there is no standard error code for "unexpected payload"
                    sendError(snet, sadr, apdu.serviceChoice, apdu.inv, ErrorClass.COMMUNICATION, ErrorCode.HEADER_ENCODING_ERROR);
                    return;
                }
                return; // bad option, always return, even if didn't send error
            }
        }
        if (apdu.serviceRequest != null) apdu.serviceRequest.mark(); // mark the position in the buffer where the service data begins so if you need to hand the buffer to more then one person, you can reset() in between.
        switch (apdu.pduType) {
            case APDU.UNCONF_REQ:
                switch (apdu.serviceChoice) {
                    case UnconfirmedServiceChoice.WI:
                        WhoIsService.indication(device, snet, sadr, apdu, auth);
                        break;
                    case UnconfirmedServiceChoice.WH:
                        WhoHasService.indication(device, snet, sadr, apdu, auth);
                        break;
                    case UnconfirmedServiceChoice.IA:
                        IAmService.indication(device, snet, sadr, apdu, auth);
                        if (unconfirmedClient != null && unconfirmedClient.clientCheckMatch(snet,sadr,apdu.serviceChoice)) {
                            apdu.serviceRequest.reset(); // reset since this is the second person to consume the service data
                            unconfirmedClient.clientIndication(device,snet,sadr,apdu.serviceChoice,apdu.serviceRequest,auth);
                        }
                        break;
                    case UnconfirmedServiceChoice.IH:
                        IHaveService.indication(device, snet, sadr, apdu, auth);
                        if (unconfirmedClient != null && unconfirmedClient.clientCheckMatch(snet,sadr,apdu.serviceChoice)) {
                            apdu.serviceRequest.reset(); // reset since this is the second person to consume the service data
                            unconfirmedClient.clientIndication(device,snet,sadr,apdu.serviceChoice,apdu.serviceRequest,auth);
                        }
                        break;
                }
                break;
            case APDU.CONF_REQ:
                if (broadcast) {
                    log.error(device,"AL","Received broadcast confirmed service - ignoring");
                    return;
                }
                switch (apdu.serviceChoice) {
                    case ConfirmedServiceChoice.RP:
                        ReadPropertyService.indication(device, snet, sadr, apdu, auth);
                        break;
                    case ConfirmedServiceChoice.WP:
                        WritePropertyService.indication(device, snet, sadr, apdu, auth);
                        break;
                    case ConfirmedServiceChoice.RPM:
                        ReadPropertyMultipleService.indication(device, snet, sadr, apdu, auth);
                        break;
                    case ConfirmedServiceChoice.RD:
                        ReinitializeDeviceService.indication(device, snet, sadr, apdu, auth);
                        break;
                    case ConfirmedServiceChoice.DCC:
                        DCCService.indication(device, snet, sadr, apdu, auth);
                        break;
                    default:
                        sendReject(snet, sadr, apdu.inv, RejectReason.UNRECOGNIZED_SERVICE);
                        break;
                }
                break;
            case APDU.CONF_ACK:
                // check match for our *one* outstanding request
                ConfirmedServiceClient client = confirmedClient;
                if (client != null && client.clientCheckMatch(snet,sadr,apdu.serviceChoice,invokeID)) {
                    removeClient(client);
                    client.clientConfirmData(device, snet, sadr, apdu.serviceChoice, invokeID, apdu.serviceRequest, auth);
                }
                else {
                    log.error(device,"AL","Received unexpected ConfirmedService-ACK from "+ Formatting.toNetMac(snet,sadr)+" svc="+apdu.serviceChoice);
                }
                break;
            case APDU.SIMPLE_ACK:
                // check match for our *one* outstanding request
                client = confirmedClient;
                if (client != null && client.clientCheckMatch(snet,sadr,apdu.serviceChoice,invokeID)) {
                    removeClient(client);
                    client.clientConfirmAck(device, snet, sadr, apdu.serviceChoice, invokeID, auth);
                }
                else {
                    log.error(device,"AL","Received unexpected Simple-ACK from "+ Formatting.toNetMac(snet,sadr)+" svc="+apdu.serviceChoice);
                }
                break;
            case APDU.ERROR:
                // check match for our *one* outstanding request
                client = confirmedClient;
                if (client != null && client.clientCheckMatch(snet,sadr,apdu.serviceChoice,invokeID)) {
                    removeClient(client);
                    try {
                        int errorClass = apdu.serviceRequest.readEnumerated();
                        int errorCode  = apdu.serviceRequest.readEnumerated();
                        client.clientConfirmFailure(device, snet, sadr, apdu.serviceChoice, invokeID, new Failure.Error(errorClass,errorCode), auth);
                    }
                    catch (Failure.Reject e)  {
                        log.error(device,"AL","Received Malformed Error from "+ Formatting.toNetMac(snet,sadr)+" svc="+apdu.serviceChoice+" e="+e);
                        client.clientConfirmFailure(device, snet, sadr, apdu.serviceChoice, invokeID, e, auth);
                    }
                }
                else {
                    log.error(device,"AL","Received unexpected Error from "+ Formatting.toNetMac(snet,sadr)+" svc="+apdu.serviceChoice);
                }
                break;
            case APDU.REJECT:
                // check match for our *one* outstanding request
                client = confirmedClient;
                if (client != null && client.clientCheckMatch(snet,sadr,apdu.serviceChoice,invokeID)) {
                    removeClient(client);
                    client.clientConfirmFailure(device, snet, sadr, apdu.serviceChoice, invokeID, new Failure.Reject(apdu.reason), auth);
                }
                else {
                    log.error(device,"AL","Received unexpected Reject from "+ Formatting.toNetMac(snet,sadr)+" svc="+apdu.serviceChoice);
                }
                break;
            case APDU.ABORT:
                // check match for our *one* outstanding request
                client = confirmedClient;
                if (client != null && client.clientCheckMatch(snet,sadr,apdu.serviceChoice,invokeID)) {
                    removeClient(client);
                    client.clientConfirmFailure(device, snet, sadr, apdu.serviceChoice, invokeID, new Failure.Abort(apdu.reason), auth);
                }
                else {
                    log.error(device,"AL","Received unexpected Abort from "+ Formatting.toNetMac(snet,sadr)+" svc="+apdu.serviceChoice);
                }
                break;
        }
    }

}
