// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.common;

import dev.bscs.bacnet.stack.Failure;
import dev.bscs.bacnet.stack.constants.ErrorClass;
import dev.bscs.bacnet.stack.constants.ErrorCode;

import java.net.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Just a collection of static methods for formatting some advanced data types to/from strings.
 * @author drobin
 */
public class Formatting {

    private static char[] hexDigits = new char[]{'0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F',};

    public static int parseInteger(String s, int defaultValue) {
        try { return Integer.parseInt(s); }
        catch (NumberFormatException e) { return defaultValue; }
    }

    public static String toHex(byte[] bytes, int max) {
        if (bytes == null) return "(null)";
        StringBuilder hex = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            hex.append(hexDigits[bytes[i]>>4&0x0F]).append(hexDigits[bytes[i]&0x0F]);
            if (i==max) { hex.append("..."); break; }
        }
        return hex.toString();
    }

    public static String toHex(byte[] bytes) {
        if (bytes == null) return "(null)";
        else return toHex(bytes,bytes.length);
    }

    public static String toMac(byte[] bytes) { // guess how to show mac
        if (bytes == null) return "(null)";
        // show MS/TP or ARCNET as decimal.  if it smells like IPv4 then show as d.d.d.d:p. Else as raw hex
        if (bytes.length == 1) return Integer.toString(bytes[0]%0xFF);
        if (bytes.length == 6 && (bytes[4]&0xFF) == 0xBA && (bytes[5]&0xFF) == 0xC0) return toIPAndPort(bytes);
        return toHex(bytes);
    }

    public static String toNetMac(int net, byte[] bytes) {
        return net+":"+toMac(bytes);
    }

    public static String toIP(byte[] bytes) {
        if (bytes == null) return "(null)";
        if (bytes.length!=4) return toHex(bytes,bytes.length);
        return String.format("%d.%d.%d.%d",bytes[0]&0xff,bytes[1]&0xff,bytes[2]&0xff,bytes[3]&0xff);
    }

    public static String toIPAndPort(byte[] bytes) {
        if (bytes == null) return "(null)";
        if (bytes.length!=6) return toHex(bytes,bytes.length);
        return String.format("%d.%d.%d.%d:%d",bytes[0]&0xff,bytes[1]&0xff,bytes[2]&0xff,bytes[3]&0xff,((bytes[4]&0xff)<<8)|(bytes[5]&0xff));
    }

    public static String toHex(byte b) {
        if ((b & 0xF0) == 0) return "0"+Integer.toHexString(b);
        else                 return Integer.toHexString(b);
    }

    public static UUID parseUUID(ByteBuffer buf) {
        long msb = buf.getLong();
        long lsb = buf.getLong();
        UUID uuid = new UUID(msb,lsb);
        return uuid;
    }

    public static void generateUUID(ByteBuffer buf, UUID uuid) {
        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();
        buf.putLong(msb);
        buf.putLong(lsb);
    }

    private static final String digits = "0123456789ABCDEF";

    public static byte[] fromHex(String hex) throws Failure.Error {
        if (hex.length()%2 != 0) throw new Failure.Error(ErrorClass.PROPERTY, ErrorCode.VALUE_OUT_OF_RANGE,"Odd number of chars in hex value");
        byte[] result = new byte[hex.length()/2];
        for (int i = 0; i < hex.length(); i += 2) {
            int high = digits.indexOf(Character.toUpperCase(hex.charAt(i)));
            int low  = digits.indexOf(Character.toUpperCase(hex.charAt(i+1)));
            if (high == -1 || low == -1) throw new Failure.Error(ErrorClass.PROPERTY, ErrorCode.VALUE_OUT_OF_RANGE,"Invalid char in hex value");
            result[i/2] = (byte)((high<<4) + low);
        }
        return result;
    }

    /**
     *  Parse a string into a BACnet mac address:
     *  d            one decimal number will become one octet.
     *  hhhhhhhhhhhh 12 hex digits will become 6 octets.
     *  0xhh...      variable hex will become 6 octets filled from low end.
     *  d.d.d.d      will be parsed as an IP address with port 47808
     *  d.d.d.d:d    will be parsed as an IP address and port
     * @param mac
     * @return
     * @throws Failure.Error
     */
    public static byte[] fromMacString(String mac) throws Failure.Error {
        try {
            if (mac.startsWith("0x")) {
                byte[] partial = fromHex(mac.substring(2));
                if (partial.length<1 || partial.length>6) throw new Failure.Error(ErrorClass.PROPERTY, ErrorCode.VALUE_OUT_OF_RANGE,"invalid length after 'x'");
                byte[] result = new byte[]{0,0,0,0,0,0};
                System.arraycopy( partial, 0, result, 6-partial.length, partial.length);
                return result;
            }
            else if (mac.contains(".") && mac.contains(":")) { // n.n.n.n:p
                URI uri = new URI("dummy://"+mac);
                InetSocketAddress address = new InetSocketAddress (uri.getHost(),uri.getPort());
                byte[] result = new byte[6];
                System.arraycopy(address.getAddress().getAddress(),0,result,0,4);
                result[4] = (byte)(address.getPort()>>8);
                result[5] = (byte)(address.getPort()&0xFF);
                return result;
            }
            else if (mac.contains(".")) { // n.n.n.n + assumed 47808
                URI uri = new URI("dummy://"+mac);
                InetAddress address = InetAddress.getByName(uri.getHost());
                byte[] result = new byte[6];
                System.arraycopy(address.getAddress(),0,result,0,4);
                result[4] = (byte)(0xBA);
                result[5] = (byte)(0xC0);
                return result;
            }
            else if (mac.length()==12) {
                return fromHex(mac);
            }
            else if (mac.length()<=3) {
                byte[] result = new byte[1];
                result[0] = (byte)Integer.parseUnsignedInt(mac);
                return result;
            }
            else throw new Failure.Error(ErrorClass.PROPERTY, ErrorCode.VALUE_OUT_OF_RANGE,"could not parse address");
        }
        catch (URISyntaxException|NumberFormatException|UnknownHostException e) {
            throw new Failure.Error(ErrorClass.PROPERTY, ErrorCode.VALUE_OUT_OF_RANGE,"invalid format: "+e);
        }
    }

    public static List<String> split(String input, String delim) // a fast split WITHOUT using regex like String.split()
    {
        List<String> result = new ArrayList<>();
        int offset = 0;
        while (true)
        {
            int index = input.indexOf(delim,offset);
            if (index != -1) {
                result.add(input.substring(offset,index));
                offset = index + delim.length();
            }
            else {
                result.add(input.substring(offset));
                return result;
            }
        }
    }
    public static String splitBefore(String input, String delim)
    {
        int index = input.indexOf(delim);
        if (index == -1) return input;
        else return (input.substring(0,index));
    }

    public static String splitAfter(String input, String delim)
    {
        int index = input.indexOf(delim);
        if (index == -1) return input;
        else return (input.substring(index+1));
    }
    public static String splitBetween(String input, String delimStart, String delimEnd)
    {
        return splitBefore(splitAfter(input,delimStart),delimEnd);
    }


}
