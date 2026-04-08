// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.stack.data;

import dev.bscs.bacnet.stack.data.base.BACnetEnumerated;

public class BACnetIPMode extends BACnetEnumerated {
    public BACnetIPMode(int value) {super(value);}
    public static final int NORMAL  = 0;
    public static final int FOREIGN = 1;
    public static final int BBMD    = 2;
}
