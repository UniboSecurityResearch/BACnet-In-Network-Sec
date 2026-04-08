// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.bacnetsc;

import dev.bscs.common.Timer;

/**
 * Holds the results of a successful resolution of the direct connect addresses for a given SC VMAC.
 * A list of these is maintained by {@link SCNode} as a result of receiving Address-Resolution-ACK.
 * It contains a freshness timer that can be used to invalidate the resolution after a certain mount of time.
 * @author drobin
 */
public class SCAddressResolution {
    public SCVMAC     vmac;
    public String[]   urls;
    public Timer      freshness;
    public SCAddressResolution (SCVMAC vmac, String[] urls, int freshnessLimit) {
        if (urls == null) urls = new String[0];
        this.vmac = vmac; this.urls = urls; freshness = new Timer(freshnessLimit);
    }
    public String toString() {
        return "{vmac="+vmac+" urls="+String.join(" ",urls)+" time="+freshness+"}";
    }
}
