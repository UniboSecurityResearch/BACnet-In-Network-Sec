// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.stack;

import dev.bscs.bacnet.stack.constants.RejectReason;
import dev.bscs.common.Application;
import dev.bscs.common.Formatting;
import dev.bscs.common.Log;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

public class APDU {

    private static final Log log = new Log(APDU.class);

    public static final int CONF_REQ   = 0;
    public static final int UNCONF_REQ = 1;
    public static final int SIMPLE_ACK = 2;
    public static final int CONF_ACK   = 3;
    public static final int SEG_ACK    = 4;
    public static final int ERROR      = 5;
    public static final int REJECT     = 6;
    public static final int ABORT      = 7;

    public int        pduType = -1; // 0 is valid so this will indicate that it is unknown
    public boolean    seg;
    public boolean    mor;
    public boolean    sa;
    public int        maxSegs;
    public int        maxResp;
    public int        inv;
    public int        seq;
    public boolean    nak;
    public boolean    srv;
    public int        aws;
    public int        pws;
    public int        serviceChoice;
    public ASNBuffer  serviceRequest;
    public int        reason;
    public Failure    failure;

    public APDU() {  }

    public APDU(byte[] data) { parse(ByteBuffer.wrap(data)); }

    public APDU(int pduType, int serviceChoice, int invoke, ASNBuffer serviceRequest ) {  // for confirmed
        this.pduType        = pduType;
        this.serviceChoice  = serviceChoice;
        this.serviceRequest = serviceRequest;
        this.inv            = invoke;
    }

    public APDU(int pduType, int serviceChoice, ASNBuffer serviceRequest) { // for unconfirmed
        this.pduType        = pduType;
        this.serviceChoice  = serviceChoice;
        this.serviceRequest = serviceRequest;
    }

    public APDU(int pduType, int invoke, int reason ) { // for REJECT
        this.pduType        = pduType;
        this.inv            = invoke;
        this.reason         = reason;
    }

    public APDU(int pduType, int invoke, int reason, boolean server) { // for ABORT
        this.pduType        = pduType;
        this.inv            = invoke;
        this.reason         = reason;
        this.srv            = server;
    }

    public byte[] generate() {
        ByteBuffer buf = ByteBuffer.allocate(serviceRequest==null? 6 : serviceRequest.limit()+6); //  6 is worst case header
        generate(buf);
        int actualSize = buf.position();
        byte[] result = new byte[actualSize];
        System.arraycopy(buf.array(),0,result,0,actualSize);
        return result;
    }

