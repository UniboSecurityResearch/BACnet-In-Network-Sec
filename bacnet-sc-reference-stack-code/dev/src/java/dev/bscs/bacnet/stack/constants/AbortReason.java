// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.stack.constants;

public class AbortReason {
    public static final int OTHER 							   = 0;
    public static final int BUFFER_OVERFLOW 				   = 1;
    public static final int INVALID_APDU_IN_THIS_STATE 	       = 2;
    public static final int PREEMPTED_BY_HIGHER_PRIORITY_TASK  = 3;
    public static final int SEGMENTATION_NOT_SUPPORTED 	       = 4;
    public static final int SECURITY_ERROR 				       = 5;
    public static final int INSUFFICIENT_SECURITY 			   = 6;
    public static final int WINDOW_SIZE_OUT_OF_RANGE		   = 7;
    public static final int APPLICATION_EXCEEDED_REPLY_TIME    = 8;
    public static final int OUT_OF_RESOURCES 				   = 9;
    public static final int TSM_TIMEOUT 					   = 10;
    public static final int APDU_TOO_LONG 					   = 11;

    public static String toString(int reason)  {
        switch (reason) {
            case OTHER: 						   return "OTHER";
            case BUFFER_OVERFLOW: 				   return "BUFFER_OVERFLOW";
            case INVALID_APDU_IN_THIS_STATE: 	   return "INVALID_APDU_IN_THIS_STATE";
            case PREEMPTED_BY_HIGHER_PRIORITY_TASK:return "PREEMPTED_BY_HIGHER_PRIORITY_TASK";
            case SEGMENTATION_NOT_SUPPORTED: 	   return "SEGMENTATION_NOT_SUPPORTED";
            case SECURITY_ERROR: 				   return "SECURITY_ERROR";
            case INSUFFICIENT_SECURITY: 		   return "INSUFFICIENT_SECURITY";
            case WINDOW_SIZE_OUT_OF_RANGE:		   return "WINDOW_SIZE_OUT_OF_RANGE";
            case APPLICATION_EXCEEDED_REPLY_TIME:  return "APPLICATION_EXCEEDED_REPLY_TIME";
            case OUT_OF_RESOURCES:				   return "OUT_OF_RESOURCES";
            case TSM_TIMEOUT: 					   return "TSM_TIMEOUT";
            case APDU_TOO_LONG: 			       return "APDU_TOO_LONG";
            default:                               return "???";
        }
    }
}

