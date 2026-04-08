// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.stack;

import dev.bscs.common.Formatting;
import dev.bscs.common.Timer;

public class Binding {
    public int    instance;
    public int    dnet;
    public byte[] dadr;
    public long   maxAPDULengthAccepted;
    public int    segmentationSupported;
    public int    vendorID;
    public Timer  whoIsPacingTimer = new Timer();

    public String toString() {
        return instance+" "+dnet+":"+ Formatting.toMac(dadr)+" ma="+maxAPDULengthAccepted+" ss="+segmentationSupported+" vi="+vendorID;
    }
}
