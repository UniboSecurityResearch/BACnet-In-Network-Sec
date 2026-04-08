// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.applications;

import dev.bscs.bacnet.bacnetsc.*;
import dev.bscs.bacnet.stack.*;
import dev.bscs.common.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Adds commands specific to BACnet/SC devices to the common BACnetCommands.
 * Each command documents itself. See {@link Command} for info on writing new commands.
 *
 * Some of these commands share a "selected" context, so that commands like "node" and "conn"
 * set the context for other commands to use. This context is stored in several static variables in this class.
 * Since there is only one Terminal window (for now), there is only one interactive user and this is not a problem.
 *
 * @author drobin
 */

public class SCCommands  {

    public static SCNode        selectedNode;       // defaults to first node of first device; selected with "node" command
    public static SCHubFunction selectedHub;        // defaults to first active hub found; "hub" command
    public static SCConnection  selectedConnection; // defaults to active hub connector of first node; selected with "conn" command

    /////////////////////////////////////////////////////////////
    /////////////////////////////
    /////////////////////////////////////////////////////////////

    private SCCommands() {  } // no constructor, static only

    public static void addCommands() {
        CommandProcessor.addCommand(new HubCommand());
        CommandProcessor.addCommand(new NodeCommand());
        CommandProcessor.addCommand(new ConnCommand());
        CommandProcessor.addCommand(new ASCommand()); // uses selected node
        CommandProcessor.addCommand(new ARCommand()); // uses selected node
        CommandProcessor.addCommand(new HBCommand()); // uses selected conn
        CommandProcessor.addCommand(new DCCommand());
        CommandProcessor.addCommand(new HCCommand());
        CommandProcessor.addCommand(new InjectCommand());
    }

    public static class HubCommand extends Command {
        public HubCommand() { super("hub", "hub list|select|start|stop|report [<number>]",
                "The 'hub' command performs actions on a BACnet/SC hub function.",
                "If there is more than one hub function in the application (e.g., more than one SC datalink or",
                "more than one device), then the optional <number> argument will indicate which one to operate on",
                "if not previously selected with the 'select' option.",
                "OPTIONS",
                "'hub list'            show the numbers of all the active hub functions in the application.",
                "'hub select <number>' select the hub number for future operations like 'hub stop'.",
                "'hub start'           start the hub.",
                "'hub stop'            stop the hub.",
                "'hub report'          report the status of the hub.",
                "'... <number>'        override any previous selection and act on the specified hub number."); }
        @Override public boolean execute(String[] words) {
            if (words.length < 2) { Shell.println("Usage: "+synopsis); return true;}
            String action = words[1];
            String name = (words.length > 2)? words[2] : null;
            switch (action)  {
                case "list":
                    for (SCHubFunction hub : getAllHubs()) Shell.println(hub.number+": "+hub);
                    break;
                case "select":
                    if (name == null) { Shell.println("Usage: "+synopsis); return true; }
                    SCHubFunction hub = getIdentifiedHub(name);
                    if (hub != null) {
                        selectedHub = hub;
                        Shell.println("Selected " + hub.number + ": " + hub.name);
                    }
                    break;
                case "start":
                    hub = (name != null) ? getIdentifiedHub(name) : getSelectedOrDefaultHub();
                    if (hub != null) {
                        hub.start();
                        Shell.println("Started " + hub.number + ": " + hub.name);
                    }
                    break;
                case "stop":
                    hub = (name != null) ? getIdentifiedHub(name) : getSelectedOrDefaultHub();
                    if (hub != null) {
                        hub.stop();
                        Shell.println("Stopped " + hub.number + ": " + hub.name);
                    }
                    break;
                case "report":
                    hub = (name != null) ? getIdentifiedHub(name) : getSelectedOrDefaultHub();
                    if (hub != null) hub.dump("");
                    break;
                default:
                    Shell.println("Usage: "+synopsis);
                    break;
            }
            return true;
        }
    }

