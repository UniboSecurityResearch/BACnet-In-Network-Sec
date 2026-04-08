// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.stack.services;

import dev.bscs.bacnet.stack.*;
import dev.bscs.bacnet.stack.constants.AbortReason;
import dev.bscs.bacnet.stack.constants.ConfirmedServiceChoice;
import dev.bscs.bacnet.stack.constants.ErrorClass;
import dev.bscs.bacnet.stack.constants.ErrorCode;
import dev.bscs.bacnet.stack.data.BACnetPropertyIdentifier;
import dev.bscs.bacnet.stack.data.base.BACnetARRAY;
import dev.bscs.bacnet.stack.data.base.BACnetData;
import dev.bscs.bacnet.stack.objects.BACnetObject;
import dev.bscs.common.Formatting;

import java.util.List;

public class ReadPropertyMultipleService {

    private static final BACnetLog log = new BACnetLog(ReadPropertyService.class);

    public static void indication(Device device, int snet, byte[] sadr, APDU apdu, AuthData auth) {
        ASNBuffer request = apdu.serviceRequest;
        int invokeID      = apdu.inv;
        try {
            ASNBuffer response = new ASNBuffer(1497);
            while (request.remaining() > 0) {
                int objectIdentifier = request.readObjectIdentitfier(0); // ReadAccessSpecification: object-identifier [0] BACnetObjectIdentifier
                BACnetObject object = device.findObject(objectIdentifier); // even if object is not found, we will return individual results/errors below
                if (object != null) objectIdentifier = object.objectIdentifier; // this looks weird but turns wild cards into real instance.
                response.writeObjectIdentitfier(0, objectIdentifier); // ReadAccessResult: object-identifier [0] BACnetObjectIdentifier
                request.readOpenTag(1);                              // ReadAccessSpecification: list-of-property-references [1] SEQUENCE OF BACnetPropertyReference
                response.writeOpenTag(1);                            // ReadAccessResult: list-of-results   [1] SEQUENCE OF SEQUENCE {
                while (!request.peekCloseTag()) { // iterate over the requested properties for this object, each is a BACnetPropertyReference
                    int propertyIdentifier = request.readEnumerated(0);    // BACnetPropertyReference: property-identifier  [0] BACnetPropertyIdentifier
                    int arrayIndex = (int)request.readUnsigned(1,-1);      // BACnetPropertyReference: property-array-index [1] Unsigned OPTIONAL
                    if (object != null && (propertyIdentifier == BACnetPropertyIdentifier.ALL || propertyIdentifier == BACnetPropertyIdentifier.REQUIRED || propertyIdentifier == BACnetPropertyIdentifier.OPTIONAL)) {
                        int[] properties;
                        if      (propertyIdentifier == BACnetPropertyIdentifier.REQUIRED) properties = object.getRequiredPropertyIdentifiers();
                        else if (propertyIdentifier == BACnetPropertyIdentifier.OPTIONAL) properties = object.getOptionalPropertyIdentifiers();
                        else                                                              properties = object.getAllPropertyIdentifiers();
                        for (int property : properties) encodeProperty(device, snet, sadr, invokeID, response, object, property, arrayIndex, auth);
                    }
                    else {
                        encodeProperty(device, snet, sadr, invokeID, response, object, propertyIdentifier, arrayIndex, auth);
                    }
                }
                response.writeCloseTag(1);
                request.readCloseTag(1);
            }
            response.flip();
            if (response.limit() > apdu.getMaxResponseSize()) {
                device.applicationLayer.sendAbort(snet,sadr,invokeID,AbortReason.SEGMENTATION_NOT_SUPPORTED,true);
            }
            else {
                APDU responseApdu = new APDU(APDU.CONF_ACK, ConfirmedServiceChoice.RPM, invokeID, response);
                device.applicationLayer.sendAPDU(snet, sadr, responseApdu);
            }
        }
        catch (Failure.Reject e) {
            log.error(device,"RPM","Rejecting RPM from " + Formatting.toNetMac(snet, sadr) + " inv" + invokeID + " reject=" + e);
            device.applicationLayer.sendReject(snet, sadr, invokeID, e.rejectReason);
        }
        catch (Failure.Abort e) {
            log.error(device,"RPM","Aborting RPM from "+Formatting.toNetMac(snet,sadr)+" inv"+invokeID+" abort="+e);
            device.applicationLayer.sendAbort(snet, sadr, invokeID, e.abortReason,true);
        }
    }
    private static void encodeProperty(Device device, int snet, byte[] sadr, int invokeID, ASNBuffer response,  BACnetObject object, int propertyIdentifier, int arrayIndex, AuthData auth) {
        try {
            response.writeEnumerated(2, propertyIdentifier);             // ReadAccessResult: property-identifier  [2] BACnetPropertyIdentifier
            if (arrayIndex != -1) response.writeUnsigned(3, arrayIndex); // ReadAccessResult: property-array-index [3] Unsigned OPTIONAL
            if (object == null) {
                response.writeOpenTag(5); // ReadAccessResult: property-access-error [5] Error
                response.writeError(ErrorClass.OBJECT, ErrorCode.UNKNOWN_OBJECT);
                response.writeCloseTag(5);
            }
            else {
                BACnetData propertyData = object.findProperty(propertyIdentifier);
                if (propertyData == null) {
                    response.writeOpenTag(5); // ReadAccessResult: property-access-error [5] Error
                    response.writeError(ErrorClass.PROPERTY, ErrorCode.UNKNOWN_PROPERTY);
                    response.writeCloseTag(5);
                }
                else if (arrayIndex != -1 && !(propertyData instanceof BACnetARRAY)) {
                    response.writeOpenTag(5); // ReadAccessResult: property-access-error [5] Error
                    response.writeError(ErrorClass.PROPERTY, ErrorCode.PROPERTY_IS_NOT_AN_ARRAY);
                    response.writeCloseTag(5);
                }
                else if (!object.canReadProperty(propertyIdentifier, auth)) {
                    response.writeOpenTag(5); // ReadAccessResult: property-access-error [5] Error
                    response.writeError(ErrorClass.PROPERTY, ErrorCode.READ_ACCESS_DENIED);
                    response.writeCloseTag(5);
                }
                else {
                    try {
                        if (propertyData instanceof BACnetARRAY && arrayIndex != -1) propertyData = ((BACnetARRAY)propertyData).getMember(arrayIndex);
                        response.writeOpenTag(4);  // property-value [4] ABSTRACT-SYNTAX.&Type
                        response.write(propertyData);
                        response.writeCloseTag(4);
                    }
                    catch (Failure.Error e) {
                        response.writeOpenTag(5); // ReadAccessResult: property-access-error [5] Error
                        response.writeError(e.errorClass, e.errorCode);
                        response.writeCloseTag(5);
                    }
                }
            }
        }
        catch (Failure.Abort e) {
            log.error(device,"RPM","Aborting RPM from "+Formatting.toNetMac(snet,sadr)+" inv"+invokeID+" abort="+e);
            device.applicationLayer.sendAbort(snet, sadr, invokeID, e.abortReason,true);
        }
    }
}
