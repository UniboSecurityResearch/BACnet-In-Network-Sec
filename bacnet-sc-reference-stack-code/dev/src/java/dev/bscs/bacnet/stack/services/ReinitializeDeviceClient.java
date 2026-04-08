// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.stack.services;

import dev.bscs.bacnet.stack.*;
import dev.bscs.bacnet.stack.constants.ConfirmedServiceChoice;

public abstract class ReinitializeDeviceClient extends ConfirmedServiceClient {

    private static final BACnetLog log = new BACnetLog(ReinitializeDeviceClient.class);

    //////////////////////////////////////////////////
    // ReinitializeDevice API for application code //
    /////////////////////////////////////////////////

    // these must be overridden by implementation subclass to handle the response
    abstract void success(Device device, AuthData auth);
    abstract void failure(Device device, Failure failure, AuthData auth);

    public void request(Device device, int dnet, byte[] dadr, int state) {
        request(device, dnet, dadr, state, null);
    }
    public void request(Device device, int dnet, byte[] dadr, int state, String password) {
        try {
            ASNBuffer request = new ASNBuffer();
            request.writeEnumerated(0,state);
            if (password != null) request.writeCharacterString(1,password);
            request.flip();
            APDU apdu = new APDU(APDU.CONF_REQ, ConfirmedServiceChoice.RD, request);
            request(device,dnet,dadr,apdu);
        }
        catch (Failure e) {
            log.implementation(device,"RD"," initiate failed! "+e);
        }
    }

    //////////////////////////////////////////////////////////
    // ConfirmedServiceClient interface to ApplicationLayer //
    //////////////////////////////////////////////////////////

    // positive response in from the network...
    @Override public void clientConfirmAck(Device device, int snet, byte[] sadr, int serviceChoice, int invokeID, AuthData auth) {
        success(device,auth);
    }
    // negative response in from the network...
    @Override public void clientConfirmFailure(Device device, int snet, byte[] sadr, int serviceChoice, int invokeID, Failure failure, AuthData auth) {
        failure(device,failure,auth);
    }




}
