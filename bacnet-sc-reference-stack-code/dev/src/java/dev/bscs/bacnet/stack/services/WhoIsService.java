// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.stack.services;

import dev.bscs.bacnet.stack.*;
import dev.bscs.bacnet.stack.constants.UnconfirmedServiceChoice;
import dev.bscs.bacnet.stack.objects.DeviceObject;
import dev.bscs.bacnet.stack.objects.DeviceObjectProperties;
import dev.bscs.common.Formatting;
import dev.bscs.common.Log;

public class WhoIsService {

    private static final BACnetLog log = new BACnetLog(WhoIsService.class);

    public static void request(Device device, int instance) {
        request(device, 65535, new byte[0], instance, instance);
    }

    public static void request(Device device, int low, int high) {
        request(device, 65535, new byte[0], low, high);
    }

    public static void request(Device device, int dnet, byte[] dadr) {
        request(device, dnet, dadr, 0, 0x3FFFFE);
    }

    public static void request(Device device, int dnet, byte[] dadr, int low, int high) {
        try {
            ASNBuffer request = new ASNBuffer();
            if (!(low == 0 && high == 0x3FFFFE)) {
                request.writeUnsigned(0,low);
                request.writeUnsigned(1,high);
            }
            request.flip();
            APDU apdu = new APDU(APDU.UNCONF_REQ, UnconfirmedServiceChoice.WI, request);
            device.applicationLayer.unconfirmedRequest(dnet,dadr,apdu);
        }
        catch (Failure e) {
            log.implementation(device,"WI","initiate failed! "+e);
        }
    }

    public static void indication(Device device, int snet, byte[] sadr, APDU apdu, AuthData auth) {
        ASNBuffer request = apdu.serviceRequest;
        try {
            long low;
            long high;
            low   = request.readUnsigned(0,0);
            high  = request.readUnsigned(1,0x3FFFFE);
            DeviceObject deviceObject = device.deviceObject;
            DeviceObjectProperties deviceProperties = deviceObject.properties;
            int deviceInstance = deviceProperties.instance;
            if (deviceInstance >= low && deviceInstance <= high) IAmService.request(device,snet,sadr);
        }
        catch (Failure e) {
            log.error("Malformed WhoIs received from "+snet+":"+ Formatting.toMac(sadr)+" ["+Formatting.toHex(request.getBytes())+"]");
        }
    }

}
