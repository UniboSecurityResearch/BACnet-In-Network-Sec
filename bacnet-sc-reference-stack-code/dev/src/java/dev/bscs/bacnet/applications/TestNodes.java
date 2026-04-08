// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.applications;

import dev.bscs.bacnet.bacnetsc.SCDatalink;
import dev.bscs.bacnet.bacnetsc.SCProperties;
import dev.bscs.bacnet.bacnetsc.SCVMAC;
import dev.bscs.bacnet.stack.Device;
import dev.bscs.bacnet.stack.NetworkLayer;
import dev.bscs.bacnet.stack.objects.DeviceObjectProperties;
import dev.bscs.common.Application;
import dev.bscs.common.Command;
import dev.bscs.common.CommandProcessor;
import dev.bscs.common.Shell;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A single application that creates 1..n devices, each configured to connect to the same primary and failover hubs.
 * This allows this single app to act as any number of a hoard of nodes connecting to the hubs.
 * Manual commands are provided to show the status of all the nodes.  The "nodestats" command will list the
 * connection status of all the nodes individually.  The "nodecounts" command will show just the totals for how many nodes
 * are connected to primary, failover, or none.  In addition to these two app-specific commands, individual nodes can be
 * sent common commands using the "n command" format, for example, "42 node report" will send the "node report" command
 * to node #42.
 * The configuration of this application is controlled by the TestNodes.properties file. See the top level README for details.
 * Also see {@link Application} class for details of the structure of the meaning of these overridden methods.
 * @author drobin
 */
public class TestNodes extends Application {

    private static List<Zergling>  zerglings = new ArrayList<>();

    protected void start()  {
        int numberOfNodes = configuration.getInteger("app.numberOfNodes",2);
        if (numberOfNodes == 0) { Application.shutdown("configuration property app.numberOfNodes is zero. Nothing to do here!",false); return; }
        // create the hoard
        for (int i=0; i<numberOfNodes; i++) zerglings.add(new Zergling(i));
        // now, go, my minions!
        for (Zergling minion : zerglings) minion.start();
    }

    @Override protected void stop()  { for (Zergling node : zerglings) node.stop(); }

    @Override protected void close() { for (Zergling node : zerglings) node.close(); }

    @Override protected void addCommands() {
        BACnetCommands.addCommands();
        SCCommands.addCommands();
        CommandProcessor.addCommand(new NodestatsCommand());
        CommandProcessor.addCommand(new NodecountsCommand());
    }

    public static class NodestatsCommand extends Command {
        public NodestatsCommand() { super("nodestats", "nodestats", "Gives statistics of each node individually"); }
        @Override public boolean execute(String[] words) {
            if (words.length > 0 && words[0].equals("help")) {
                Shell.println(synopsis);
                return true;
            }
            for (Zergling zergling : zerglings) {
                int state = zergling.scDatalink.node.hubConnector.getStateAsInt(); // 0=NO_CONNECT 1=CONNECTED_PRIMARY 2=CONNECTED_FAILOVER;
                Shell.println(zergling.scDatalink.getName() + ": " + (state == 1 ? "Primary" : state == 2 ? "Failover" : "---"));
            }
            return true;
        }
    }

    public static class NodecountsCommand extends Command {
        public NodecountsCommand() { super("nodecounts", "nodecounts", "Gives total node counts for primary, failover, or none"); }
        @Override public boolean execute(String[] words) {
            int noconnect=0;
            int primary=0;
            int failover=0;
            for (Zergling zergling : zerglings) {
                int state = zergling.scDatalink.node.hubConnector.getStateAsInt(); // 0=NO_CONNECT 1=CONNECTED_PRIMARY 2=CONNECTED_FAILOVER;
                if      (state == 0) noconnect++;
                else if (state == 1) primary++;
                else if (state == 2) failover++;
            }
            Shell.println("Primary "+primary+" Failover "+failover+" None "+noconnect);
            return true;
        }
    }

    /////////////////////////////////////////

    private static class Zergling {
        public Device device;
        public SCDatalink scDatalink;
        public Zergling(int i) {
            DeviceObjectProperties deviceObjectProperties = new DeviceObjectProperties(configuration);
            deviceObjectProperties.instance += i;
            deviceObjectProperties.uuid = UUID.randomUUID();
            device = new Device(configuration,deviceObjectProperties);
            SCProperties datalinkProperties = new SCProperties(configuration);
            // make the properties unique for each node
            datalinkProperties.vmac   = SCVMAC.makeRandom();
            scDatalink = new SCDatalink("SC-"+(i+1), datalinkProperties, device, 0);
        }
        public void start() { scDatalink.start(); }
        public void stop()  { scDatalink.stop();  }
        public void close() { scDatalink.close();  }
    }

}
