// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.applications;

import dev.bscs.bacnet.bacnetsc.SCDatalink;
import dev.bscs.bacnet.bacnetsc.SCProperties;
import dev.bscs.bacnet.bacnetsc.SCVMAC;
import dev.bscs.bacnet.stack.Device;
import dev.bscs.common.*;

/**
 * An application that creates one device that is a router between BACnet/SC and BACnet/SC and can optionally be hubs.
 * The configuration of this application is controlled by the TestSCSC.properties file. See the top level README for details.
 * Also see {@link Application} class for details of the structure of the meaning of these overridden methods.
 * @author drobin
 */
public class TestSCSC extends Application {

    private static SCDatalink scDatalink1;
    private static SCDatalink scDatalink2;

    @Override protected void start() {
        Device device = new Device(configuration);
        int scNetworkNumber1 = configuration.getInteger("app.scNetworkNumber1", 55501);
        int scNetworkNumber2 = configuration.getInteger("app.scNetworkNumber2", 55502);

        SCProperties scProperties1 = new SCProperties(configuration);

        SCProperties scProperties2 = new SCProperties(configuration);

        scProperties2.vmac = new SCVMAC(configuration.getMAC("app.2.sc.vmac", 6, new byte[6]));
        scProperties2.primaryHubURI = configuration.getString("app.2.sc.primaryHubURI", "");
        scProperties2.hubFunctionEnable = configuration.getBoolean("app.2.sc.hubFunctionEnable", true);
        scProperties2.hubFunctionBindURI = configuration.getString("app.2.sc.hubFunctionBindURI", "");

        scDatalink1 = new SCDatalink("SC-1", scProperties1, device, scNetworkNumber1);
        scDatalink2 = new SCDatalink("SC-2", scProperties2, device, scNetworkNumber2);

        scDatalink1.start();
        scDatalink2.start();
    }

    @Override protected void addCommands() {
        BACnetCommands.addCommands();
        SCCommands.addCommands();
    }

    @Override protected void stop() {
        scDatalink1.stop();
        scDatalink2.stop();
    }

    @Override protected void close() {
        scDatalink1.close();
        scDatalink2.close();
    }

}
