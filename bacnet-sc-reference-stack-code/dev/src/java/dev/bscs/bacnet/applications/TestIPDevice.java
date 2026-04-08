// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.applications;

import dev.bscs.bacnet.bacnetip.IPDatalink;
import dev.bscs.bacnet.bacnetip.IPDatalinkProperties;
import dev.bscs.bacnet.stack.Device;
import dev.bscs.bacnet.stack.NetworkLayer;
import dev.bscs.common.Application;

/**
 * A simple BACnet/IP device.  No SC functionality.
 * The configuration of this application is controlled by the TestIPDevice.properties file. See the top level README for details.
 * Also see {@link Application} class for details of the structure of the meaning of these overridden methods.
 * @author drobin
 */
public class TestIPDevice extends Application {

    private IPDatalink     ipDatalink;

    @Override protected void start() {
        Device device = new Device(configuration);
        ipDatalink = new IPDatalink("IP-1", new IPDatalinkProperties(configuration),device,0);
        ipDatalink.start();
    }

    @Override protected void addCommands() {
        BACnetCommands.addCommands();
    }


    @Override protected void stop() {
        ipDatalink.stop();
    }

    @Override protected void close() {
        ipDatalink.close();
    }

}
