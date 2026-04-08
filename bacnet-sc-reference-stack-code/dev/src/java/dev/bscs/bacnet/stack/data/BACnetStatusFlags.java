// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.stack.data;

import dev.bscs.bacnet.stack.data.base.BACnetBitString;

public class BACnetStatusFlags extends BACnetBitString {
    public BACnetStatusFlags()      { super(_LENGTH); }
    public static final int IN_ALARM       = 0;
    public static final int FAULT          = 2;
    public static final int OVERRIDDEN     = 2;
    public static final int OUT_OF_SERVICE = 3;
    public static final int _LENGTH        = 4;
}
