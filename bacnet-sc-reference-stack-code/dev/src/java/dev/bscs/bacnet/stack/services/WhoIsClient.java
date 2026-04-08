// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.stack.services;

import dev.bscs.bacnet.stack.*;
import dev.bscs.bacnet.stack.constants.UnconfirmedServiceChoice;
import dev.bscs.common.Formatting;
import dev.bscs.common.Log;

public abstract class WhoIsClient extends UnconfirmedServiceClient {

    private static final BACnetLog log = new BACnetLog(WhoIsClient.class);

    /////////////////////////////////////
    // WhoHas API for application code //
    /////////////////////////////////////

    // this must be overridden by implementation subclass to handle the response data
    // this can be called multiple times, so don't forget to call device.applicationLayer.removeClient(..) when finished.
    protected abstract void success(Device device, Binding binding, AuthData auth);

    public void request(Device device, int instance) {
        request(device, 65535, new byte[0], instance, instance);
    }

    public void request(Device device, int low, int high) {
        request(device, 65535, new byte[0], low, high);
    }

    public void request(Device device, int dnet, byte[] dadr) {
        request(device, dnet, dadr, 0, 0x3FFFFE);
    }

    public void request(Device device, int dnet, byte[] dadr, int low, int high) {
        try {
            ASNBuffer request = new ASNBuffer();
            if (!(low == 0 && high == 0x3FFFFE)) {
                request.writeUnsigned(0,low);
                request.writeUnsigned(1,high);
            }
            request.flip();
            APDU apdu = new APDU(APDU.UNCONF_REQ, UnconfirmedServiceChoice.WI, request);
            request(device,dnet,dadr,apdu, dnet == 65535 ? -1 : dnet, dadr, UnconfirmedServiceChoice.IA);
        }
        catch (Failure e) {
            log.implementation(device,"WI","initiate failed! "+e);
        }
    }

    ////////////////////////////////////////////////////////////
    // UnconfirmedServiceClient interface to ApplicationLayer //
    ////////////////////////////////////////////////////////////

    // positive response in from the network...
    @Override public void clientIndication(Device device, int snet, byte[] sadr, int serviceChoice, ASNBuffer serviceData, AuthData auth) {
        try {
            Binding binding = IAmService.parse(snet,sadr,serviceData);
            success(device,binding,auth);
        }
        catch (Failure e) {
            log.error("Malformed I-Am from "+Formatting.toNetMac(snet,sadr)+" e="+e+" ["+Formatting.toHex(serviceData.getBytes())+"]");
        }

    }



}
