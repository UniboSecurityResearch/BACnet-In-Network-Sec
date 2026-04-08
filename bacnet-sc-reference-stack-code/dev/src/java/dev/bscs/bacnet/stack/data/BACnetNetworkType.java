// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.stack.data;

import dev.bscs.bacnet.stack.data.base.BACnetEnumerated;

public class BACnetNetworkType extends BACnetEnumerated {
    public BACnetNetworkType(int value) { super(value); }
    public static final int ETHERNET	= 0;
    public static final int ARCNET		= 1;
    public static final int MSTP		= 2;
    public static final int PTP		    = 3;
    public static final int LONTALK		= 4;
    public static final int IPV4		= 5;
    public static final int ZIGBEE		= 6;
    public static final int VIRTUAL		= 7;
    public static final int IPV6		= 9;
    public static final int SERIAL		= 10;
    public static final int PROPRIETARY = 64;
}
