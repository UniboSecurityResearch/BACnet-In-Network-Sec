// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.bacnetip;

import dev.bscs.bacnet.bacnetsc.SCLog;
import dev.bscs.common.Configuration;

/**
 * The properties needed for a BACnet/IP datalink.
 * This is a generic "data" object with all public members, so populate it any way you want.
 * For file-configured applications, there is convenience constructor that reads its values from a Properties object.
 * For embedded applications, the properties will come from somewhere else, like an IP Network Port object.
 * @author drobin
 */
public class IPDatalinkProperties implements Cloneable  {

    private static SCLog log = new SCLog(IPDatalinkProperties.class);

    //////////// CONFIGURATION PROPERTIES /////////////

    public String bindAddress      = "en0";
    public int    bindPort         = 47808;
    public int    foreignRefresh   = 0; // seconds, 0 == disabled
    public String foreignAddress   = "0.0.0.0";
    public int    foreignPort      = 47808;


    //////////// STATUS PROPERTIES /////////////

    //none yet...

    // For file-based configuration, this constructor will get its values from a Configuration object, likely read from a file.
    public IPDatalinkProperties(Configuration properties) {
        bindAddress      = properties.getString( "ip.bindAddress",bindAddress);
        bindPort         = properties.getInteger("ip.bindPort",bindPort);
        foreignRefresh   = properties.getInteger("ip.foreignRefresh",foreignRefresh);
        foreignAddress   = properties.getString( "ip.foreignAddress",foreignAddress);
        foreignPort      = properties.getInteger("ip.foreignPort",foreignPort);
    }


}
