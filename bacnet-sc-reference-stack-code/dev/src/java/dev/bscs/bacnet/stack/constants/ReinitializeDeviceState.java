// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.stack.constants;

public class ReinitializeDeviceState {
    public static final int COLDSTART        = 0;
    public static final int WARMSTART        = 1;
    public static final int START_BACKUP     = 2;
    public static final int END_BACKUP       = 3;
    public static final int START_RESTORE    = 4;
    public static final int END_RESTORE      = 5;
    public static final int ABORT_RESTORE    = 6;
    public static final int ACTIVATE_CHANGES = 7;
}