    public static class NodeCommand extends Command {
        public NodeCommand() { super("node", "node list|select|start|stop|report [<number>]",
                "The 'node' command performs actions on the node in a BACnet/SC datalink.",
                "If there is more than one node in the application (e.g., more than one SC datalink and/or",
                "more than one device), the the optional <number> argument will indicate which one to operate on",
                "if not previously selected with the 'select' option.",
                "OPTIONS",
                "'node list'            show the numbers of all the active nodes in the application.",
                "'node select <number>' select the node number for future operations like 'node stop'.",
                "'node start'           start the node.",
                "'node stop'            stop the node.",
                "'node report'          report the status of the node.",
                "'... <number>'         override any previous selection and act on the specified node number."); }
        @Override public boolean execute(String[] words) {
            if (words.length < 2) { Shell.println("Usage: "+synopsis); return true; }
            String action = words[1];
            String name = words.length>2? words[2]:null;
            switch (action) {
                case "list":
                    for (SCNode node : getAllNodes()) Shell.println(node.number+": "+node);
                    break;
                case "select":
                    if (name == null) { Shell.println("Usage: "+synopsis); return true; }
                    SCNode node = getIdentifiedNode(name);
                    if (node != null) {
                        selectedNode = node;
                        Shell.println("Selected " + node.number + ": " + node.name);
                    }
                    break;
                case "start":
                    node = name != null ? getIdentifiedNode(name) : getSelectedOrDefaultNode();
                    if (node != null) {
                        node.start();
                        Shell.println("Started " + node.number + ": " + node.name);
                    }
                    break;
                case "stop":
                    node = name != null ? getIdentifiedNode(name) : getSelectedOrDefaultNode();
                    if (node != null) {
                        node.stop();
                        Shell.println("Stopped " + node.number + ": " + node.name);
                    }
                    break;
                case "report":
                    node = name != null ? getIdentifiedNode(name) : getSelectedOrDefaultNode();
                    if (node != null) node.dump("");
                    break;
                default:
                    Shell.println("Usage: "+synopsis);
                    break;

            }
            return true;
        }
    }

    public static class ConnCommand extends Command {
        public ConnCommand() { super("conn", "conn list|select|close|disconnect|report [<name>|<number>]",
                "The 'conn' command performs actions on a BACnet/SC connections.",
                "The optional <number> or <name> argument will indicate which connection to operate on",
                "if not previously selected with the 'select' option.",
                "OPTIONS",
                "'conn list'            show the numbers of all the active connections in the application.",
                "'conn select <number>' select the connection number for future operations like 'conn close'.",
                "'conn close'           abruptly closes the WebSocket connection.",
                "'conn disconnect'      initiates the disconnect sequence on the connection.",
                "'conn report'          report the status of the connection.",
                "'... <name>'           override any previous selection and act on the named connection.",
                "'... <number>'         override any previous selection and act on the numbered connection."); }
        @Override public boolean execute(String[] words) {
            if (words.length < 2) { Shell.println("Usage: "+synopsis); return true; }
            String action = words[1];
            String name = words.length>2? words[2]:null;
            switch (action) {
                case "list":
                    // we don't just use getAllConnections() here because the description is different for each type
                    for (Device device : Device.devices) {
                        for (Datalink datalink : device.networkLayer.datalinks) {
                            if (datalink instanceof SCDatalink) {
                                SCNode node = ((SCDatalink) datalink).node;
                                SCConnection hubConnection = node.hubConnector.getActiveConnection();
                                if (hubConnection != null) {
                                    Shell.println(hubConnection.number + ": " + hubConnection.properties.vmac + "<>" + hubConnection.peerVMAC + " " + hubConnection.name + " initiated from HC of " + datalink.getName() + " in " + device.deviceObject.objectName);
                                }
                                SCHubFunction hubFunction = node.hubFunction;
                                if (hubFunction != null) {
                                    for (SCConnection connection : hubFunction.connections) {
                                        Shell.println(connection.number + ": " + connection.properties.vmac + "<>" + connection.peerVMAC + " " + connection.name + " accepted by HF of " + datalink.getName() + " in " + device.deviceObject.objectName);
                                    }
                                }
                                SCNodeSwitch nodeSwitch = node.nodeSwitch;
                                if (nodeSwitch != null) {
                                    for (SCDirectConnector directConnector : nodeSwitch.connectors) {
                                        SCConnection connection = directConnector.connection;
                                        if (connection != null && connection.isConnected()) {
                                            Shell.println(connection.number + ": " + connection.properties.vmac + "<>" + connection.peerVMAC + " " + connection.name + " initiated from NS of " + datalink.getName() + " in " + device.deviceObject.objectName);
                                        }
                                    }
                                    for (SCConnection connection : nodeSwitch.connections) {
                                        Shell.println(connection.number + ": " + connection.properties.vmac + "<>" + connection.peerVMAC + " " + connection.name + " accepted by NS of " + datalink.getName() + " in " + device.deviceObject.objectName);
                                    }
                                }
                            }
                        }
                    }
                    break;
                case "select": {
                    SCConnection connection = getIdentifiedConnection(name);
                    if (connection != null) {
                        selectedConnection = connection;
                        Shell.println("Selected " + connection.number + ": " + connection.name);
                    }
                    break;
                }
                case "close": {
                    SCConnection connection = name != null ? getIdentifiedConnection(name) : getSelectedOrDefaultConnection();
                    if (connection != null) {
                        connection.close();
                        Shell.println("Closed " + connection.number + ": " + connection.name);
                    }
                    break;
                }
                case "disconnect": {
                    SCConnection connection = name != null ? getIdentifiedConnection(name) : getSelectedOrDefaultConnection();
                    if (connection != null) {
                        connection.disconnect();
                        Shell.println("Disconnected " + connection.number + ": " + connection.name);
                    }
                    break;
                }
                case "report": {
                    SCConnection connection = name != null ? getIdentifiedConnection(name) : getSelectedOrDefaultConnection();
                    if (connection != null) connection.dump("");
                    break;
                }
                default:
                    Shell.println("Usage: "+synopsis);
                    break;
            }
            return true;
        }
    }

