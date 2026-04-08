// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.stack.services;

import dev.bscs.bacnet.stack.*;
import dev.bscs.bacnet.stack.constants.UnconfirmedServiceChoice;
import dev.bscs.bacnet.stack.data.BACnetObjectType;
import dev.bscs.bacnet.stack.data.base.BACnetObjectIdentifier;
import dev.bscs.bacnet.stack.objects.DeviceObject;
import dev.bscs.bacnet.stack.objects.DeviceObjectProperties;
import dev.bscs.common.Formatting;
import dev.bscs.common.Log;

public class IHaveService {

    private static final BACnetLog log = new BACnetLog(IHaveService.class);

    public static void request(Device device, int dnet, byte[] dadr, int objectID, String objectName) {
        try {
            DeviceObject deviceObject = device.deviceObject;
            ASNBuffer response = new ASNBuffer(100);
            response.writeObjectIdentitfier(deviceObject.objectIdentifier);
            response.writeObjectIdentitfier(objectID);
            response.writeCharacterString(objectName);
            response.flip();
            APDU apdu = new APDU(APDU.UNCONF_REQ, UnconfirmedServiceChoice.IH, response);
            device.applicationLayer.unconfirmedRequest(dnet, dadr, apdu);
        }
        catch (Failure e) {
            log.implementation(device,"I-Have","request failed! "+e);
        }
    }

    public static void indication(Device device, int snet, byte[] sadr, APDU apdu, AuthData auth) {
        ASNBuffer serviceAck = apdu.serviceRequest;
        // don't care
    }

}
