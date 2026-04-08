// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.applications;

import dev.bscs.bacnet.bacnetsc.SCDatalink;
import dev.bscs.bacnet.bacnetsc.SCProperties;
import dev.bscs.bacnet.bacnetsc.SCVMAC;
import dev.bscs.bacnet.stack.Device;
import dev.bscs.bacnet.stack.objects.DeviceObjectProperties;
import dev.bscs.common.Application;
import dev.bscs.common.Command;
import dev.bscs.common.CommandProcessor;

import java.util.UUID;

/**
 * A single application that creates two devices, each with a BACnet/SC hub function. This allows this single app to
 * act as both the primary and failover hubs. Manual commands are provided to take either hub up or down to facilitate
 * testing of external nodes movement between the two hubs. The internal nodes can be turned off to simplify the logs.
 * In addition to the app-specific commands, individual hubs can be sent common commands using the "n command" format,
 * for example, "2 hub report" will send the "hub report" command to the failover device. The primary is the default device
 * so "1 hub report" is the same as "hub report".
 * The configuration of this application is controlled by the TestHubs.properties file. See the top level README for details.
 * Also see {@link Application} class for details of the structure of the meaning of these overridden methods.
 * @author drobin
 */
public class TestHubs extends Application {

    private static SCDatalink  primaryDatalink;
    private static SCDatalink  failoverDatalink;

    protected void start()  {

        Device primaryDevice = new Device(configuration);
        primaryDatalink = new SCDatalink("SC-P", new SCProperties(configuration),primaryDevice,0);

        DeviceObjectProperties deviceObjectProperties = new DeviceObjectProperties(configuration);
        deviceObjectProperties.instance  = configuration.getInteger("app.failover.device.instance", primaryDevice.deviceObject.properties.instance+1);
        deviceObjectProperties.uuid      = configuration.getUUID(   "app.failover.device.uuid", UUID.randomUUID());
        Device failoverDevice = new Device(configuration, deviceObjectProperties);
        failoverDatalink = new SCDatalink("SC-F", new SCProperties(configuration),failoverDevice,0);

        // the failover gets modified based on app properties or with guesses
        failoverDatalink.properties.vmac                   = new SCVMAC(configuration.getMAC("app.failover.sc.vmac", 6,SCVMAC.makeRandom().toBytes()));
        failoverDatalink.properties.hubFunctionBindURI     = configuration.getString( "app.failover.sc.hubFunctionBindURI", "");
        failoverDatalink.properties.directConnectEnable    = configuration.getBoolean("app.failover.sc.directConnectEnable", false);
        failoverDatalink.properties.directConnectBindURI   = configuration.getString( "app.failover.sc.directConnectBindURI", "");
        failoverDatalink.properties.directConnectAcceptURIs= configuration.getString( "app.failover.sc.directConnectAcceptURIs", "");

        primaryDatalink.start();
        failoverDatalink.start();
    }

    @Override protected void stop()  {  primaryDatalink.stop(); failoverDatalink.stop(); }

    @Override protected void close() { primaryDatalink.close(); failoverDatalink.close();}

    @Override protected void addCommands() {
        BACnetCommands.addCommands();
        SCCommands.addCommands();
        CommandProcessor.addCommand(new PupCommand());
        CommandProcessor.addCommand(new PdnCommand());
        CommandProcessor.addCommand(new FupCommand());
        CommandProcessor.addCommand(new FdnCommand());
    }

    public static class PupCommand extends Command {
        public PupCommand() { super("pup", "pup", "Primary hub up"); }
        @Override public boolean execute(String[] words) {
            primaryDatalink.start();
            return true;
        }
    }
    public static class PdnCommand extends Command {
        public PdnCommand() { super("pdn", "pdn", "Primary hub down"); }
        @Override public boolean execute(String[] words) {
            primaryDatalink.stop();
            return true;
        }
    }
    public static class FupCommand extends Command {
        public FupCommand() { super("fup", "fup", "Failoover hub up"); }
        @Override public boolean execute(String[] words) {
            failoverDatalink.start();
            return true;
        }
    }
    public static class FdnCommand extends Command {
        public FdnCommand() { super("fdn", "fdn", "Failover hub down"); }
        @Override public boolean execute(String[] words) {
            failoverDatalink.stop();
            return true;
        }
    }

}