     public static class ASCommand extends Command {
        public ASCommand() { super("as", "as <vmac>",
                "Initiates Advertisement-Solicitation and waits for an answer.",
                "The node to originate from is selected with 'node select' or defaults to first available node.",
                "OPTIONS",
                "'as <vmac>'  sends the request to the specified vmac and waits for an answer."); }
        @Override public boolean execute(String[] words) {
            if (words.length < 2) { Shell.println("Usage: "+synopsis); return true; }
            try {
                SCVMAC vmac = new SCVMAC(Formatting.fromHex(words[1]));
                SCNode node = getSelectedOrDefaultNode();
                if (node == null) { Shell.println("There is no selected or default node to use"); return true; }
                Shell.println("Sending AS from node " + node.number + ": " + node.name);
                node.setTap(advertisementTap);
                node.sendAdvertisementSolicitation(vmac);
                return false;
            } catch (Throwable ignore) {
                Shell.println("Usage: " + synopsis);
                return true;
            }
        }
        @Override public void cancel() {
            SCNode node = getSelectedOrDefaultNode();
            if (node != null) node.clearTap();
        }
        private static final SCNode.Tap advertisementTap = new SCNode.Tap() {
            @Override public void advertisement(SCConnection connection, SCMessage message, SCPayloadAdvertisement advertisement) {
                complete("Received ACK: "+advertisement);
            }
            @Override public void bvlcResult(SCConnection connection, SCMessage message, SCPayloadBVLCResult result) {
                complete("Received NAK: "+result.toString());
            }
            private void complete(String s) {
                Shell.println(s);
                SCNode node = getSelectedOrDefaultNode();
                if (node != null) node.clearTap();
                Shell.commandDone();
            }
        };
    }

