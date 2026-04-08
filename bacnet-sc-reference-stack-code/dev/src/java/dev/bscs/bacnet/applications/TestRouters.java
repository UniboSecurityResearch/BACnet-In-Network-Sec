// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.applications;

import dev.bscs.bacnet.bacnetip.IPDatalink;
import dev.bscs.bacnet.bacnetip.IPDatalinkProperties;
import dev.bscs.bacnet.bacnetsc.SCDatalink;
import dev.bscs.bacnet.bacnetsc.SCProperties;
import dev.bscs.bacnet.bacnetsc.SCVMAC;
import dev.bscs.bacnet.stack.Device;
import dev.bscs.bacnet.stack.NetworkLayer;
import dev.bscs.bacnet.stack.objects.DeviceObjectProperties;
import dev.bscs.common.Application;

import java.util.UUID;

/**
 * A single application that creates two routers, one called "left", from IP to SC, and another called "right", from SC back to IP .
 * This allows this app to track the routing among three networks with a single log. Individual routers can be sent common commands
 * using the "n command" format, for example, "2 node report" will send the "node report" command to the right router. The left router
 * is the default device so "1 node report" is the same as "node report".
 * The configuration of this application is controlled by the TestRouters.properties file. See the top level README for details.
 * Also see {@link Application} class for details of the structure of the meaning of these overridden methods.
 * @author drobin
 */
public class TestRouters extends Application {

    private static SCDatalink   leftSCDatalink;
    private static IPDatalink   leftIPDatalink;
    private static SCDatalink   rightSCDatalink;
    private static IPDatalink   rightIPDatalink;

    @Override protected void  start() {

        int scNetworkNumber      = configuration.getInteger("app.scNetworkNumber",55501);
        int leftIPNetworkNumber  = configuration.getInteger("app.leftIPNetworkNumber",55502);
        int rightIPNetworkNumber = configuration.getInteger("app.rightIPNetworkNumber",55503);

        Device leftDevice = new Device(configuration);
        leftSCDatalink = new SCDatalink("SC-LEFT", new SCProperties(configuration), leftDevice, scNetworkNumber);
        leftIPDatalink = new IPDatalink("IP-LEFT", new IPDatalinkProperties(configuration), leftDevice, leftIPNetworkNumber);

        // make modified device properties before creating device.
        DeviceObjectProperties rightDeviceProperties = new DeviceObjectProperties(configuration);
        rightDeviceProperties.instance = configuration.getInteger("app.right.device.instance", leftDevice.deviceObject.properties.instance+1);
        rightDeviceProperties.uuid     = configuration.getUUID(   "app.right.device.uuid", UUID.randomUUID());

        Device rightDevice = new Device(configuration,rightDeviceProperties);
        rightSCDatalink = new SCDatalink("SC-RIGHT", new SCProperties(configuration), rightDevice, scNetworkNumber);
        rightIPDatalink = new IPDatalink("IP-RIGHT", new IPDatalinkProperties(configuration), rightDevice, rightIPNetworkNumber);

        rightIPDatalink.properties.bindPort               = configuration.getInteger("app.right.ip.bindPort", 47808);
        rightIPDatalink.properties.bindAddress            = configuration.getString( "app.right.ip.bindAddress", "0.0.0.0"); // no guess, just cause error
        rightSCDatalink.properties.vmac                   = new SCVMAC(configuration.getMAC("app.right.sc.vmac", 6,SCVMAC.makeRandom().toBytes()));
        rightSCDatalink.properties.hubFunctionEnable      = configuration.getBoolean("app.right.sc.hubFunctionEnable", false);;
        rightSCDatalink.properties.hubFunctionBindURI     = configuration.getString( "app.right.sc.hubFunctionBindURI", "");
        rightSCDatalink.properties.directConnectEnable    = configuration.getBoolean("app.right.sc.directConnectEnable", false);
        rightSCDatalink.properties.directConnectBindURI   = configuration.getString( "app.right.sc.directConnectBindURI", "");
        rightSCDatalink.properties.directConnectAcceptURIs= configuration.getString( "app.right.sc.directConnectAcceptURIs", "");

        leftIPDatalink.start();
        leftSCDatalink.start();
        rightIPDatalink.start();
        rightSCDatalink.start();

    }

    @Override protected void addCommands() {
        BACnetCommands.addCommands();
        SCCommands.addCommands();
    }

    @Override protected void stop() {
        leftIPDatalink.stop();
        leftSCDatalink.stop();
        rightIPDatalink.stop();
        rightSCDatalink.stop();
    }

    @Override protected void close() {
        leftIPDatalink.close();
        leftSCDatalink.close();
        rightIPDatalink.close();
        rightSCDatalink.close();
    }

}
