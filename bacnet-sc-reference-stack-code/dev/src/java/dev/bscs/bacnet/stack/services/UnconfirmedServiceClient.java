// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.stack.services;

import dev.bscs.bacnet.stack.*;

import java.util.Arrays;

public abstract class UnconfirmedServiceClient {

    protected int     expectedServiceChoice = -1;
    protected int     expectedSnet = -1;
    protected byte[]  expectedSadr = null;
    protected Failure failure;

    protected void request(Device device, int dnet, byte[] dadr, APDU apdu) {
        expectedSnet = dnet;
        expectedSadr = dadr;
        expectedServiceChoice = apdu.serviceChoice;
        device.applicationLayer.unconfirmedRequest(dnet,dadr,apdu);
    }

    protected void request(Device device, int dnet, byte[] dadr, APDU apdu, int expectedSnet, byte[] expectedSadr, int expectedServiceChoice) {
        this.expectedSnet = expectedSnet;
        this.expectedSadr = expectedSadr;
        this.expectedServiceChoice = expectedServiceChoice;
        device.applicationLayer.unconfirmedRequest(this,dnet,dadr,apdu);
    }

    public void cancel(Device device) {
        device.applicationLayer.removeClient(this);
    }

    public boolean clientCheckMatch(int snet, byte[] sadr, int serviceChoice) {
        return  (expectedSnet == -1 || snet == expectedSnet) &&
                (expectedSadr == null || expectedSadr.length == 0 || Arrays.equals(sadr,expectedSadr)) &&
                (expectedServiceChoice == -1 || expectedServiceChoice == serviceChoice);
    }

    public void clientIndication(Device device, int snet, byte[] sadr, int serviceChoice, ASNBuffer serviceData, AuthData auth) {}

}
