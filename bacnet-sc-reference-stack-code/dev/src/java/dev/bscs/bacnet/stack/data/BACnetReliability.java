// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.stack.data;

import dev.bscs.bacnet.stack.data.base.BACnetEnumerated;

public class BACnetReliability extends BACnetEnumerated {
    public BACnetReliability(int value) { super(value); }
    public static final int NO_FAULT_DETECTED			     =  0;
    public static final int NO_SENSOR			             =  1;
    public static final int OVER_RANGE			             =  2;
    public static final int UNDER_RANGE			             =  3;
    public static final int OPEN_LOOP			             =  4;
    public static final int SHORTED_LOOP			         =  5;
    public static final int NO_OUTPUT			             =  6;
    public static final int UNRELIABLE_OTHER			     =  7;
    public static final int PROCESS_ERROR			         =  8;
    public static final int MULTI_STATE_FAULT			     =  9;
    public static final int CONFIGURATION_ERROR		         = 10;
    public static final int COMMUNICATION_FAILURE		     = 12;
    public static final int MEMBER_FAULT			         = 13;
    public static final int MONITORED_OBJECT_FAULT		     = 14;
    public static final int TRIPPED				             = 15;
    public static final int LAMP_FAILURE			         = 16;
    public static final int ACTIVATION_FAILURE			     = 17;
    public static final int RENEW_DHCP_FAILURE 		         = 18;
    public static final int RENEW_FD_REGISTRATION_FAILURE 	 = 19;
    public static final int RESTART_AUTO_NEGOTIATION_FAILURE = 20;
    public static final int RESTART_FAILURE 			     = 21;
    public static final int PROPRIETARY_COMMAND_FAILURE 	 = 22;
    public static final int FAULTS_LISTED			         = 23;
    public static final int REFERENCED_OBJECT_FAULT		     = 24;
}
