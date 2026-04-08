// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.bacnetsc;

import dev.bscs.bacnet.stack.NPDU;
import dev.bscs.bacnet.stack.constants.ErrorCode;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A holder for the data in a Secure Connect message, as defined in YY.2.1.
 * This includes methods to construct messages, add options, and to parse from, and generate to, byte buffers.
 * @author drobin
 */
public class SCMessage {

    private static final SCLog log = new SCLog(SCHubFunction.class);

    public int            function = -1;       // BVLC Function               1-octet BVLC function
    public int            control;             // Control Flags               1-octet Control flags
    public int            id;                  // Message ID                  2-octets The message identifier
    public SCVMAC         originating = null;  // Originating Virtual Address Optional, 6-octets Source node VMAC address
    public SCVMAC         destination = null;  // Destination Virtual Address Optional, 6-octets Destination VMAC address
    public List<SCOption> destOptions = null;  // Destination Options         Optional, N-octets options
    public List<SCOption> dataOptions = null;  // Data Options                Optional, N-octets options
    public byte[]         payload     = null;  // Payload                     Variable, The payload of the BVLC message
    public int            number;

    public boolean        parseError;
    public int            parseErrorCode;
    public String         parseErrorReason;

    public static final int MESSAGE_HEADER_MIN_LENGTH = 4;            // function + control + id
    public static final int MESSAGE_HEADER_MAX_LENGTH = 4+6+6+0+4192; // function + control + id +orig + dest + no dest opts + 4192 of data opts

    public static final int BVLC_RESULT                 = 0;
    public static final int ENCAPSULATED_NPDU           = 1;
    public static final int ADDRESS_RESOLUTION          = 2;
    public static final int ADDRESS_RESOLUTION_ACK      = 3;
    public static final int ADVERTISEMENT               = 4;
    public static final int ADVERTISEMENT_SOLICITATION  = 5;
    public static final int CONNECT_REQUEST             = 6;
    public static final int CONNECT_ACCEPT              = 7;
    public static final int DISCONNECT_REQUEST          = 8;
    public static final int DISCONNECT_ACK              = 9;
    public static final int HEARTBEAT_REQUEST           = 10;
    public static final int HEARTBEAT_ACK               = 11;
    public static final int PROPRIETARY_MESSAGE         = 12;
    public static final int MAX_FUNCTION                = 12;

    private static final int FLAG_ORIG_ADDR = 0x08;
    private static final int FLAG_DEST_ADDR = 0x04;
    private static final int FLAG_DEST_OPTS = 0x02;
    private static final int FLAG_DATA_OPTS = 0x01;
    private static final int FLAG_MASK      = 0x0F;

    private static short nextID;     // all connections in this VM share this message ID, but that's OK, it doesn't need to be per-connection
    private static short nextNumber; // all messages get a unique number so we can track their flow through internal routing/switching

    public SCMessage(SCVMAC originating, SCVMAC destination, int function, byte[] payload, int id) {
        this.originating = originating;
        this.destination = destination;
        this.function    = function;
        this.payload     = payload;
        this.id          = id;
        this.number      = nextNumber++;
    }

    public SCMessage(SCVMAC originating, SCVMAC destination, int function, byte[] payload) {
        this.originating = originating;
        this.destination = destination;
        this.function    = function;
        this.payload     = payload;
        this.id          = nextID++ & 0xFFFF;
        this.number      = nextNumber++;

    }
    public SCMessage(SCVMAC originating, SCVMAC destination, int function, int id) {
        this.originating = originating;
        this.destination = destination;
        this.function    = function;
        this.id          = id;
        this.number      = nextNumber++;
        // leave payload null
    }
    public SCMessage(SCVMAC originating, SCVMAC destination, int function) {
        this.originating = originating;
        this.destination = destination;
        this.function    = function;
        this.id          = nextID++ & 0xFFFF;
        this.number      = nextNumber++;
        // leave payload null
    }

    public SCMessage(ByteBuffer bytes)  {
        parse(bytes);
        number = nextNumber++;
    }

