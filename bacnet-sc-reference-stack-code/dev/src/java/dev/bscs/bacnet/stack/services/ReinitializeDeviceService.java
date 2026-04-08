// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.stack.services;

import dev.bscs.bacnet.stack.*;
import dev.bscs.bacnet.stack.constants.ConfirmedServiceChoice;
import dev.bscs.bacnet.stack.constants.ErrorClass;
import dev.bscs.bacnet.stack.constants.ErrorCode;
import dev.bscs.bacnet.stack.constants.ReinitializeDeviceState;
import dev.bscs.common.Formatting;
import dev.bscs.common.Log;

public class ReinitializeDeviceService {

    private static final BACnetLog log = new BACnetLog(ReinitializeDeviceService.class);

    public static final String NO_PASSWORD = "__no_password__";  // can't use null for properties

    public static void indication(Device device, int snet, byte[] sadr, APDU apdu, AuthData auth) {
        ASNBuffer request = apdu.serviceRequest;
        int invokeID      = apdu.inv;
        try {
            int state = request.readEnumerated(0);
            String receivedPassword = request.readCharacterString(1,NO_PASSWORD);
            String requiredPassword = device.configProperites.getString("device.reinitPassword",NO_PASSWORD);
            if (!receivedPassword.equals(requiredPassword)) {
                device.applicationLayer.sendError(snet, sadr, ConfirmedServiceChoice.RD, invokeID, ErrorClass.SECURITY, ErrorCode.PASSWORD_FAILURE);
                log.error(device,"RD",receivedPassword.equals(NO_PASSWORD)?"Password required but none received":"Received password does not match");
                return;
            }
            switch (state) {
                case ReinitializeDeviceState.ACTIVATE_CHANGES:
                    device.applicationLayer.sendError(snet, sadr, ConfirmedServiceChoice.RD, invokeID, ErrorClass.SERVICES, ErrorCode.OPTIONAL_FUNCTIONALITY_NOT_SUPPORTED);
                    break;
                case ReinitializeDeviceState.START_BACKUP:
                case ReinitializeDeviceState.END_BACKUP:
                case ReinitializeDeviceState.START_RESTORE:
                case ReinitializeDeviceState.END_RESTORE:
                case ReinitializeDeviceState.ABORT_RESTORE:
                    log.error(device,"RD","Rejecting RD from "+ Formatting.toNetMac(snet,sadr)+" with state "+state);
                    if (device.applicationLayer.isDisabled()) device.applicationLayer.sendError(snet, sadr, ConfirmedServiceChoice.RD, invokeID, ErrorClass.SERVICES, ErrorCode.COMMUNICATION_DISABLED); // 135.1 Clause 9.24.2.3
                    else                                      device.applicationLayer.sendError(snet, sadr, ConfirmedServiceChoice.RD, invokeID, ErrorClass.SERVICES, ErrorCode.OPTIONAL_FUNCTIONALITY_NOT_SUPPORTED);
                    break;
                case ReinitializeDeviceState.COLDSTART:
                case ReinitializeDeviceState.WARMSTART:
                    log.error(device,"RD","Accepting RD from "+ Formatting.toNetMac(snet,sadr)+" with state "+state);
                    device.applicationLayer.sendAck(snet,sadr,ConfirmedServiceChoice.RD,invokeID);
                    device.reinitialize(state);
                    break;
            }
        }
        catch (Failure.Reject e) {
            log.error(device,"RD","Rejecting RD from "+Formatting.toNetMac(snet,sadr)+" inv"+invokeID+" reject="+e);
            device.applicationLayer.sendReject(snet, sadr, invokeID, e.rejectReason);
        }
        catch (Failure.Error e) {
            log.error(device,"RD","Sending Error for RD from "+Formatting.toNetMac(snet,sadr)+" inv"+invokeID+" error="+e);
            device.applicationLayer.sendError(snet, sadr, ConfirmedServiceChoice.RP,invokeID,e.errorClass,e.errorCode);
        }
    }
}
