// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.stack.services;

import dev.bscs.bacnet.stack.*;
import dev.bscs.bacnet.stack.constants.ConfirmedServiceChoice;
import dev.bscs.bacnet.stack.constants.ErrorClass;
import dev.bscs.bacnet.stack.constants.ErrorCode;
import dev.bscs.bacnet.stack.data.BACnetObjectType;
import dev.bscs.bacnet.stack.data.base.BACnetData;
import dev.bscs.bacnet.stack.data.base.BACnetObjectIdentifier;
import dev.bscs.common.Formatting;

public abstract class WritePropertyClient extends ConfirmedServiceClient {

    private static final BACnetLog log = new BACnetLog(WritePropertyClient.class);

    private int expectedObjectID;
    private int expectedPropertyID;
    private int expectedIndex;

    ///////////////////////////////////////////
    // WriteProperty API for application code //
    ///////////////////////////////////////////

    // these must be overridden by implementation subclass to handle the response data
    protected abstract void  success(Device device, AuthData auth);
    protected abstract void  failure(Device device, Failure failure, AuthData auth);

    // send request with this, then use success() or failure() to get results.
    public boolean request(Device device, int dnet, byte[] dadr, int objectID, int propertyID, int index, BACnetData data) {
        expectedInvokeID = device.applicationLayer.newInvokeID();
        expectedSadr = dadr;
        expectedSnet = dnet;
        expectedObjectID = objectID;
        expectedPropertyID = propertyID;
        expectedIndex = index;
        try {
            ASNBuffer request = new ASNBuffer();
            request.writeObjectIdentitfier(0,objectID);
            request.writeEnumerated(1,propertyID);
            if (index != -1) request.writeUnsigned(2,index);
            request.writeOpenTag(3);
            request.write(data);
            request.writeCloseTag(3);
            request.flip();
            APDU apdu = new APDU(APDU.CONF_REQ, ConfirmedServiceChoice.WP, expectedInvokeID, request);
            request(device,dnet,dadr,apdu);
            return true;
        }
        catch (Failure e) {
            log.error(device,"WP","Can't initiate WP to "+Formatting.toNetMac(dnet,dadr)+" "+e);
            failure = e;
            return false;
        }
    }

    //////////////////////////////////////////////////////////
    // ConfirmedServiceClient interface to ApplicationLayer //
    //////////////////////////////////////////////////////////

    // positive response in from the network...
    @Override public void clientConfirmData(Device device, int snet, byte[] sadr, int serviceChoice, int invokeID, ASNBuffer serviceData, AuthData auth) {
        try {
            int objectID   = serviceData.readObjectIdentitfier(0);
            int propertyID = serviceData.readEnumerated(1);
            int index      = (int)serviceData.readUnsigned(2,-1);
            if (expectedObjectID == BACnetObjectIdentifier.combine(BACnetObjectType.DEVICE,0x3FFFFF)) expectedObjectID = objectID;
            if (expectedObjectID != objectID || expectedPropertyID != propertyID || expectedIndex != index) throw new Failure.Error(ErrorClass.COMMUNICATION,ErrorCode.INCONSISTENT_PARAMETERS);
            success(device,auth);
        }
        catch (Failure e) {
            log.error(device,"WP","Malformed WriteProperty-ACK from "+snet+":"+ Formatting.toMac(sadr)+" "+e+" ["+Formatting.toHex(serviceData.getBytes())+"]");
            failure(device,e,auth);
        }
    }

    // negative response in from the network...
    @Override public void clientConfirmFailure(Device device, int snet, byte[] sadr, int serviceChoice, int invokeID, Failure failure, AuthData auth) {
        failure(device,failure,auth);
    }

}
