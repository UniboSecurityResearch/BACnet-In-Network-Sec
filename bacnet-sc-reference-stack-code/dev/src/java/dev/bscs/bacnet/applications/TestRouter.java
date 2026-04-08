// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.applications;

import dev.bscs.bacnet.bacnetip.IPDatalink;
import dev.bscs.bacnet.bacnetip.IPDatalinkProperties;
import dev.bscs.bacnet.bacnetsc.SCDatalink;
import dev.bscs.bacnet.bacnetsc.SCProperties;
import dev.bscs.bacnet.stack.Device;
import dev.bscs.bacnet.stack.NetworkLayer;
import dev.bscs.common.Application;

/**
 * An application that creates one device that is a router between BACnet/IP and BACnet/SC and can optionally be a hub.
 * The configuration of this application is controlled by the TestRouter.properties file. See the top level README for details.
 * Also see {@link Application} class for details of the structure of the meaning of these overridden methods.
 * @author drobin
 */
public class TestRouter extends Application {

    private static SCDatalink scDatalink;
    private static IPDatalink ipDatalink;

    @Override protected void  start() {
        Device device = new Device(configuration);
        int    scNetworkNumber = configuration.getInteger("app.scNetworkNumber",55501);
        int    ipNetworkNumber = configuration.getInteger("app.ipNetworkNumber",55502);
        scDatalink = new SCDatalink("SC-1", new SCProperties(configuration),device,scNetworkNumber);
        ipDatalink = new IPDatalink("IP-1", new IPDatalinkProperties(configuration),device,ipNetworkNumber);
        scDatalink.start();
        ipDatalink.start();
    }

    @Override protected void addCommands() {
        BACnetCommands.addCommands();
        SCCommands.addCommands();
    }

    @Override protected void stop() {
        scDatalink.stop();
        ipDatalink.stop();
    }

    @Override protected void close() {
        scDatalink.close();
        ipDatalink.close();
    }

}
