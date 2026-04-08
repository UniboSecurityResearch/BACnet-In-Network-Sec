// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.stack.data;

import dev.bscs.bacnet.stack.data.base.BACnetBitString;

public class BACnetProtocolServicesSupported extends BACnetBitString {
    public BACnetProtocolServicesSupported(int... trueBits) { super(_LENGTH,trueBits);}
    public static final int ACKNOWLEDGE_ALARM		                = 0;
    public static final int CONFIRMED_COV_NOTIFICATION		        = 1;
    public static final int CONFIRMED_EVENT_NOTIFICATION            = 2;
    public static final int GET_ALARM_SUMMARY		                = 3;
    public static final int GET_ENROLLMENT_SUMMARY		            = 4;
    public static final int SUBSCRIBE_COV			                = 5;
    public static final int ATOMIC_READ_FILE			            = 6;
    public static final int ATOMIC_WRITE_FILE			            = 7;
    public static final int ADD_LIST_ELEMENT			            = 8;
    public static final int REMOVE_LIST_ELEMENT		                = 9;
    public static final int CREATE_OBJECT			                = 10;
    public static final int DELETE_OBJECT			                = 11;
    public static final int READ_PROPERTY			                = 12;
    public static final int READ_PROPERTY_MULTIPLE		            = 14;
    public static final int WRITE_PROPERTY 			                = 15;
    public static final int WRITE_PROPERTY_MULTIPLE		            = 16;
    public static final int DEVICE_COMMUNICATION_CONTROL            = 17;
    public static final int CONFIRMED_PRIVATE_TRANSFER	            = 18;
    public static final int CONFIRMED_TEXT_MESSAGE		            = 19;
    public static final int REINITIALIZE_DEVICE			            = 20;
    public static final int VT_OPEN				                    = 21;
    public static final int VT_CLOSE			                   	= 22;
    public static final int VT_DATA				                    = 23;
    public static final int I_AM				                    = 26;
    public static final int I_HAVE				                    = 27;
    public static final int UNCONFIRMED_COV_NOTIFICATION	        = 28;
    public static final int UNCONFIRMED_EVENT_NOTIFICATION	        = 29;
    public static final int UNCONFIRMED_PRIVATE_TRANSFER	        = 30;
    public static final int UNCONFIRMED_TEXT_MESSAGE		        = 31;
    public static final int TIME_SYNCHRONIZATION		            = 32;
    public static final int WHO_HAS				                    = 33;
    public static final int WHO_IS				                    = 34;
    public static final int READ_RANGE			                    = 35;
    public static final int UTC_TIME_SYNCHRONIZATION		        = 36;
    public static final int LIFE_SAFETY_OPERATION		            = 37;
    public static final int SUBSCRIBE_COV_PROPERTY		            = 38;
    public static final int GET_EVENT_INFORMATION		            = 39;
    public static final int WRITE_GROUP			                    = 40;
    public static final int _LENGTH                                 = 41;
    //public static final int SUBSCRIBE_COV_PROPERTY_MULTIPLE	      = 41;
    //public static final int CONFIRMED_COV_NOTIFICATION_MULTIPLE     = 42;
    //public static final int UNCONFIRMED_COV_NOTIFICATION_MULTIPLE   = 43;
    //public static final int _LENGTH                                 = 44;

}
