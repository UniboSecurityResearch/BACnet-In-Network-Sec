// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.bacnetsc;

import dev.bscs.common.Formatting;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * A data object that holds a payload of a Connect-Request message, as defined in YY.2.10.
 * This includes methods to parse from, and generate to, byte buffers.
 * @author drobin
 */
public class SCPayloadConnectRequest {

    public SCVMAC      vmac;
    public UUID        uuid ;
    public int         maxBVLC;
    public int         maxNPDU;

    private final int CONNECT_REQUEST_LENGTH = 26; // vmac=6;uuid=16;minBVLC=2;minNPDU=2

    public SCPayloadConnectRequest(SCVMAC vmac, UUID uuid, int maximumBVLCLength, int maximumNPDULength) {
        this.vmac    = vmac;
        this.uuid    = uuid;
        this.maxBVLC = maximumBVLCLength;
        this.maxNPDU = maximumNPDULength;
    }

    public SCPayloadConnectRequest(byte[] payload)  {
        parse(payload);
    }

    public void parse(byte[] payload) {
        parse(ByteBuffer.wrap(payload));
    }

    public void parse(ByteBuffer buf) {
        vmac    = new SCVMAC(buf);
        uuid    = Formatting.parseUUID(buf);
        maxBVLC = buf.getShort()&0xFFFF;
        maxNPDU = buf.getShort()&0xFFFF;
    }

    public byte[] generate() {
        byte[] payload = new byte[CONNECT_REQUEST_LENGTH];
        generate(ByteBuffer.wrap(payload));
        return payload;
    }

    public void generate(ByteBuffer buf) {
        vmac.generate(buf);
        Formatting.generateUUID(buf,uuid);
        buf.putShort((short) maxBVLC);
        buf.putShort((short) maxNPDU);
    }

    public String toString() {
        return  "(v="+vmac+
                " u="+uuid+
                " b="+ maxBVLC +
                " n="+ maxNPDU +
                ")";
    }

}