    public static class ARCommand extends Command {
        public ARCommand() { super("ar", "ar <vmac>|list ",
                "Initiates Advertisement-Solicitation and waits for an answer.",
                "The node to originate from is selected with 'node select' or defaults to first available node.",
                "OPTIONS",
                "'ar <vmac>'  sends the request to the specified vmac and waits for an answer.",
                "'ar list'    lists the resolutions that are already known to the node.There is no default for vmac."); }
        @Override public boolean execute(String[] words) {
            if (words.length < 2) { Shell.println("Usage: "+synopsis); return true; }
            if (words[1].equals("list")) {
                for (Datalink datalink : BACnetCommands.getSelectedDevice().networkLayer.datalinks) {
                    if (datalink instanceof SCDatalink) {
                        for (SCAddressResolution resolution : ((SCDatalink)datalink).node.getAddressResolutions()) {
                            Shell.println(resolution.toString());
                        }
                    }
                }
            }
            else {
                try {
                    SCVMAC vmac = new SCVMAC(Formatting.fromHex(words[1]));
                    SCNode node = getSelectedOrDefaultNode();
                    if (node == null) { Shell.println("There is no selected or default node to use"); return true; }
                    Shell.println("Sending AR from node " + node.number + ": " + node.name);
                    node.setTap(resolutionTap);
                    node.sendAddressResolution(vmac);
                    return false;
                } catch (Throwable ignore) {
                    Shell.println("Usage: " + synopsis);
                }
            }
            return true;
        }
        @Override public void cancel() {
            SCNode node = getSelectedOrDefaultNode();
            if (node != null) node.clearTap();
        }
        private static final SCNode.Tap resolutionTap = new SCNode.Tap() {
            @Override public void addressResolutionAck(SCConnection connection, SCMessage message, SCPayloadAddressResolutionAck ack) {
                complete("Received ACK: "+ack);
            }
            @Override public void bvlcResult(SCConnection connection, SCMessage message, SCPayloadBVLCResult result) {
                complete("Received NAK: "+result);
            }
            private void complete(String s) {
                Shell.println(s);
                SCNode node = getSelectedOrDefaultNode();
                if (node != null) node.clearTap();
                Shell.commandDone();
            }
        };
    }

    public static class HBCommand extends Command {
        public HBCommand() { super("hb", "hb [<number>|<name>]",
                "Initiates Heartbeat on a connection.",
                "OPTIONS",
                "'hb'          initiates heartbeat request on previously selected connection (e.g. 'conn select xxx').",
                "              if no connection has been previously selected, the first node's hub connector is used.",
                "'hb <number>' initiates heartbeat request on numbered connection (from 'conn list')",
                "'hb <name>'   initiates heartbeat request on named (from 'conn list')" ); }
        @Override public boolean execute(String[] words) {
            String name = (words.length>1) ? words[1] : null;
            SCConnection connection = name != null? getIdentifiedConnection(name) : getSelectedOrDefaultConnection();
            if (connection != null) {
                connection.initiateHeartbeat();
                Shell.println("Heartbeat initiated on " + connection.number + ": " + connection.name);

            }
            return true;
        }
    }

