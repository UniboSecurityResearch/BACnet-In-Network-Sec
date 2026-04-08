// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.stack.services;

import dev.bscs.bacnet.stack.APDU;
import dev.bscs.bacnet.stack.ASNBuffer;
import dev.bscs.bacnet.stack.AuthData;
import dev.bscs.bacnet.stack.BACnetLog;
import dev.bscs.bacnet.stack.Device;
import dev.bscs.bacnet.stack.Failure;
import dev.bscs.bacnet.stack.constants.AbortReason;
import dev.bscs.bacnet.stack.constants.ConfirmedServiceChoice;
import dev.bscs.bacnet.stack.constants.ErrorClass;
import dev.bscs.bacnet.stack.constants.ErrorCode;
import dev.bscs.bacnet.stack.constants.RejectReason;
import dev.bscs.bacnet.stack.data.base.BACnetData;
import dev.bscs.bacnet.stack.objects.BACnetObject;
import dev.bscs.common.Formatting;

public class WritePropertyService {

    private static final BACnetLog log = new BACnetLog(WritePropertyService.class);

    public static void indication(Device device, int snet, byte[] sadr, APDU apdu, AuthData auth) {
        ASNBuffer request = apdu.serviceRequest;
        int invokeID = apdu.inv;

        try {
            int objectIdentifier = request.readObjectIdentitfier(0);
            int propertyIdentifier = request.readEnumerated(1);
            int arrayIndex = (int) request.readUnsigned(2, -1);
            BACnetData value = request.readWrappedPrimitive(3);

            if (request.remaining() > 0) {
                throw new Failure.Reject(RejectReason.TOO_MANY_ARGUMENTS);
            }

            BACnetObject object = device.findObject(objectIdentifier);
            if (object == null) {
                throw new Failure.Error(ErrorClass.OBJECT, ErrorCode.UNKNOWN_OBJECT);
            }

            // Normalize wildcard device object identifier to the concrete one.
            objectIdentifier = object.objectIdentifier;

            // This implementation currently supports scalar primitive values only.
            if (arrayIndex != -1) {
                throw new Failure.Error(ErrorClass.PROPERTY, ErrorCode.WRITE_ACCESS_DENIED);
            }

            object.setProperty(propertyIdentifier, value);

            ASNBuffer response = new ASNBuffer(256);
            response.writeObjectIdentitfier(0, objectIdentifier);
            response.writeEnumerated(1, propertyIdentifier);
            response.flip();

            if (response.limit() > apdu.getMaxResponseSize()) {
                device.applicationLayer.sendAbort(snet, sadr, invokeID, AbortReason.SEGMENTATION_NOT_SUPPORTED, true);
            } else {
                APDU responseApdu = new APDU(APDU.CONF_ACK, ConfirmedServiceChoice.WP, invokeID, response);
                device.applicationLayer.sendAPDU(snet, sadr, responseApdu);
            }
        } catch (Failure.Reject e) {
            log.error(device, "WP", "Rejecting WP from " + Formatting.toNetMac(snet, sadr) + " inv" + invokeID + " reject=" + e);
            device.applicationLayer.sendReject(snet, sadr, invokeID, e.rejectReason);
        } catch (Failure.Error e) {
            log.error(device, "WP", "Sending Error for WP from " + Formatting.toNetMac(snet, sadr) + " inv" + invokeID + " error=" + e);
            device.applicationLayer.sendError(snet, sadr, ConfirmedServiceChoice.WP, invokeID, e.errorClass, e.errorCode);
        } catch (Failure.Abort e) {
            log.error(device, "WP", "Aborting WP from " + Formatting.toNetMac(snet, sadr) + " inv" + invokeID + " abort=" + e);
            device.applicationLayer.sendAbort(snet, sadr, invokeID, e.abortReason, true);
        }
    }
}
