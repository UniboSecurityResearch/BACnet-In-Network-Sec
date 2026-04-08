// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.stack.data;

import dev.bscs.bacnet.stack.data.base.BACnetBitString;

public class BACnetObjectTypesSupported extends BACnetBitString {
    public BACnetObjectTypesSupported(int... trueBits) { super(_LENGTH, trueBits); }
    public static final int ANALOG_INPUT		    = 0;
    public static final int ANALOG_OUTPUT		    = 1;
    public static final int ANALOG_VALUE		    = 2;
    public static final int BINARY_INPUT		    = 3;
    public static final int BINARY_OUTPUT		    = 4;
    public static final int BINARY_VALUE		    = 5;
    public static final int CALENDAR			    = 6;
    public static final int COMMAND		            = 7;
    public static final int DEVICE			        = 8;
    public static final int EVENT_ENROLLMENT 	    = 9;
    public static final int FILE			        = 10;
    public static final int GROUP			        = 11;
    public static final int LOOP			        = 12;
    public static final int MULTI_STATE_INPUT	    = 13;
    public static final int MULTI_STATE_OUTPUT	    = 14;
    public static final int NOTIFICATION_CLASS	    = 15;
    public static final int PROGRAM			        = 16;
    public static final int SCHEDULE			    = 17;
    public static final int AVERAGING		        = 18;
    public static final int MULTI_STATE_VALUE	    = 19;
    public static final int TREND_LOG			    = 20;
    public static final int LIFE_SAFETY_POINT	    = 21;
    public static final int LIFE_SAFETY_ZONE	    = 22;
    public static final int ACCUMULATOR		        = 23;
    public static final int PULSE_CONVERTER		    = 24;
    public static final int EVENT_LOG		        = 25;
    public static final int GLOBAL_GROUP		    = 26;
    public static final int TREND_LOG_MULTIPLE	    = 27;
    public static final int LOAD_CONTROL		    = 28;
    public static final int STRUCTURED_VIEW		    = 29;
    public static final int ACCESS_DOOR		        = 30;
    public static final int TIMER			        = 31;
    public static final int ACCESS_CREDENTIAL	    = 32;
    public static final int ACCESS_POINT		    = 33;
    public static final int ACCESS_RIGHTS		    = 34;
    public static final int ACCESS_USER		        = 35;
    public static final int ACCESS_ZONE		        = 36;
    public static final int CREDENTIAL_DATA_INPUT	= 37;
    public static final int NETWORK_SECURITY		= 38;
    public static final int BITSTRING_VALUE		    = 39;
    public static final int CHARACTERSTRING_VALUE	= 40;
    public static final int DATEPATTERN_VALUE		= 41;
    public static final int DATE_VALUE		        = 42;
    public static final int DATETIMEPATTERN_VALUE	= 43;
    public static final int DATETIME_VALUE 		    = 44;
    public static final int INTEGER_VALUE 		    = 45;
    public static final int LARGE_ANALOG_VALUE	    = 46;
    public static final int OCTETSTRING_VALUE		= 47;
    public static final int POSITIVE_INTEGER_VALUE	= 48;
    public static final int TIMEPATTERN_VALUE	 	= 49;
    public static final int TIME_VALUE 		        = 50;
    public static final int NOTIFICATION_FORWARDER	= 51;
    public static final int ALERT_ENROLLMENT		= 52;
    public static final int CHANNEL			        = 53;
    public static final int LIGHTING_OUTPUT		    = 54;
    public static final int BINARY_LIGHTING_OUTPUT	= 55;
    public static final int _LENGTH                 = 56;
    //public static final int NETWORK_PORT		    = 56;
    //public static final int ELEVATOR_GROUP		= 57;
    //public static final int ESCALATOR			    = 58;
    //public static final int LIFT			        = 59;
    //public static final int _LENGTH               = 60;

}
