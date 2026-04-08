// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.stack.data;

import dev.bscs.bacnet.stack.data.base.BACnetEnumerated;

public class BACnetProtocolLevel extends BACnetEnumerated {
    public BACnetProtocolLevel(int value) { super(value); }
    public static final int PHYSICAL	            = 0;
    public static final int PROTOCOL		        = 1;
    public static final int BACNET_APPLICATION	    = 2;
    public static final int NON_BACNET_APPLICATION  = 3;
}
