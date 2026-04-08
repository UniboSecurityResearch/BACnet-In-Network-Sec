// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.stack.constants;

public class RejectReason {
    public static final int OTHER				        = 0;
    public static final int BUFFER_OVERFLOW				= 1;
    public static final int INCONSISTENT_PARAMETERS		= 2;
    public static final int INVALID_PARAMETER_DATA_TYPE	= 3;
    public static final int INVALID_TAG				    = 4;
    public static final int MISSING_REQUIRED_PARAMETER  = 5;
    public static final int PARAMETER_OUT_OF_RANGE		= 6;
    public static final int TOO_MANY_ARGUMENTS			= 7;
    public static final int UNDEFINED_ENUMERATION	    = 8;
    public static final int UNRECOGNIZED_SERVICE	    = 9;

    public static String toString(int reason)  {
        switch (reason) {
            case OTHER:				         return "OTHER";
            case BUFFER_OVERFLOW:			 return "BUFFER_OVERFLOW";
            case INCONSISTENT_PARAMETERS:	 return "INCONSISTENT_PARAMETERS";
            case INVALID_PARAMETER_DATA_TYPE:return "INVALID_PARAMETER_DATA_TYPE";
            case INVALID_TAG:				 return "INVALID_TAG";
            case MISSING_REQUIRED_PARAMETER: return "MISSING_REQUIRED_PARAMETER";
            case PARAMETER_OUT_OF_RANGE:	 return "PARAMETER_OUT_OF_RANGE";
            case TOO_MANY_ARGUMENTS:		 return "TOO_MANY_ARGUMENTS";
            case UNDEFINED_ENUMERATION:	     return "UNDEFINED_ENUMERATION";
            case UNRECOGNIZED_SERVICE:	     return "UNRECOGNIZED_SERVICE";
            default:                         return "???";
        }
    }

}