    public static class DCCommand extends Command {
        public DCCommand() { super("dc", "dc connect <vmac> | disconnect <vmac> | report",
                "Works with BACnet/SC Direct Connections.",
                "The node to originate connections from can be selected with the 'node select' command",
                "and defaults to the node in the first SC datalink of the first device.",
                "OPTIONS",
                "'dc connect <vmac>'       attempts to resolve addresses for <vmac> then initiates DC to that node.",
                "'dc connect <vmac> <url>' initiates DC to the <url> without attempting address resolution",
                "'dc force <vmac>'         forces connection even if one already exists.",
                "'dc force <vmac> <url>'   forces connection to <url> even if one already exists.",
                "'dc disconnect <vmac>'    disconnects a previously made connection to <vmac> (keeps connector).",
                "'dc delete <vmac>'        deletes the connector for <vmac>.",
                "'dc report'               reports the status of all the connection in the selected node switch."
        ); }
        @Override public boolean execute(String[] words) {
            if (words.length < 2) { Shell.println("Usage: "+synopsis); return true; }
            switch (words[1]) {
                case "connect":
                case "force":
                    if (words.length >= 3) try {
                        byte[] vmac = Formatting.fromHex(words[2]);
                        String[] urls = (words.length == 4)? new String[]{words[3]} : null;
                        SCNode node = getSelectedOrDefaultNode();
                        if (node == null)  { Shell.println("No SCNode found");  return true; }
                        SCNodeSwitch nodeSwitch = node.nodeSwitch;
                        if (nodeSwitch == null) { Shell.println("No SCNodeSwitch found");  return true; }
                        nodeSwitch.establishDirectConnection(new SCVMAC(vmac),words[1].equals("force"),urls);
                        Shell.println("DC connection initiated...");
                        // TODO change this to false and wait for connection to complete and print results to the shell
                        return true;
                    } catch(Throwable ignore){}
                    Shell.println("Usage: "+synopsis);
                    return true;
                case "disconnect":
                    if (words.length == 3) try {
                        byte[] vmac = Formatting.fromHex(words[2]);
                        SCNode node = getSelectedOrDefaultNode();
                        if (node == null)  { Shell.println("No SCNode found");  return true; }
                        SCNodeSwitch nodeSwitch = node.nodeSwitch;
                        if (nodeSwitch == null) { Shell.println("No SCNodeSwitch found");  return true; }
                        nodeSwitch.disconnectDirectConnection(new SCVMAC(vmac));
                        Shell.println("DC disconnection initiated...");
                        return true;
                    } catch(Throwable ignore){}
                    Shell.println("Usage: "+synopsis);
                    return true;
                case "delete":
                    if (words.length == 3) try {
                        byte[] vmac = Formatting.fromHex(words[2]);
                        SCNode node = getSelectedOrDefaultNode();
                        if (node == null)  { Shell.println("No SCNode found");  return true; }
                        SCNodeSwitch nodeSwitch = node.nodeSwitch;
                        if (nodeSwitch == null) { Shell.println("No SCNodeSwitch found");  return true; }
                        nodeSwitch.deleteDirectConnection(new SCVMAC(vmac));
                        return true;
                    } catch(Throwable ignore){}
                    Shell.println("Usage: "+synopsis);
                    return true;
                case "report":
                    Shell.println("Node Switches:");
                    for (Datalink datalink : BACnetCommands.getSelectedDevice().networkLayer.datalinks) {
                        if (datalink instanceof SCDatalink) {
                            SCNodeSwitch swtch =((SCDatalink)datalink).node.nodeSwitch;
                            if (swtch != null) swtch.dump("");
                        }
                    }
                    return true;
                default:
                    Shell.println("Usage: "+synopsis);
                    return true;
            }
        }
    }

    public static class HCCommand extends Command {
        public HCCommand() { super("hc", "hc start | stop | restart | report",
                "Works with BACnet/SC Hub Connector.",
                "The node to originate connections from can be selected with the 'node select' command",
                "and defaults to the node in the first SC datalink of the first device.",
                "OPTIONS",
                "'hc stop'        stops the hub connector (disconnects and doesn't retry).",
                "'hc start'       starts the hub connector's normal primary/failover behavior",
                "'hc restart'     abandons existing connectios and starts again (for negative tests)",
                "'hc report'      reports the status of the hub connection in the selected node."
        ); }
        @Override public boolean execute(String[] words) {
            if (words.length < 2) { Shell.println("Usage: "+synopsis); return true; }
            SCNode node = getSelectedOrDefaultNode();
            if (node == null)  { Shell.println("No SCNode found");  return true; }
            SCHubConnector hubConnector = node.hubConnector;
            if (hubConnector == null) { Shell.println("No SCHubConnector found");  return true; }
            switch (words[1]) {
                case "start":
                    hubConnector.start();
                    return true;
                case "stop":
                    hubConnector.stop();
                    return true;
                case "restart":
                    hubConnector.restart();
                    return true;
                case "report":
                    hubConnector.dump("");
                    return true;
                default:
                    Shell.println("Usage: "+synopsis);
                    return true;
            }
        }
    }