    public void generate(ByteBuffer buf) {
        switch (pduType) {
            case CONF_REQ:
                //  |---|---|---|---|---|---|---|---|
                //  |    PDU Type   |SEG|MOR| SA| 0 |
                //  |---|---|---|---|---|---|---|---|
                //  | 0 |  MaxSegs  |    MaxResp    |
                //  |---|---|---|---|---|---|---|---|
                //  |           Invoke ID           |
                //  |---|---|---|---|---|---|---|---|
                //  |        Sequence Number        | Only present if SEG = 1
                //  |---|---|---|---|---|---|---|---|
                //  |      Proposed Window Size     | Only present if SEG = 1
                //  |---|---|---|---|---|---|---|---|
                //  |        Service Choice         |
                //  |---|---|---|---|---|---|---|---|
                //  |       Service Request...      |
                buf.put((byte)(pduType<<4));
                buf.put((byte)0b00000101);  // 0 segs, 1497 resp
                buf.put((byte)inv);
                buf.put((byte)serviceChoice);
                buf.put(serviceRequest.getBytes());
                break;
            case UNCONF_REQ:
                //  |---|---|---|---|---|---|---|---|
                //  |    PDU Type   | 0 | 0 | 0 | 0 |
                //  |---|---|---|---|---|---|---|---|
                //  |        Service Choice         |
                //  |---|---|---|---|---|---|---|---|
                //  |       Service Request...      |
                buf.put((byte)(pduType<<4));
                buf.put((byte)serviceChoice);
                buf.put(serviceRequest.getBytes());
                break;
            case SIMPLE_ACK:
                //  |---|---|---|---|---|---|---|---|
                //  |    PDU Type   | 0 | 0 | 0 | 0 |
                //  |---|---|---|---|---|---|---|---|
                //  |      Original Invoke ID       |
                //  |---|---|---|---|---|---|---|---|
                //  |       Service Ack Choice      |
                //  |---|---|---|---|---|---|---|---|
                buf.put((byte)(pduType<<4));
                buf.put((byte)inv);
                buf.put((byte)serviceChoice);
                break;
            case CONF_ACK:
                //  |---|---|---|---|---|---|---|---|
                //  |    PDU Type   |SEG|MOR| 0 | 0 |
                //  |---|---|---|---|---|---|---|---|
                //  |     Original Invoke ID        |
                //  |---|---|---|---|---|---|---|---|
                //  |        Sequence Number        | Only present if SEG = 1
                //  |---|---|---|---|---|---|---|---|
                //  |      Proposed Window Size     | Only present if SEG = 1
                //  |---|---|---|---|---|---|---|---|
                //  |     Service ACK Choice        |
                //  |---|---|---|---|---|---|---|---|
                //  |         Service ACK...        |
                buf.put((byte)(pduType<<4));
                buf.put((byte)inv);
                buf.put((byte)serviceChoice);
                buf.put(serviceRequest.getBytes());
                break;
            case SEG_ACK:
                // TODO not supported
                throw new RuntimeException("IMPLEMENTATION ERROR: APDU: Sending SEG_ACK not supported");
            case ERROR:
                //  |---|---|---|---|---|---|---|---|
                //  |    PDU Type   | 0 | 0 | 0 | 0 |
                //  |---|---|---|---|---|---|---|---|
                //  |     Original Invoke ID        |
                //  |---|---|---|---|---|---|---|---|
                //  |         Error Choice          |  // service choice
                //  |---|---|---|---|---|---|---|---|
                //  |         Error ...             |
                buf.put((byte)(pduType<<4));
                buf.put((byte)inv);
                buf.put((byte)serviceChoice);
                buf.put(serviceRequest.getBytes());
                break;
            case REJECT:
                //  |---|---|---|---|---|---|---|---|
                //  |    PDU Type   | 0 | 0 | 0 | 0 |
                //  |---|---|---|---|---|---|---|---|
                //  |     Original Invoke ID        |
                //  |---|---|---|---|---|---|---|---|
                //  |         Reject Reason         |
                //  |---|---|---|---|---|---|---|---|
                buf.put((byte)(pduType<<4));
                buf.put((byte)inv);
                buf.put((byte)reason);
                break;
            case ABORT:
                //  |---|---|---|---|---|---|---|---|
                //  |    PDU Type   | 0 | 0 | 0 |SRV|
                //  |---|---|---|---|---|---|---|---|
                //  |     Original Invoke ID        |
                //  |---|---|---|---|---|---|---|---|
                //  |          Abort Reason         |
                //  |---|---|---|---|---|---|---|---|
                buf.put((byte)((pduType<<4)|(srv?1:0)));
                buf.put((byte)inv);
                buf.put((byte)reason);
                break;
        }
    }

    private void  parse(ByteBuffer buf) {
        try {
            int head1 = buf.get()&0xFF;
            pduType   = (head1 & 0b11110000) >> 4;
            switch (pduType) {
                case CONF_REQ:
                    seg = (head1 & 0b00001000) != 0;
                    mor = (head1 & 0b00000100) != 0;
                    sa = (head1 & 0b00000010) != 0;
                    int head2 = buf.get()&0xFF;
                    maxSegs = (head2 & 0b01110000) >> 4;
                    maxResp = (head2 & 0b00001111);
                    inv = buf.get()&0xFF;
                    if (seg) {
                        seq = buf.get()&0xFF;
                        pws = buf.get()&0xFF;
                    }
                    serviceChoice = buf.get()&0xFF;
                    serviceRequest = new ASNBuffer(buf);     // remainder of the buffer is the service request
                    break;
                case UNCONF_REQ:
                    serviceChoice = buf.get()&0xFF;
                    serviceRequest = new ASNBuffer(buf);     // remainder of the buffer is the service request
                    break;
                case SIMPLE_ACK:
                    inv = buf.get()&0xFF;
                    serviceChoice = buf.get()&0xFF;
                    break;
                case CONF_ACK:
                    seg = (head1 & 0b00001000) != 0;
                    mor = (head1 & 0b00000100) != 0;
                    inv = buf.get()&0xFF;
                    if (seg) {
                        seq = buf.get()&0xFF;
                        pws = buf.get()&0xFF;
                    }
                    serviceChoice = buf.get()&0xFF;
                    serviceRequest = new ASNBuffer(buf);     // remainder of the buffer is the service request
                    break;
                case SEG_ACK:
                    nak = (head1 & 0b00000010) != 0;
                    srv = (head1 & 0b00000001) != 0;
                    inv = buf.get()&0xFF;
                    seq = buf.get()&0xFF;
                    aws = buf.get()&0xFF;
                    break;
                case ERROR:
                    nak = (head1 & 0b00000010) != 0;
                    srv = (head1 & 0b00000001) != 0;
                    inv = buf.get()&0xFF;
                    serviceChoice = buf.get()&0xFF;
                    serviceRequest = new ASNBuffer(buf);     // remainder of the buffer is the service request
                    break;
                case REJECT:
                    inv = buf.get()&0xFF;
                    reason = buf.get()&0xFF;
                    break;
                case ABORT:
                    inv = buf.get()&0xFF;
                    srv = (head1 & 0b00000001) != 0;
                    reason = buf.get()&0xFF;
                    break;
            }
        }
        catch (BufferUnderflowException e) {
            log.error("Received truncated APDU - could not finish parsing");
            failure = new Failure.Reject(RejectReason.MISSING_REQUIRED_PARAMETER);
        }
    }

