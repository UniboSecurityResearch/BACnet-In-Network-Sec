// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.stack.constants;

public class ErrorClass {
    public static final int DEVICE        = 0;
    public static final int OBJECT        = 1;
    public static final int PROPERTY      = 2;
    public static final int RESOURCES     = 3;
    public static final int SECURITY      = 4;
    public static final int SERVICES      = 5;
    public static final int VT            = 6;
    public static final int COMMUNICATION = 7;

    public static String toString(int reason)  {
        switch (reason) {
            case DEVICE:        return "DEVICE";
            case OBJECT:        return "OBJECT";
            case PROPERTY:      return "PROPERTY";
            case RESOURCES:     return "RESOURCES";
            case SECURITY:      return "SECURITY";
            case SERVICES:      return "SERVICES";
            case VT:            return "VT";
            case COMMUNICATION: return "COMMUNICATION";
            default:            return "???";
        }
    }

}
