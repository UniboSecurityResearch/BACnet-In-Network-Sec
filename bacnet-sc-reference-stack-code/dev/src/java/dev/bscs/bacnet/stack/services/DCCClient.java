// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.stack.services;

import dev.bscs.bacnet.stack.*;
import dev.bscs.bacnet.stack.constants.ConfirmedServiceChoice;

public abstract class DCCClient extends ConfirmedServiceClient {

    private static final BACnetLog log = new BACnetLog(DCCClient.class);

    //////////////////////////////////
    // DCC API for application code //
    //////////////////////////////////

    // these must be overridden by implementation subclass to handle the response data
    abstract void success(Device device, AuthData auth);
    abstract void failure(Device device, Failure failure, AuthData auth);

    public void request(Device device, int dnet, byte[] dadr, int mode) {
        request(device, dnet, dadr, mode, -1, null);
    }
    public void request(Device device, int dnet, byte[] dadr, int mode, String password) {
        request(device, dnet, dadr, mode, -1, password);
    }
    public void request(Device device, int dnet, byte[] dadr, int mode, int time) {
        request(device, dnet, dadr, mode, time, null);
    }
    public void request(Device device, int dnet, byte[] dadr, int mode, int time, String password) {
        try {
            ASNBuffer request = new ASNBuffer();
            if (time>0) request.writeUnsigned(0,time);
            request.writeEnumerated(1,mode);
            if (password != null) request.writeCharacterString(2,password);
            request.flip();
            APDU apdu = new APDU(APDU.CONF_REQ, ConfirmedServiceChoice.DCC, request);
            request(device, dnet, dadr, apdu);
        }
        catch (Failure e) {
            log.implementation(device,"DCC","initiate failed! "+e);
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
