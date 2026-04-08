// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.bacnetsc;

import dev.bscs.bacnet.stack.constants.ErrorCode;
import dev.bscs.common.Formatting;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

/**
 * A data object that holds a Header Option, as defined in YY.2.3.
 * This includes methods to parse from, and generate to, byte buffers.
 * The only standard option at the moment is "Secure Path" set by messages originating in an SC node.
 * @author drobin
 */
public class SCOption {

    public int     marker; // original marker including the more flag is needed for the 'Error Header Marker' field in a NAK
    public int     type;   // for error injection testing, if type is -1, then 'data' forms the entire result of generate()
    public boolean mustUnderstand;
    public byte[]  data;

    public boolean parseError;
    public int     parseErrorCode;
    public String  parseErrorReason;

    public  static final int TYPE_SECURE_PATH   = 1;

    private static final int FLAG_MORE       = 0x80; // bit 7
    private static final int FLAG_UNDERSTAND = 0x40; // bit 6
    private static final int FLAG_DATA       = 0x20; // bit 5
    private static final int TYPE_MASK       = 0x1F; // bits 4..0

    public SCOption() { }

    public SCOption(int type, boolean mustUnderstand, byte[] data) { this(type,mustUnderstand); this.data = data; }

    public SCOption(int type, boolean mustUnderstand) {
        this.type = type; this.mustUnderstand = mustUnderstand;
    }

    public boolean parse(ByteBuffer buf)  {
        marker          = buf.get()&0xFF;
        boolean more    = (marker & FLAG_MORE) != 0;
        mustUnderstand  = (marker & FLAG_UNDERSTAND) != 0;
        type            = marker & TYPE_MASK;
        if ((marker & FLAG_DATA) != 0) {
            try {
                int length = buf.getShort()&0xFFFF;
                data = new byte[length];
                buf.get(data);
            }
            catch (BufferUnderflowException e) {
                // YY.3.1.5 "If a BVLC message is received that is truncated..."
                setParseError(ErrorCode.MESSAGE_INCOMPLETE,"Not enough data in option - length field wrong?");
            }
        }
        return more;
    }

    public void generate(ByteBuffer buf, boolean more) {
        if (type == -1) { buf.put(data); return; } // for error injection testing, if type is -1, then 'data' forms the entire result of generate()
        marker = type;
        if (mustUnderstand)   marker |= FLAG_UNDERSTAND;
        if (data != null)     marker |= FLAG_DATA;
        if (more)             marker |= FLAG_MORE;
        buf.put((byte)marker);
        if (data != null) {
            buf.putShort((short)data.length);
            buf.put(data);
        }
    }

    private void setParseError(int code, String reason) {
        parseError = true;
        parseErrorCode = code;
        parseErrorReason = reason;
    }

    public String toString() {
        // brief option format (<number><must-understand-as-Y-or-N><[hexdata]>)  e.g., secure path = (1Y)
        return "("+ (type==-1?"C":type) + (mustUnderstand?"Y":"N") + (data==null?"":"["+Formatting.toHex(data)+"]")  +")";
    }



}
