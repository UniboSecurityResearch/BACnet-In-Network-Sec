// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.stack.data;

import dev.bscs.bacnet.stack.data.base.BACnetEnumerated;

public class BACnetNetworkNumberQuality extends BACnetEnumerated {
    public BACnetNetworkNumberQuality(int value) {super(value);}
    public static final int UNKNOWN             = 0;
    public static final int LEARNED             = 1;
    public static final int LEARNED_CONFIGURED  = 2;
    public static final int CONFIGURED          = 3;
}