    public void parse(ByteBuffer buf)  {
        // This will check for YY.3.1.5 Common Error Situations, including cases for "If a BVLC message is received..."
        // "...is truncated" - checked
        // "...a header has encoding errors" - not much to check for, actually
        // "...any control flag has an unexpected value" - checked
        // "...any parameter, field of a known header, or parameter in a BACnet/SC defined payload, is out of range"
        // "...any data inconsistency exists in any" - checked by users of this class, payload not checked here
        try {
            function    = buf.get()&0xFF;
            control     = buf.get()&0xFF;
            id          = buf.getShort()&0xFFFF;
            originating = ((control & FLAG_ORIG_ADDR) == 0)? null : new SCVMAC(buf);
            destination = ((control & FLAG_DEST_ADDR) == 0)? null : new SCVMAC(buf);
            destOptions = ((control & FLAG_DEST_OPTS) == 0)? null : parseOptions(buf); // can set parseErrorXxxx
            dataOptions = ((control & FLAG_DATA_OPTS) == 0)? null : parseOptions(buf); // can set parseErrorXxxx
            if (buf.hasRemaining()) {
                payload = new byte[buf.remaining()];
                buf.get(payload);
            }
            else payload = null;
            // check for errors and inconsistency
            if ((control & ~FLAG_MASK) != 0) setParseError(ErrorCode.INCONSISTENT_PARAMETERS,"reserved control bits are not 0");
            if (function > MAX_FUNCTION)     setParseError(ErrorCode.BVLC_FUNCTION_UNKNOWN,"Function Code unknown");
            // This only checked for general "parsing errors".  It did not check for address/payload inconsistencies or
            // unsupported things.  Because this class does not know the context in which the messages are used, the users
            // of SCMessage, like SCConnection, check for a lot more error conditions.
            // ALSO... checking options for "must understand" is also done by higher level users. This class doesn't want
            // to assume too much.
        }
        catch (BufferUnderflowException e) {
            // YY.3.1.5 "If a BVLC message is received that is truncated..."
            parseError = true;
            parseErrorCode = ErrorCode.MESSAGE_INCOMPLETE;
            parseErrorReason = "Not enough data in message - length wrong?";
        }
    }

    private void setParseError(int code, String reason) {
        parseError = true;
        parseErrorCode = code;
        parseErrorReason = reason;
    }

    public void clearDataOptions() {
        dataOptions = null;
        updateControlFlags();
    }
    public void addDataOption(SCOption option) {
        if (dataOptions == null) dataOptions = new ArrayList<>();
        dataOptions.add(option);
        updateControlFlags();
    }
    public void addDataOptions(List<SCOption> options) {
        if (dataOptions == null) dataOptions = new ArrayList<>();
        dataOptions.addAll(options);
        updateControlFlags();
    }
    public void clearDestOptions() {
        destOptions = null;
        updateControlFlags();
    }
    public void addDestOption(SCOption option) {
        if (destOptions == null) destOptions = new ArrayList<>();
        destOptions.add(option);
        updateControlFlags();
    }
    public void addDestOptions(List<SCOption> options) {
        if (destOptions == null) destOptions = new ArrayList<>();
        destOptions.addAll(options);
        updateControlFlags();
    }
    public void setDestination(SCVMAC vmac) {
        destination = vmac;
        updateControlFlags();
    }
    public void setOriginating(SCVMAC vmac) {
        originating = vmac;
        updateControlFlags();
    }
    private void updateControlFlags() {
        control = 0;
        if (originating != null) { control |= FLAG_ORIG_ADDR; }
        if (destination != null) { control |= FLAG_DEST_ADDR; }
        if (destOptions != null && !destOptions.isEmpty()) { control |= FLAG_DEST_OPTS; }
        if (dataOptions != null && !dataOptions.isEmpty()) { control |= FLAG_DATA_OPTS; }
    }

    public byte[] generate() {
        updateControlFlags();
        int totalLength = MESSAGE_HEADER_MIN_LENGTH;
        if (originating != null) { totalLength += 6;  }
        if (destination != null) { totalLength += 6;  }
        if (destOptions != null) { totalLength += lengthOfOptions(destOptions); }
        if (dataOptions != null) { totalLength += lengthOfOptions(dataOptions); }
        if (payload     != null)   totalLength += payload.length;
        ByteBuffer buf = ByteBuffer.allocate(totalLength);
        buf.put((byte)function);
        buf.put((byte)control);
        buf.putShort((short)id);
        if (originating != null) originating.generate(buf);
        if (destination != null) destination.generate(buf);
        if (destOptions != null) generateOptions(destOptions, buf);
        if (dataOptions != null) generateOptions(dataOptions, buf);
        if (payload     != null) buf.put(payload);
        return buf.array();
    }

    public boolean isUnicast() {
        return destination == null || !destination.isBroadcast();
    }

    public boolean isBroadcast() {
        return destination != null && destination.isBroadcast();
    }

    public boolean isUnicastRequest() {
        return  isUnicast() && (
                function == CONNECT_REQUEST ||
                function == DISCONNECT_REQUEST ||
                function == ENCAPSULATED_NPDU ||
                function == ADDRESS_RESOLUTION ||
                function == ADVERTISEMENT_SOLICITATION  ||
                function == HEARTBEAT_REQUEST ||
                function > MAX_FUNCTION);
    }

    /////////////////////////////////////////////


