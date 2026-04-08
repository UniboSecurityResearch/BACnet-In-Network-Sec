// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.applications;

import dev.bscs.bacnet.bacnetip.IPDatalink;
import dev.bscs.bacnet.bacnetip.IPDatalinkProperties;
import dev.bscs.bacnet.bacnetsc.SCDatalink;
import dev.bscs.bacnet.bacnetsc.SCProperties;
import dev.bscs.bacnet.bacnetsc.SCVMAC;
import dev.bscs.bacnet.stack.Device;
import dev.bscs.bacnet.stack.objects.DeviceObjectProperties;
import dev.bscs.common.Application;
import dev.bscs.common.Command;
import dev.bscs.common.CommandProcessor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A single application that is a combination of {@link TestHubs}, {@link TestRouter}, and {@link TestNodes}.
 * See those applications for explanation of configuration and commands.
 * @author drobin
 */
public class TestCombo extends Application {

    private static SCDatalink  primaryDatalink;
    private static SCDatalink  failoverDatalink;
    private static IPDatalink  ipDatalink;
    private static List<Node>  nodes = new ArrayList<>();

    protected void start()  {

        // PRIMARY DEVICE / HUB / ROUTER

        Device primaryDevice = new Device(configuration);

        int scNetworkNumber = configuration.getInteger("app.scNetworkNumber",55501);
        int ipNetworkNumber = configuration.getInteger("app.ipNetworkNumber",55502);

        ipDatalink      = new IPDatalink("IP-P", new IPDatalinkProperties(configuration), primaryDevice, ipNetworkNumber);
        primaryDatalink = new SCDatalink("SC-P", new SCProperties(configuration), primaryDevice, scNetworkNumber);

        // FAILOVER DEVICE / HUB

        DeviceObjectProperties deviceObjectProperties = new DeviceObjectProperties(configuration);
        deviceObjectProperties.instance  = configuration.getInteger("app.failover.device.instance", primaryDevice.deviceObject.properties.instance+1);
        deviceObjectProperties.uuid      = configuration.getUUID(   "app.failover.device.uuid", UUID.randomUUID());
        Device failoverDevice = new Device(configuration, deviceObjectProperties);

        failoverDatalink = new SCDatalink("SC-F", new SCProperties(configuration), failoverDevice, scNetworkNumber);

        // the failover gets modified based on app properties or with guesses
        failoverDatalink.properties.vmac                   = new SCVMAC(configuration.getMAC("app.failover.sc.vmac", 6,SCVMAC.makeRandom().toBytes()));
        failoverDatalink.properties.hubFunctionBindURI     = configuration.getString( "app.failover.sc.hubFunctionBindURI", "");
        failoverDatalink.properties.directConnectEnable    = configuration.getBoolean("app.failover.sc.directConnectEnable", false);
        failoverDatalink.properties.directConnectBindURI   = configuration.getString( "app.failover.sc.directConnectBindURI", "");
        failoverDatalink.properties.directConnectAcceptURIs= configuration.getString( "app.failover.sc.directConnectAcceptURIs", "");

        primaryDatalink.start();
        failoverDatalink.start();
        ipDatalink.start();

        // NODES

        int numberOfNodes = configuration.getInteger("app.numberOfNodes",2);
        if (numberOfNodes == 0) { Application.shutdown("Configuration property app.numberOfNodes is zero. Nothing to do here!",false); return; }
        for (int i=0; i<numberOfNodes; i++) nodes.add(new Node(i));
        for (Node node : nodes) node.start();
    }

    @Override protected void addCommands() {
        BACnetCommands.addCommands();
        SCCommands.addCommands();
        CommandProcessor.addCommand(new PupCommand());
        CommandProcessor.addCommand(new PdnCommand());
        CommandProcessor.addCommand(new FupCommand());
        CommandProcessor.addCommand(new FdnCommand());
    }

    @Override protected void stop()  {
        for (Node node : nodes) node.stop();
        primaryDatalink.stop();
        failoverDatalink.stop();
        ipDatalink.stop();
    }

    @Override protected void close() {
        for (Node node : nodes) node.close();
        primaryDatalink.close();
        failoverDatalink.close();
        ipDatalink.close();
    }

    /////////////////////////////////////////

    private class Node {
        public Device device;
        public SCDatalink scDatalink;
        public Node(int i) {
            DeviceObjectProperties deviceObjectProperties = new DeviceObjectProperties(configuration);
            deviceObjectProperties.instance = configuration.getInteger("app.nodesBaseDeviceInstance",777001) + i;
            deviceObjectProperties.description = "Just a node";
            deviceObjectProperties.modelName = "BSNode 6000";
            deviceObjectProperties.namePrefix = "Node-";
            deviceObjectProperties.uuid = UUID.randomUUID();
            device = new Device(configuration,deviceObjectProperties);
            SCProperties datalinkProperties = new SCProperties(configuration);
            // make the properties unique for each node
            long vmacBase = configuration.getLong("app.nodesBaseVMAC",777777777001L);
            try { datalinkProperties.vmac = new SCVMAC(Long.toString(vmacBase+i)); } catch (Exception ignore) {} // clean number, no exception
            int dcPort = configuration.getInteger("app.nodesBaseDirectConnectPort",4446);
            datalinkProperties.directConnectBindURI = configuration.getString("app.nodesBaseDirectConnectBindURI","wss://127.0.0.1:")+(dcPort+i);
            datalinkProperties.directConnectAcceptURIs = configuration.getString("app.nodesBaseDirectConnectAcceptURI","wss://127.0.0.1:")+(dcPort+i);
            datalinkProperties.hubFunctionEnable = false; // ! the nodes are most definitely not hubs!
            scDatalink = new SCDatalink("SC-Z"+(i+1), datalinkProperties,device,0);
        }
        public void start() { scDatalink.start(); }
        public void stop()  { scDatalink.stop();  }
        public void close() { scDatalink.close();  }
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
