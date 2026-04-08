// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.stack.data;

import dev.bscs.common.Formatting;

public class BACnetAddress {
    public int    networkNumber;
    public byte[] macAdddress;
    public BACnetAddress(int networkNumber, byte[] macAdddress) { this.networkNumber = networkNumber; this.macAdddress = macAdddress; }
    public String toString()  { return Formatting.toNetMac(networkNumber,macAdddress); }
}