    public static class InjectCommand extends Command {
        public InjectCommand() { super("inject", "inject list|clear|<id> [<conn-num> [<function>]]",
                "Injects anomalous behavior into BACnet/SC communications.",
                "The injection identifiers follow a naming convention of direction-thing-action.",
                "e.g., 'i-cr-nak' is incoming-ConnectionRequest-NAK. and o-ha-drop is outgoing-heartbeat-drop.",
                "Some identifiers take arguments with parentheses, e.g., 'o-d-pa(DEADBEEF1234)'.",
                "You create anomalous behavior by using the inject command and then causing, or waiting for, a message",
                "that will trigger the action. After the injected action has been triggered, it will be cleared. There",
                "are no 'permanent' injections. However, some injections can lie in wait for a while, especially if",
                "given the optional filters for connection number and message function. The message function filter",
                "obviously only makes sense for messages that don't operate on a specific message function already.",
                "Most injections are 'outbound', meaning the next message that matches the trigger will have the defect.",
                "For these, you can either set the trap to modify response messages, or take an active role by setting",
                "the injection and doing something to create an outbound message, e.g., 'hb', 'as', 'dc connect',",
                "or even 'conn disconnect'.  Error injections work at a very low level and will wait for an appropriate",
                "trigger no matter what state anything else is in, so defects can be injected during node/hub/datalink",
                "stop and start cycles.",
                "OPTIONS",
                "'inject list'   lists available injections and their descriptions.",
                "'inject clear'  clears/disarms any pending injections",
                "'inject <id>'   arms the identified injection",
                "'inject <id> <conn-num>'  arms <id> only for the identified connection number",
                "'inject <id> <conn-num> <function>'  arms <id> only for connection number and message function code");
        }
        @Override public boolean execute(String[] words) {
            if (words.length < 2) { Shell.println("Usage: "+synopsis); return true; }
            String id = words[1];
            if (id.equals("list")) { for (String s :SCErrorInjection.injections) Shell.println(s); return true; }
            if (id.equals("clear")) { SCErrorInjection.reset(); return true; }
            int    connectionNumber = -1;
            int    functionCode     = -1;
            try {
                if (words.length > 2) connectionNumber = Integer.parseUnsignedInt(words[2]);
                if (words.length > 3) functionCode = Integer.parseUnsignedInt(words[3]);
            }
            catch (NumberFormatException e) { Shell.println("Usage: "+synopsis); return true; }

            String injectionType = Formatting.splitBefore(id,"("); // get just the root of the id; i.e., strip "(..." args if present
            String injectionData = null; // for arguments, we just get the blob between the parens. someone else will parse further if needed.
            if (id.contains("(")) try { injectionData = Formatting.splitBetween(id,"(",")"); }
            catch (Throwable t) { Shell.println("Injection: malformed argument list (bad parentheses)"); return true; }

            try { SCErrorInjection.setInjection(injectionType,injectionData,connectionNumber,functionCode); }
            catch (Exception e) { Shell.println("Error: "+e.getLocalizedMessage()); Shell.println("Use \"inject list\" for possibilities"); return true; }

            return true;
        }
    }


    ///////// CONNECTION HELPERS ///////////

    private static SCConnection getIdentifiedConnection(String identifier) {
        int number = -1;
        try { number = Integer.parseUnsignedInt(identifier); } catch (NumberFormatException ignore){}
        for (SCConnection connection : getAllConnections()) {
            if (number == -1 && connection.name.equals(identifier) || connection.number == number) {
                return connection;
            }
        }
        Shell.println("Connection " + identifier + " not found.  Use 'conn list' for listing");
        return null;
    }

    private static SCConnection getSelectedOrDefaultConnection() {
        if (selectedConnection != null) return selectedConnection;
        SCConnection connection = getDefaultConnection();
        if (connection == null) Shell.println("No Selected or default connection found. Use 'conn list' for listing");
        return connection;
    }

    private static SCConnection getDefaultConnection() {
        // the default is the hub connector of the first node of the selected device (which defaults to the first device)
        for (Datalink datalink : BACnetCommands.getSelectedDevice().networkLayer.datalinks) {
            if (datalink instanceof SCDatalink) {
                selectedConnection = ((SCDatalink)datalink).node.hubConnector.getActiveConnection();
                return selectedConnection;// could be null if not actively connected
            }
        }
        return null;
    }