    public int getMaxResponseSize() {
        switch (maxResp) {
            case 0:  return 50;
            case 1:  return 128;
            case 2:  return 206;
            case 3:  return 480;
            case 4:  return 1024;
            case 5:  return 1476;
            default: return 0;     // invalid
        }
    }

    public String toString() {
        if (!Application.configuration.getBoolean("stack.debugDisplayAPDUs",true)) return "(no decode)";
        StringBuilder result = new StringBuilder();
        switch (pduType) {
            case CONF_REQ:
                result.append("CnfReq");
                result.append(" seg=").append(seg);
                result.append(" mor=").append(mor);
                result.append(" sa=").append(sa);
                result.append(" maxs=").append(maxSegs);
                result.append(" maxr=").append(maxResp);
                result.append(" inv=").append(inv).append(" ");
                if (seg) result.append("seq=").append(seq).append(" pws=").append(pws).append(" ");
                result.append(confServiceChoiceToString(serviceChoice)).append(" ");
                result.append(serviceRequest.toString());
                break;
            case UNCONF_REQ:
                result.append("UncReq").append(" ");
                result.append(unconfServiceChoiceToString(serviceChoice)).append(" ");
                result.append(serviceRequest.toString());
                break;
            case SIMPLE_ACK:
                result.append("SplAck").append(" ");
                result.append(" inv=").append(inv).append(" ");
                result.append(confServiceChoiceToString(serviceChoice));
                break;
            case CONF_ACK:
                result.append("CmpAck").append(" ");
                result.append(" seg=").append(seg);
                result.append(" mor=").append(mor);
                result.append(" inv=").append(inv).append(" ");
                if (seg) result.append("seq=").append(seq).append(" pws=").append(pws).append(" ");
                result.append(confServiceChoiceToString(serviceChoice)).append(" ");
                result.append(serviceRequest.toString());
                break;
            case SEG_ACK:
                result.append("SegAck").append(" ");
                result.append(" nak=").append(nak);
                result.append(" srv=").append(srv);
                result.append(" seq=").append(seq);
                result.append(" aws=").append(aws);
                break;
            case ERROR:
                result.append("Error");
                result.append(" nak=").append(nak);
                result.append(" srv=").append(srv).append(" ");
                result.append(confServiceChoiceToString(serviceChoice)).append(" ");
                result.append(serviceRequest.toString());
                break;
            case REJECT:
                result.append("Reject");
                result.append(" inv=").append(inv);
                result.append(" rsn=").append(reason);
                break;
            case ABORT:
                result.append("Abort");
                result.append(" srv=").append(srv);
                result.append(" rsn=").append(reason);
                break;
        }
        return result.toString();
    }


    public String confServiceChoiceToString(int serviceChoice) {
        switch (serviceChoice) {
            case 1:  return "CCOV";
            case 31: return "CCOVM";
            case 2:  return "CEN";
            case 3:  return "GAS";
            case 4:  return "GES";
            case 29: return "GEI";
            case 27: return "LSOP";
            case 5:  return "SCOV";
            case 28: return "SCOVP";
            case 30: return "SCOVPM";
            case 6:  return "ARF";
            case 7:  return "AWF";
            case 8:  return "ALE";
            case 9:  return "RLE";
            case 10: return "CO";
            case 11: return "DO";
            case 12: return "ReadProp";
            case 14: return "ReadPropM";
            case 26: return "RR";
            case 15: return "WriteProp";
            case 16: return "WritePropM";
            case 17: return "DCC";
            case 18: return "CPT";
            case 19: return "CTM";
            case 20: return "RD";
            case 21: return "VTO";
            case 22: return "VTC";
            case 23: return "VTD";
            default: return "???";
        }
    }

    public String unconfServiceChoiceToString(int serviceChoice) {
        switch (serviceChoice) {
            case 0:  return "I-Am";
            case 1:  return "I-Have";
            case 2:  return "UCOV";
            case 3:  return "UEVT";
            case 4:  return "UPVT";
            case 5:  return "UTM";
            case 6:  return "Time";
            case 7:  return "Who-Has";
            case 8:  return "Who-Is";
            case 9:  return "UTC";
            case 10: return "WG";
            case 11: return "UCOVM";
            default: return "???";
        }
    }

}
