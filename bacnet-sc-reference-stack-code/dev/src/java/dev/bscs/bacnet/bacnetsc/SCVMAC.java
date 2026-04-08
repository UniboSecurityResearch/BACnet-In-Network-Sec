// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.bacnetsc;

import dev.bscs.common.Formatting;

import java.nio.ByteBuffer;
import java.util.Random;

/**
 * A holder for the six bytes that make up an SC VMAC address, plus some methods for parsing, generating, and comparing.
 * @author drobin
 */
public class SCVMAC {

    private byte[] vmac = new byte[]{0,0,0,0,0,0};

    public static final SCVMAC BROADCAST = new SCVMAC(new byte[]{-1,-1,-1,-1,-1,-1});

    public SCVMAC(byte[] bytes) {
        vmac = bytes.clone();
    }

    public SCVMAC(String s) throws Exception {
        if (s.contains(".")) {  // dotted decimal
            String[] parts = s.split("\\.");
            if (parts.length!=6) throw new Exception("Not a valid dotted decimal VMAC: "+s);
            for (int i = 0; i < 6; i++) vmac[i] = (byte)Integer.parseUnsignedInt(parts[i]);
        }
        else if (s.contains(":")) { // hex with colons
            String[] parts = s.split(":");
            if (parts.length!=6) throw new Exception("Not a valid colon-separated hex VMAC: "+s);
            for (int i = 0; i < 6; i++) vmac[i] = (byte)Integer.parseUnsignedInt(parts[i],16);
        }
        else {  // straight hex
            if (s.length()!=12) throw new Exception("Not a valid hex VMAC: "+s);
            for (int i = 0; i < 6; i++) vmac[i] = (byte)Integer.parseUnsignedInt(s.substring(i * 2, i * 2 + 2), 16);
        }
    }

    public SCVMAC(ByteBuffer buf)  {
        vmac[0]=buf.get();
        vmac[1]=buf.get();
        vmac[2]=buf.get();
        vmac[3]=buf.get();
        vmac[4]=buf.get();
        vmac[5]=buf.get();
    }

    static public SCVMAC makeRandom() {
        byte[] bytes = new byte[6];
        new Random().nextBytes(bytes);
        bytes[0] = (byte)(bytes[0] & (byte)0b11110000 | (byte)0b00000010); // low bits of first byte = '0010', i.e. local unicast
        return new SCVMAC(bytes);
    }

    public void generate(ByteBuffer buf) {
        buf.put(vmac[0]);
        buf.put(vmac[1]);
        buf.put(vmac[2]);
        buf.put(vmac[3]);
        buf.put(vmac[4]);
        buf.put(vmac[5]);
    }

    public byte[]  toBytes()     { return vmac.clone(); }

    public boolean isBroadcast() { return equals(BROADCAST); }

    public boolean equals(SCVMAC other) {  // important for many reasons... but super important for use as a Map key!
        for (int i=0; i<6; i++) if (other.vmac[i] != vmac[i]) return false;
        return true;
    }

    public String  toString() {
        return isBroadcast()? "Broadcast" : Formatting.toHex(vmac);
    }
    public static String  toString(byte[] bytes) {
        return new SCVMAC(bytes).toString();
    }

    public String  toHex() {
        return Formatting.toHex(vmac);
    }

}
