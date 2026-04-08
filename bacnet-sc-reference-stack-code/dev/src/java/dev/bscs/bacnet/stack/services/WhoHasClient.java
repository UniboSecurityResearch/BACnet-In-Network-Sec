// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.stack.services;


import dev.bscs.bacnet.stack.*;
import dev.bscs.bacnet.stack.constants.UnconfirmedServiceChoice;
import dev.bscs.common.Formatting;
import dev.bscs.common.Log;

public abstract class WhoHasClient extends UnconfirmedServiceClient {

    private static final BACnetLog log = new BACnetLog(WhoHasClient.class);

    /////////////////////////////////////
    // WhoHas API for application code //
    /////////////////////////////////////

    // this must be overridden by implementation subclass to handle the response data
    // this can be called multiple times, so don't forget to call device.applicationLayer.removeClient(..) when finished.
    abstract protected void success(Device device, int deviceID, int objectID, String objectName, AuthData auth);

    public void request(Device device, int dnet, byte[] dadr, int objectID, String objectName, int low, int high) {
        try {
            ASNBuffer request = new ASNBuffer();
            if (low != -1) {
                request.writeUnsigned(0,low);
                request.writeUnsigned(1,high);
            }
            if (objectName == null || objectName.isEmpty())
                request.writeObjectIdentitfier(2,objectID);
            else
                request.writeCharacterString(3,objectName);
            request.flip();
            APDU apdu = new APDU(APDU.UNCONF_REQ, UnconfirmedServiceChoice.WH, request);
            request(device, dnet, dadr, apdu, dnet == 65535 ? -1 : dnet, dadr, UnconfirmedServiceChoice.IH);
        }
        catch (Failure e) {
            log.implementation(device,"WH","initiate failed! "+e);
        }
    }

    ////////////////////////////////////////////////////////////
    // UnconfirmedServiceClient interface to ApplicationLayer //
    ////////////////////////////////////////////////////////////

    // positive response in from the network...
    @Override public void clientIndication(Device device, int snet, byte[] sadr, int serviceChoice, ASNBuffer serviceData, AuthData auth) {
        try {
            int deviceID = serviceData.readObjectIdentitfier();
            int objectID = serviceData.readObjectIdentitfier();
            String objectName = serviceData.readCharacterString();
            success(device, deviceID, objectID, objectName, auth);
        }
        catch (Failure e) {
            log.error(device,"WH","Malformed I-Am from "+ Formatting.toNetMac(snet,sadr)+" ["+Formatting.toHex(serviceData.getBytes())+"]");
        }

    }




}
