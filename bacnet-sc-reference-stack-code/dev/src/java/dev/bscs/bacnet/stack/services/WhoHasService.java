// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.stack.services;

import dev.bscs.bacnet.stack.*;
import dev.bscs.bacnet.stack.constants.UnconfirmedServiceChoice;
import dev.bscs.bacnet.stack.objects.BACnetObject;
import dev.bscs.bacnet.stack.objects.DeviceObject;
import dev.bscs.bacnet.stack.objects.DeviceObjectProperties;
import dev.bscs.common.Formatting;
import dev.bscs.common.Log;

public class WhoHasService {

    private static final BACnetLog log = new BACnetLog(WhoHasService.class);

    public static void indication(Device device, int snet, byte[] sadr, APDU apdu, AuthData auth) {
        ASNBuffer request = apdu.serviceRequest;
        try {
            long low          = request.readUnsigned(0,-1);
            long high         = request.readUnsigned(1,-1);
            int objectID      = request.readObjectIdentitfier(2,-1);
            String objectName = request.readCharacterString(3,null);
            // error checking first
            if ( (low != -1 && high == -1) || (low == -1 && high != -1) ) { log.protocol(device,"WH","Received illegal WhoHas - either both or neither low and and high is required."); return; }
            if (objectID == -1 && objectName == null) { log.protocol(device,"WH","Received illegal WhoHas - either objectID or objectname is is required."); return; }
            DeviceObject deviceObject = device.deviceObject;
            DeviceObjectProperties deviceProperties = deviceObject.properties;
            int deviceInstance = deviceProperties.instance;
            if ((low != -1) && (deviceInstance < low || deviceInstance > high)) return; // not for our instance
            BACnetObject found = (objectID != -1)? device.findObject(objectID) : device.findObject(objectName);
            if (found != null) IHaveService.request(device, snet, sadr, found.objectIdentifier, found.objectName);
        }
        catch (Failure e) {
            log.error(device,"WH","Malformed WhoIs received from "+snet+":"+ Formatting.toMac(sadr)+" ["+Formatting.toHex(request.getBytes())+"]");
        }
    }

}
