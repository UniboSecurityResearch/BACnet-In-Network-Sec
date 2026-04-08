// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.bacnetsc;

import dev.bscs.common.Formatting;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * A data object that holds a payload of a BVLC-Result message, as defined in YY.2.4.
 * This includes methods to parse from, and generate to, byte buffers.
 * @author drobin
 */
public class SCPayloadBVLCResult {
    public int    forFunction;
    public int    resultCode;
    public int    errorHeaderMarker;
    public int    errorClass;
    public int    errorCode;
    public String errorDetails;

    private final int FIXED_PORTION_LENGTH = 7; // forFunc=1;resCode=1;HdrMarker=1;Class=2;Code=2

    public SCPayloadBVLCResult(int forFunction) {
        this.forFunction = forFunction;
        this.resultCode = 0; //ack   // NEVER ACTUALLY USED!
    }
    public SCPayloadBVLCResult(int forFunction, int errorHeaderMarker, int errorClass, int errorCode, String errorDetails) {
        this.forFunction         = forFunction;
        this.resultCode          = 1; // nak
        this.errorHeaderMarker   = errorHeaderMarker;
        this.errorClass          = errorClass;
        this.errorCode           = errorCode;
        this.errorDetails        = errorDetails;
    }

    public boolean isAck() { return resultCode == 0; }

    public boolean isNak() { return resultCode != 0; }

    public SCPayloadBVLCResult(byte[] payload) {
        parse(payload);
    }

    public void parse(byte[] payload) {
        parse(ByteBuffer.wrap(payload));
    }

    public void parse(ByteBuffer buf) {
        forFunction       = buf.get()&0xFF;
        resultCode        = buf.get()&0xFF;
        if (resultCode == 1) { // nak
            errorHeaderMarker = buf.get()&0xFF;
            errorClass        = buf.getShort()&0xFFFF;
            errorCode         = buf.getShort()&0xFFFF;
            if (buf.remaining()>0) {
                int length = buf.remaining(); // size of the string octets
                byte[] bytes = new byte[length];
                buf.get(bytes);
                errorDetails = new String(bytes,StandardCharsets.UTF_8);
            }
        }
    }

    public byte[] generate() {
        int errorDetailsLength = errorDetails!=null? errorDetails.getBytes(StandardCharsets.UTF_8).length : 0;
        byte[] payload = new byte[ FIXED_PORTION_LENGTH + errorDetailsLength ];
        generate(ByteBuffer.wrap(payload));
        return payload;
    }

    public void generate(ByteBuffer buf) {
        buf.put((byte)forFunction);
        buf.put((byte)resultCode);
        if (resultCode == (byte)1) { // nak
            buf.put((byte)errorHeaderMarker);
            buf.putShort((short)errorClass);
            buf.putShort((short)errorCode);
            if (errorDetails!=null && !errorDetails.isEmpty()) {
                buf.put(errorDetails.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    public String toString() {
        if (resultCode != 0) {
            return "(f=" + forFunction +
                    " r=" + resultCode +
                    " m=" + Formatting.toHex((byte)errorHeaderMarker) +
                    " e=" + errorClass +
                    " c=" + errorCode +
                    " d=" + errorDetails +
                    ")";
        }
        else {
            return "(f=" + forFunction +
                    " r=" + resultCode +
                    ")";
        }
    }


}
