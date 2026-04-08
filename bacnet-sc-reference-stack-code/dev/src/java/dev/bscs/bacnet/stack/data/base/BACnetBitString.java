// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.stack.data.base;

import dev.bscs.bacnet.stack.Failure;
import dev.bscs.bacnet.stack.constants.ErrorClass;
import dev.bscs.bacnet.stack.constants.ErrorCode;
import dev.bscs.common.Formatting;
import dev.bscs.common.Log;

public class BACnetBitString extends BACnetData {

    private static final Log log = new Log(BACnetBitString.class);

    public byte[] value;

    public BACnetBitString(int length, int... trueBits)  {
        if (length == 0) value = new byte[]{0};
        else {
            int size = 1 + (length + 7) / 8;
            value = new byte[size];
            int unused = length % 8;
            if (unused != 0) unused = 8 - unused;
            value[0] = (byte)unused;
            for (int bit : trueBits) value[bit/8+1] |= 1 << (7 - (bit % 8));
        }
    }

    public BACnetBitString(byte[] value) { this.value = value.clone(); }

    public BACnetBitString(String value) throws Failure.Error {
        this(value.length());
        for (int i=0; i<value.length(); i++) {
            if (value.charAt(i)=='T' || value.charAt(i)=='t') setBit(i);
            else if (value.charAt(i)=='F' || value.charAt(i)=='f') clearBit(i);
            else throw new Failure.Error(ErrorClass.PROPERTY, ErrorCode.VALUE_OUT_OF_RANGE, "invalid character (not T,t,F,f) in bit string");
        }
    }

    public int    getDataType()      { return BACnetDataType.BIT_STRING; }

    public void   setBit(int number) {
        if (number/8 +1 > value.length) log.implementation("BitString: accessing bit number greater than length");
        else value[1+number/8] |= (byte)((1<<number%8));
    }

    public void   clearBit(int number)  {
        if (number/8 +1 > value.length) log.implementation("BitString: accessing bit number greater than length");
        else value[1+number/8] &= ~(byte)((1<<number%8));
    }

    public String toString()  { return Formatting.toHex(value); }

}
