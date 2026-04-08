// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.stack.services;

import dev.bscs.bacnet.stack.*;
import dev.bscs.bacnet.stack.constants.ConfirmedServiceChoice;
import dev.bscs.bacnet.stack.constants.ErrorClass;
import dev.bscs.bacnet.stack.constants.ErrorCode;
import dev.bscs.common.Formatting;
import dev.bscs.common.Log;

public class DCCService {

    private static final BACnetLog log = new BACnetLog(IAmService.class);

    public static void indication(Device device, int snet, byte[] sadr, APDU apdu, AuthData auth) {
        ASNBuffer request = apdu.serviceRequest;
        int invokeID      = apdu.inv;
        try {
            long   time_duration   = request.readUnsigned(0,-1);    // minutes
            int    enable_disable  = request.readEnumerated(1);
            String password        = request.readCharacterString(2,null);
            String requiredPassword = device.configProperites.getString("device.dccPassword",null);
            if (requiredPassword != null) {
                if (password == null || ! password.equals(requiredPassword)) {
                    device.applicationLayer.sendError(snet, sadr, ConfirmedServiceChoice.DCC, invokeID, ErrorClass.SECURITY, ErrorCode.PASSWORD_FAILURE);
                    if (password == null) log.error(device,"DCC","Password required but none received");
                    else log.error(device,"DCC","Received password does not match");
                    return;
                }
            }
            log.info(device,"DCC","Accepting DCC from "+Formatting.toNetMac(snet,sadr)+" with mode "+enable_disable+" for "+time_duration);
            device.applicationLayer.sendAck(snet,sadr,ConfirmedServiceChoice.DCC,invokeID);
            device.applicationLayer.setDCCMode(enable_disable,time_duration*60*1000);
        }
        catch (Failure.Reject e) {
            log.error(device,"DCC","Rejecting DCC from "+Formatting.toNetMac(snet,sadr)+" inv"+invokeID+" reject="+e);
            device.applicationLayer.sendReject(snet, sadr, invokeID, e.rejectReason);
        }
        catch (Failure.Error e) {
            log.error(device,"DCC","Sending Error for DCC from "+Formatting.toNetMac(snet,sadr)+" inv"+invokeID+" error="+e);
            device.applicationLayer.sendError(snet, sadr, ConfirmedServiceChoice.RP,invokeID,e.errorClass,e.errorCode);
        }
    }

}
