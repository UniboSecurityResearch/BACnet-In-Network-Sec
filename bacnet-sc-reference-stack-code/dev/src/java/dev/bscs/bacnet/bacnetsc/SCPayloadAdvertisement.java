// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.bacnetsc;

import java.nio.ByteBuffer;

/**
 * A data object that holds a payload of a Advertisement message, as defined in YY.2.8.
 * This includes methods to parse from, and generate to, byte buffers.
 * @author drobin
 */
public class SCPayloadAdvertisement {

    public int             connStatus;
    public boolean         acceptConnections;   // Accept Connections 1-octet X'00' The node does not accept WebSocket connections, X'01' The node accepts WebSocket connections.
    public int             maximumBVLCLength;   // Maximum BVLC Length 2-octet The maximum BVLC message size that can be received and processed by the node, in number ofoctets.
    public int             maximumNPDULength;   // Maximum NPDU Length 2-octets The maximum NPDU message size that can be handled

    public static final int CONN_STAT_NONE     = 0;
    public static final int CONN_STAT_PRIMARY  = 1;
    public static final int CONN_STAT_FAILOVER = 2;

    private final int ADVERTISEMENT_LENGTH = 6; // connStat=1;Accept=1;minBVLC=2;minNPDU=2

    public SCPayloadAdvertisement(int connStatus, boolean acceptConnections, int maximumBVLCLength, int maximumNPDULength) {
        this.connStatus         = connStatus;
        this.acceptConnections  = acceptConnections;
        this.maximumBVLCLength  = maximumBVLCLength;
        this.maximumNPDULength  = maximumNPDULength;
    }

    public static SCPayloadAdvertisement forUnknownDevice() {
        return new SCPayloadAdvertisement(
                CONN_STAT_NONE,
                false,
                1497+SCMessage.MESSAGE_HEADER_MAX_LENGTH,  // assume size for full header + no data options + required data options
                1497                                       // assume size for BACnet/IP
        );
    }

    public SCPayloadAdvertisement(byte[] payload) {
        parse(payload);
    }

    public void parse(byte[] payload) {
        parse(ByteBuffer.wrap(payload));
    }

    public void parse(ByteBuffer buf) {
        connStatus        = buf.get()&0xFF;
        acceptConnections = buf.get()!=0;
        maximumBVLCLength = buf.getShort()&0xFFFF;
        maximumNPDULength = buf.getShort()&0xFFFF;
    }

    public byte[] generate() {
        byte[] payload = new byte[ADVERTISEMENT_LENGTH];
        generate(ByteBuffer.wrap(payload));
        return payload;
    }

    public void generate(ByteBuffer buf) {
        buf.put((byte)connStatus);
        buf.put(acceptConnections?(byte)1:(byte)0);
        buf.putShort((short)maximumBVLCLength);
        buf.putShort((short)maximumNPDULength);
    }

    public String toString() {
        return  "(c="+connStatus+
                " a="+acceptConnections+
                " b="+maximumBVLCLength+
                " n="+maximumNPDULength+
                ")";
    }

}
