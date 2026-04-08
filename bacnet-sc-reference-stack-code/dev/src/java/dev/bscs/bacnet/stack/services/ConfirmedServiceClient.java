// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.stack.services;

import dev.bscs.bacnet.stack.*;

import java.util.Arrays;

public abstract class ConfirmedServiceClient {

    protected int     expectedSnet = -1;
    protected byte[]  expectedSadr = null;
    protected int     expectedInvokeID = -1;
    public    Failure failure; // yes, we all have public failures

    protected void request(Device device, int dnet, byte[] dadr, APDU apdu) {
        expectedSnet = dnet;
        expectedSadr = dadr;
        expectedInvokeID = apdu.inv;
        device.applicationLayer.confirmedRequest(this,dnet,dadr,apdu);
    }

    public boolean clientCheckMatch(int snet, byte[] sadr, int serviceChoice, int invokeID) {
        return  (expectedSnet == -1 || snet == expectedSnet) &&
                (expectedSadr == null || expectedSadr.length == 0 || Arrays.equals(sadr,expectedSadr)) &&
                (expectedInvokeID == -1 || expectedInvokeID == invokeID);
    }

    // these "result" callbacks need to be overridden by subclass to do anything useful
    public void clientConfirmData(Device device, int snet, byte[] sadr, int serviceChoice, int invokeID, ASNBuffer serviceData, AuthData auth) {}
    public void clientConfirmAck(Device device, int snet, byte[] sadr, int serviceChoice, int invokeID, AuthData auth) {}
    public void clientConfirmFailure(Device device, int snet, byte[] sadr, int serviceChoice, int invokeID, Failure failure, AuthData auth) {}
}
