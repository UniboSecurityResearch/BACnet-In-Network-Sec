// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.stack;

import dev.bscs.bacnet.stack.constants.ErrorCode;
import dev.bscs.common.Formatting;
import dev.bscs.common.Log;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

public class NPDU {

    private static final Log log = new Log(NPDU.class);

    public boolean isAPDU;
    public boolean der;
    public int     priority;
    public int     dnet; // ignored if 0
    public byte[]  dadr; // ignored if dnet==0
    public int     snet; // ignored if 0
    public byte[]  sadr; // ignored if snet==0
    public int     hopCount;
    public int     messageType;
    public int     vendorID;
    public byte[]  payload;
    public boolean parseError;
    public int     parseErrorCode;
    public String  parseErrorReason;

    public NPDU() {  }

    public NPDU(byte[] data) { parse(ByteBuffer.wrap(data)); }

    public NPDU(int dnet, byte[] dadr, APDU apdu, boolean der) {
        this.isAPDU   = true;
        this.dnet     = dnet;
        this.dadr     = dadr;
        this.der      = der;
        this.hopCount = 255;
        this.priority = 0;
        this.payload  = apdu.generate();
    }

    public NPDU(int dnet, byte[] dadr) {
        this.isAPDU   = false;
        this.dnet     = dnet;
        this.dadr     = dadr;
        this.hopCount = 255;
        this.priority = 0;
    }

    public static NPDU makeResponse(NPDU request, int targetDatalinkNetworkNumber) {
        NPDU response     = new NPDU();
        response.isAPDU   = request.isAPDU;
        if (request.snet != 0 && request.snet != targetDatalinkNetworkNumber) {
            response.dnet     = request.snet;
            response.dadr     = request.sadr;
        }
        response.hopCount = 255;
        response.priority = request.priority;
        return response;
    }

    public byte[] generate() {
        ByteBuffer buf = ByteBuffer.allocate(payload==null? 24 : payload.length+24); // 24 is worst case header
        generate(buf);
        int actualSize = buf.position();
        byte[] result = new byte[actualSize];
        System.arraycopy(buf.array(),0,result,0,actualSize);
        return result;
    }

    public void generate(ByteBuffer buf) {
        buf.put((byte)1);
        byte control = 0;
        if (!isAPDU) control |= 0b10000000;
        if (dnet!=0) control |= 0b00100000;
        if (snet!=0) control |= 0b00001000;
        if (der)   control |= 0b00000100;
        control |= (byte)priority;
        buf.put(control);
        if (dnet!=0) {
            buf.putShort((short)dnet);
            buf.put(dadr==null? (byte)0 : (byte)dadr.length);
            if (dadr!=null) buf.put(dadr);
        }
        if (snet!=0) {
            buf.putShort((short)snet);
            buf.put(sadr==null? (byte)0 : (byte)sadr.length);
            if (sadr!=null) buf.put(sadr);
        }
        if (dnet!=0) buf.put((byte)hopCount);
        if (isAPDU) {
            if (payload != null) buf.put(payload);
        }
        else {
            buf.put((byte)messageType);
            if (messageType > 127) buf.putShort((short)vendorID);
            if (payload != null) buf.put(payload);
        }
    }

    private void parse(ByteBuffer buf) {
        try {
            buf.get();      // skip version  (there is no error checking in here!)
            int control = buf.get() & 0xFF;
            isAPDU = (control & 0b10000000) == 0;
            boolean hasDNET = (control & 0b00100000) != 0;
            boolean hasSNET = (control & 0b00001000) != 0;
            der = (control & 0b00000100) != 0;
            priority = control & 0b00000011;
            if (hasDNET) {
                dnet = buf.getShort() & 0xFFFF;
                int dlen = buf.get() & 0xFF;
                if (dlen != 0) {
                    dadr = new byte[dlen];
                    buf.get(dadr);
                }
            }
            if (hasSNET) {
                snet = buf.getShort() & 0xFFFF;
                int slen = buf.get() & 0xFF;
                if (slen != 0) {
                    sadr = new byte[slen];
                    buf.get(sadr);
                }
            }
            if (hasDNET) hopCount = buf.get() & 0xFF;
            if (isAPDU) {
                int length = buf.remaining(); // there better be some!
                if (length > 0) {
                    payload = new byte[length];
                    buf.get(payload);
                }
            } else {
                messageType = buf.get() & 0xFF;
                if (messageType > 127) vendorID = buf.getShort() & 0xFFFF;
                int length = buf.remaining();
                if (length > 0) {
                    payload = new byte[length];
                    buf.get(payload);
                }
            }
        } catch (BufferUnderflowException e) {
            //log.error("NPDU ran out of octets at pos="+buf.position()+" in [",Formatting.toHex(buf.array()) + "]" );
            parseError = true;
            parseErrorCode = ErrorCode.MESSAGE_INCOMPLETE;
            parseErrorReason = "NPDU parsing ran out of octets at pos="+buf.position();
        }
    }