    private List<SCOption> parseOptions(ByteBuffer buf)  {
        List<SCOption> result = new ArrayList<>();
        boolean more = true;
        while (more) {
            SCOption option = new SCOption();
            more = option.parse(buf);
            if (option.parseError) {
                parseError = true;
                parseErrorCode = option.parseErrorCode;
                parseErrorReason = option.parseErrorReason;
            }
            else result.add(option);
        }
        return result;
    }

    private void  generateOptions(List<SCOption> options, ByteBuffer buf) {
        Iterator<SCOption> iter = options.iterator(); // have to use iterator not for..each because we need to know then last one
        while (iter.hasNext()) {
            SCOption option = iter.next();
            option.generate(buf,iter.hasNext()); // indicate the last one
        }
    }

    private int  lengthOfOptions(List<SCOption> options) {
        int length = 0;
        for (SCOption option : options) {
            length += 1;  // marker
            if (option.data != null) length += 2 + option.data.length; // length + data
        }
        return length;
    }

    public String toString() {
        if (function == -1) return "(unparsed)";
        String result =
                "{#"+number+
                " f="+ functionToString(function)+
                " d="+ (destination==null?"none":destination.toString())+
                " o="+ (originating==null?"none":originating.toString())+
                " i="+ id+
                " c="+ ((control&FLAG_ORIG_ADDR)!=0?  "O":"-")+
                       ((control&FLAG_DEST_ADDR)!=0?  "D":"-")+
                       ((control&FLAG_DEST_OPTS)!=0?  "L":"-")+
                       ((control&FLAG_DATA_OPTS)!=0?  "N":"-")+
                (destOptions==null?"":" destopt="+destOptions)+
                (dataOptions==null?"":" dataopt="+dataOptions)+
                " p={";
        switch (function) {
            case SCMessage.BVLC_RESULT:
                if (payload == null) result += "error: EMPTY PAYLOAD!";
                else result += new SCPayloadBVLCResult(payload).toString();
                break;
            case SCMessage.ENCAPSULATED_NPDU:
                if (payload == null) result += "error: EMPTY PAYLOAD!";
                else try {
                    NPDU npdu = new NPDU(payload);
                    result += "NPDU("+npdu.toString()+")";
                } catch (Throwable e) { result+=e.getLocalizedMessage(); e.printStackTrace();}
                break;
            case SCMessage.ADDRESS_RESOLUTION_ACK:
                if (payload == null) result += "error: EMPTY PAYLOAD!";
                else result += new SCPayloadAddressResolutionAck(payload).toString();
                break;
            case SCMessage.ADVERTISEMENT:
                if (payload == null) result += "error: EMPTY PAYLOAD!";
                else result += new SCPayloadAdvertisement(payload).toString();
                break;
            case SCMessage.CONNECT_REQUEST:
                if (payload == null) result += "error: EMPTY PAYLOAD!";
                else result += new SCPayloadConnectRequest(payload).toString();
                break;
            case SCMessage.CONNECT_ACCEPT:
                if (payload == null) result += "error: EMPTY PAYLOAD!";
                else result += new SCPayloadConnectAccept(payload).toString();
                break;
            case SCMessage.ADDRESS_RESOLUTION:
            case SCMessage.ADVERTISEMENT_SOLICITATION:
            case SCMessage.DISCONNECT_REQUEST:
            case SCMessage.DISCONNECT_ACK:
            case SCMessage.HEARTBEAT_REQUEST:
            case SCMessage.HEARTBEAT_ACK:
                if (payload == null) result += "()";
                else  result += "error: NON-EMPTY PAYLOAD!";
                break;
            default:
                result += "error: INVALID FUNCTION";
                break;
        }
        return result+"}";
    }

    public static String functionToString(int function) {
        switch (function) {
            case SCMessage.BVLC_RESULT:                return "BR";
            case SCMessage.ENCAPSULATED_NPDU:          return "EN";
            case SCMessage.ADDRESS_RESOLUTION:         return "AR";
            case SCMessage.ADDRESS_RESOLUTION_ACK:     return "AA";
            case SCMessage.ADVERTISEMENT:              return "AD";
            case SCMessage.ADVERTISEMENT_SOLICITATION: return "AS";
            case SCMessage.CONNECT_REQUEST:            return "CR";
            case SCMessage.CONNECT_ACCEPT:             return "CA";
            case SCMessage.DISCONNECT_REQUEST:         return "DR";
            case SCMessage.DISCONNECT_ACK:             return "DA";
            case SCMessage.HEARTBEAT_REQUEST:          return "HR";
            case SCMessage.HEARTBEAT_ACK:              return "HA";
            case SCMessage.PROPRIETARY_MESSAGE:        return "PM";
            default: return "?"+function+"?";
        }
    }

}