    public static List<SCConnection> getAllConnections() {
        List<SCConnection> results = new ArrayList<>();
        for (Device device : Device.devices) {
            for (Datalink datalink : device.networkLayer.datalinks) {
                if (datalink instanceof SCDatalink) {
                    SCNode node = ((SCDatalink)datalink).node;
                    SCConnection hubConnection = node.hubConnector.getActiveConnection();
                    if (hubConnection != null)  results.add(hubConnection);
                    SCHubFunction hubFunction = node.hubFunction;
                    if (hubFunction != null) results.addAll(hubFunction.connections);
                    SCNodeSwitch nodeSwitch = node.nodeSwitch;
                    if (nodeSwitch != null) {
                        for (SCDirectConnector directConnector : nodeSwitch.connectors) {
                            SCConnection connection = directConnector.connection;
                            if (connection != null && connection.isConnected()) results.add(connection);
                        }
                        results.addAll(nodeSwitch.connections);
                    }
                }
            }
        }
        return results;
    }

    ////////////// NODE HELPERS ///////////////

    private static SCNode getIdentifiedNode(String identifier) {
        int number = -1;
        try { number = Integer.parseUnsignedInt(identifier); } catch (NumberFormatException ignore){}
        for (SCNode node : getAllNodes()) {
            if (number == -1 && node.name.equals(identifier) || node.number == number) {
                return node;
            }
        }
        Shell.println("Node " + identifier + " not found.  Use 'node list' for listing");
        return null;
    }

    private static SCNode getSelectedOrDefaultNode() {
        if (selectedNode != null) return selectedNode;
        SCNode node = getDefaultNode();
        if (node == null) Shell.println("No Selected or default node found. Use 'node list' for listing");
        return node;
    }

    private static SCNode getDefaultNode() {
        // the default is the first node of the selected device (which defaults to the first device)
        for (Datalink datalink : BACnetCommands.getSelectedDevice().networkLayer.datalinks) {
            if (datalink instanceof SCDatalink) {
                return ((SCDatalink)datalink).node;
            }
        }
        return null;
    }

    public static List<SCNode> getAllNodes() {
        List<SCNode> results = new ArrayList<>();
        for (Device device : Device.devices) {
            for (Datalink datalink : device.networkLayer.datalinks) {
                if (datalink instanceof SCDatalink) {
                    results.add(((SCDatalink)datalink).node);
                }
            }
        }
        return results;
    }

    ////////////// HUB HELPERS ///////////////

    private static SCHubFunction getIdentifiedHub(String identifier) {
        int number = -1;
        try { number = Integer.parseUnsignedInt(identifier); } catch (NumberFormatException ignore){}
        for (SCHubFunction hub : getAllHubs()) {
            if (number == -1 && hub.name.equals(identifier) || hub.number == number) {
                return hub;
            }
        }
        Shell.println("Hub " + identifier + " not found.  Use 'hub list' for listing");
        return null;
    }

    private static SCHubFunction getSelectedOrDefaultHub() {
        if (selectedHub != null) return selectedHub;
        SCHubFunction hub = getDefaultHub();
        if (hub == null) Shell.println("No Selected or default hub found. Use 'hub list' for listing");
        return hub;
    }

    private static SCHubFunction getDefaultHub() {
        for (Device device : Device.devices) {
            for (Datalink datalink : device.networkLayer.datalinks) {
                if (datalink instanceof SCDatalink) {
                    SCHubFunction hub = ((SCDatalink) datalink).node.hubFunction;
                    if (hub != null) return hub;
                }
            }
        }
        return null;
    }

    public static List<SCHubFunction> getAllHubs() {
        List<SCHubFunction> results = new ArrayList<>();
        for (Device device : Device.devices) {
            for (Datalink datalink : device.networkLayer.datalinks) {
                if (datalink instanceof SCDatalink) {
                    SCHubFunction hub = ((SCDatalink)datalink).node.hubFunction;
                    if (hub != null) results.add(hub);
                }
            }
        }
        return results;
    }

}