    public String toString() {
        StringBuilder  result = new StringBuilder();
        if (parseError) result.append("ParseError:"+parseErrorReason+" - ");
        result.append("apdu=" + isAPDU + " der=" + der + " pri=" + priority);
        result.append(" dnet=" + dnet + " dadr=" + Formatting.toMac(dadr));
        result.append(" snet=" + snet + " sadr=" + Formatting.toMac(sadr));
        result.append(" hop="  + hopCount);
        if (!isAPDU) result.append(" type=" + messageTypeToString(messageType));
        if (messageType>127) result.append(" vnd="+ vendorID);
        result.append(" data=");
        if (payload != null) {
            result.append("{");
            if (isAPDU) result.append(new APDU(payload).toString());
            else result.append(networkMessagePayloadToString(messageType,payload));
            result.append("}");
        }
        else result.append("(none)");
        return result.toString();
    }

    public String messageTypeToString(int messageType)  {
        switch (messageType) {
            case 0x00: return "Who-Is-Router";
            case 0x01: return "I-Am-Router";
            case 0x02: return "I-Could-Be-Router";
            case 0x03: return "Reject-Message";
            case 0x04: return "Router-Busy";
            case 0x05: return "Router-Avail";
            case 0x06: return "Init-Rtg-Tbl";
            case 0x07: return "Init-Rtg-Tbl-Ack";
            case 0x08: return "Establish";
            case 0x09: return "Disconnect";
            case 0x0A: return "Clause24-CR";
            case 0x0B: return "Clause24-SP";
            case 0x0C: return "Clause24-SR";
            case 0x0D: return "Clause24-RKU";
            case 0x0E: return "Clause24-UKS";
            case 0x0F: return "Clause24-UDK";
            case 0x10: return "Clause24-RMK";
            case 0x11: return "Clause24-SMK";
            case 0x12: return "What-Is-Net";
            case 0x13: return "Net-Is";
            default:   return "???";
        }
    }

    public String networkMessagePayloadToString(int messageType, byte [] payload) {
        ByteBuffer buf = ByteBuffer.wrap(payload);
        StringBuilder result = new StringBuilder();
        switch (messageType)  {
            case 0x00:  // Who-Is-Router
            case 0x04:  // Router-Busy
            case 0x05:  // Router-Available
            case 0x08:  // Establish
            case 0x09:  // Disconnect
                int network = buf.getShort()&0xFFFF;
                result.append("net="+network);
                break;
            case 0x03:  // Reject-Message
                int reason  = buf.get()&0xFF;
                network     = buf.getShort()&0xFFFF;
                result.append("reason="+reason+" net="+network);
                break;
            case 0x01: // I-Am-Router
                result.append("nets=[");
                while (buf.remaining()>0) { result.append((buf.getShort()&0xFFFF)); if (buf.remaining()>0) result.append(","); }
                result.append("]");
                break;
            case 0x02: // I-Could-Be-Router
                result.append("net=");
                result.append(buf.getShort()&0xFFFF);
                result.append(" perf=");
                result.append(buf.get()&0xFF);
                break;
            case 0x06: // IRT
            case 0x07: // IRA
            case 0x0A: // CR
            case 0x0B: // SP
            case 0x0C: // SR
            case 0x0D: // RKU
            case 0x0E: // UKS
            case 0x0F: // UDK
            case 0x10: // RMK
            case 0x11: // SMK
                result.append(Formatting.toHex(payload)); //
                break;
            case 0x12: // What-Is-Net
                break;
            case 0x13: // Net-Num-Is
                network = buf.getShort()&0xFFFF;
                result.append("net="+network);
                int flag = buf.get()&0xFF;
                result.append(" flg="+flag);
                break;
            default:   return "???";

        }
        return result.toString();
    }
}
