// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.bacnetsc;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * A data object that holds a payload of a Connect-Accept message, as defined in YY.2.11.
 * This includes methods to parse from, and generate to, byte buffers.
 * @author drobin
 */
public class SCPayloadConnectAccept {

    public UUID       uuid;
    public SCVMAC     vmac;
    public int        maximumBVLCLength;   // Maximum BVLC Length 2-octet The maximum BVLC message size that can be received and processed by the node, in number ofoctets.
    public int        maximumNPDULength;   // Maximum NPDU Length 2-octets The maximum NPDU message size that can be handled

    private final int CONNECT_ACCEPT_LENGTH = 26; // vmac=6;uuid=16;minBVLC=2;minNPDU=2

    public SCPayloadConnectAccept(SCVMAC vmac, UUID uuid, int maximumBVLCLength, int maximumNPDULength) {
        this.vmac                = vmac;
        this.uuid                = uuid;
        this.maximumBVLCLength   = maximumBVLCLength;
        this.maximumNPDULength   = maximumNPDULength;
    }

    public SCPayloadConnectAccept(byte[] payload) {
        parse(payload);
    }

    public void parse(byte[] payload) {
        parse(ByteBuffer.wrap(payload));
    }

    public void parse(ByteBuffer buf) {
        vmac              = new SCVMAC(buf);
        uuid              = parseUUID(buf);
        maximumBVLCLength = buf.getShort()&0xFFFF;
        maximumNPDULength = buf.getShort()&0xFFFF;
    }

    private UUID parseUUID(ByteBuffer buf) {
        long msb = buf.getLong();
        long lsb = buf.getLong();
        UUID uuid = new UUID(msb,lsb);
        return uuid;
    }
    private void generateUUID(ByteBuffer buf, UUID uuid) {
        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();
        buf.putLong(msb);
        buf.putLong(lsb);
    }

    public byte[] generate() {
        byte[] payload = new byte[CONNECT_ACCEPT_LENGTH];
        generate(ByteBuffer.wrap(payload));
        return payload;
    }

    public void generate(ByteBuffer buf) {
        vmac.generate(buf);
        generateUUID(buf,uuid);
        buf.putShort((short)maximumBVLCLength);
        buf.putShort((short)maximumNPDULength);
    }

    public String toString() {
        return  "(v="+vmac+
                " u="+uuid+
                " b="+maximumBVLCLength+
                " n="+maximumNPDULength+
                ")";
    }

}
