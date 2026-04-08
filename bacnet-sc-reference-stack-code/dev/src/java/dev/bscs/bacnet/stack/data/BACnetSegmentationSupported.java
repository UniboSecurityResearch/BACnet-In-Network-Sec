// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.stack.data;

import dev.bscs.bacnet.stack.data.base.BACnetEnumerated;

public class BACnetSegmentationSupported extends BACnetEnumerated {
    public BACnetSegmentationSupported(int value) { super(value); }
    public static final int SEGMENTED_BOTH     = 0;
    public static final int SEGMENTED_TRANSMIT = 1;
    public static final int SEGMENTED_RECEIVE  = 2;
    public static final int SEGMENTED_NONE     = 3;
}
