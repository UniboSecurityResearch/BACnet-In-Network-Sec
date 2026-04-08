// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.stack.constants;

public class ConfirmedServiceChoice {
    public static final int CCOV   = 1;
    public static final int CCOVM  = 31;
    public static final int CEN    = 2;
    public static final int GAS    = 3;
    public static final int GES    = 4;
    public static final int GEI    = 29;
    public static final int LSOP   = 27;
    public static final int SCOV   = 5;
    public static final int SCOVP  = 28;
    public static final int SCOVPM = 30;
    public static final int ARF    = 6;
    public static final int AWF    = 7;
    public static final int ALE    = 8;
    public static final int RLE    = 9;
    public static final int CO     = 10;
    public static final int DO     = 11;
    public static final int RP     = 12;
    public static final int RPM    = 14;
    public static final int RR     = 26;
    public static final int WP     = 15;
    public static final int WPM    = 16;
    public static final int DCC    = 17;
    public static final int CPT    = 18;
    public static final int CTM    = 19;
    public static final int RD     = 20;
    public static final int VTO    = 21;
    public static final int VTC    = 22;
    public static final int VTD    = 23;

    public static String toString(int serviceChoice) {
        switch (serviceChoice) {
            case CCOV  : return "CCOV";
            case CCOVM : return "CCOVM";
            case CEN   : return "CEN";
            case GAS   : return "GAS";
            case GES   : return "GES";
            case GEI   : return "GEI";
            case LSOP  : return "LSOP";
            case SCOV  : return "SCOV";
            case SCOVP : return "SCOVP";
            case SCOVPM: return "SCOVPM";
            case ARF   : return "ARF";
            case AWF   : return "AWF";
            case ALE   : return "ALE";
            case RLE   : return "RLE";
            case CO    : return "CO";
            case DO    : return "DO";
            case RP    : return "RP";
            case RPM   : return "RPM";
            case RR    : return "RR";
            case WP    : return "WP";
            case WPM   : return "WPM";
            case DCC   : return "DCC";
            case CPT   : return "CPT";
            case CTM   : return "CTM";
            case RD    : return "RD";
            case VTO   : return "VTO";
            case VTC   : return "VTC";
            case VTD   : return "VTD";
            default:     return "???";
        }
    }
}
