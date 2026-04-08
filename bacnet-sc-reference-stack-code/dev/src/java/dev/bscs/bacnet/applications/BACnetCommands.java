// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.applications;

import dev.bscs.bacnet.stack.*;
import dev.bscs.bacnet.stack.constants.DCCMode;
import dev.bscs.bacnet.stack.data.BACnetPropertyIdentifier;
import dev.bscs.bacnet.stack.data.base.*;
import dev.bscs.bacnet.stack.objects.DeviceObject;
import dev.bscs.bacnet.stack.objects.DeviceObjectProperties;
import dev.bscs.bacnet.stack.services.ReadPropertyClient;
import dev.bscs.bacnet.stack.services.WhoHasClient;
import dev.bscs.bacnet.stack.services.WhoIsClient;
import dev.bscs.bacnet.stack.services.WritePropertyClient;
import dev.bscs.common.*;

import java.util.List;
import java.util.Map;

/**
 * A collection of common BACnet-related commands and their associated user-selected variables.
 * Each command documents itself.  See {@link Command} for info on writing new commands.
 *
 * Some of these commands share a "selected" context, so that commands like "target" and "device"
 * set the context for other commands to use. This context is stored in several static variables in this class.
 * Since there is only one Terminal window (for now), there is only one interactive user and this is not a problem.
 *
 * @author drobin
 */

public class BACnetCommands {

    static Log log = new Log(BACnetCommands.class);

    public static int     targetDnet       = -1;
    public static byte[]  targetDadr       = null;
    public static int     targetObjectID   = BACnetObjectIdentifier.combine(8,0x3FFFFF);
    public static int     targetPropertyID = BACnetPropertyIdentifier.OBJECT_NAME;
    public static int     targetIndex      = -1;
    public static Device  selectedDevice;  // active device; defaults to first device; selected with "dev" command
    public static boolean expectingMoreThanOneAnswer; // if we are only expecting one answer, then we will release the shell as soon as we get a single answer

    /////////////////////////////////////////////////////////////////////
    ///////////// Process common BACnet manual commands  ////////////////
    /////////////////////////////////////////////////////////////////////

    private BACnetCommands() {} // no constructor, static only

    public static void addCommands() {
        CommandProcessor.addCommand(new DeviceCommand());
        CommandProcessor.addCommand(new TargetCommand());
        CommandProcessor.addCommand(new RPCommand());
        CommandProcessor.addCommand(new WPCommand());
        CommandProcessor.addCommand(new WICommand());
        CommandProcessor.addCommand(new WHCommand());
        CommandProcessor.addCommand(new BindingsCommand());
        CommandProcessor.addCommand(new RoutesCommand());
        CommandProcessor.addCommand(new DatalinkCommand());
        CommandProcessor.addCommand(new IRTQCommand());
        CommandProcessor.addCommand(new WIRTNCommand());
    }

    public static Device getSelectedDevice() {
        return selectedDevice != null? selectedDevice : Device.getDefaultDevice();
    }

    public static class DeviceCommand extends Command {
        public DeviceCommand() { super("device", "device list|report|select [<number>]",
                "This is only for applications that have more than one device.",
                "It allows you to list and select a device that will be the host for future commands.",
                "OPTIONS",
                "'device list'  will display a list of devices and their selection numbers.",
                "'device select <number>'  will select that device for all future operations.",
                "'device report'  general info about the device  "); }
        @Override public boolean execute(String[] words) {
            if (words.length < 2) { Shell.println("Usage: "+synopsis); return true; }
            String action = words[1];
            String number = words.length>2 ? words[2] : null;
            switch (action) {
                case "list":
                    for (int i=0; i< Device.devices.size(); i++) Shell.println((i+1)+": name="+Device.devices.get(i).deviceObject.objectName+" inst="+Device.devices.get(i).deviceObject.properties.instance);
                    return true;
                case "select":
                    if (number == null) { Shell.println("'select' must be given a number");return true; }
                    int ordinal = Formatting.parseInteger(number,-1);
                    if (ordinal == 0) { Shell.println("Device numbers start at 1"); return true; }
                    if (ordinal == -1) { Shell.println("'select' must be given a number"); return true; }
                    Device found = Device.devices.get(ordinal-1);
                    if (found != null) selectedDevice = found;
                    else Shell.println("device "+number+" not found");
                    return true;
                case "report":
                    Device device = getSelectedDevice();
                    DeviceObject deviceObject = device.deviceObject;
                    DeviceObjectProperties deviceProperties = deviceObject.properties;
                    ApplicationLayer applicationLayer = device.applicationLayer;
                    NetworkLayer networkLayer = device.networkLayer;
                    int dccMode = applicationLayer.getDCCMode();
                    Shell.println("instance: "+deviceProperties.instance +
                            ", name: \""+deviceObject.objectName +"\""+
                            ", vendor: \""+deviceProperties.vendorName + "\"("+getSelectedDevice().deviceObject.properties.vendorIdentifier+")" +
                            ", model: \""+deviceProperties.modelName+"\"");
                    Shell.println("fwrev: \""+deviceProperties.firmwareRevision +"\""+
                            ", dcc: "+(dccMode== DCCMode.DISABLE?"DISABLE":dccMode== DCCMode.DISABLE_INITIATION ?"DISABLE_INIT":"ENABLE") +
                            ", datalinks: "+networkLayer.datalinks.size() +
                            ", bindings: "+applicationLayer.getAllBindings().size() +
                            ", routes: "+networkLayer.routes.size() );
                    break;
                default:
                    Shell.println("Usage: "+synopsis);
            }
            return true;
        }
    }

    public static class WICommand extends Command {
        public WICommand() { super("wi", "wi (<instance>|<low>-<high>|all) [<dnet>[<dadr>]]",
                "Initiates a Who-Is either globally or on a specific network.",
                "If given a range or 'all', it will wait for more than one answer.",
                "OPTIONS",
                "wi <instance>     will ask for a single instance and when response is received, will end command.",
                "wi <low>-<high>   will ask for the specified range and wait for answers.",
                "wi all            will ask for the unspecified range(!) wait for answers.",
                "... <dnet>        added to the end will make a directed broadcast on the specified network.",
                "... <dnet> <dadr> added to the end will make a unicast to dnet:dadr (dnet can be 0 for local network)"
                ); }
        @Override public boolean execute(String[] words) {
            expectingMoreThanOneAnswer = true; // assume this except for exceptions below
            if (words.length < 2) { Shell.println("Usage: "+synopsis); return true; }
            try {
                String specifier = words[1]; // either <type>,<instance> or <name>
                int    dnet = (words.length > 2)? Integer.parseUnsignedInt(words[2]) : 65535;
                byte[] dadr = (words.length > 3)? Formatting.fromMacString(words[3]) : null;
                if (specifier.equals("all")) {
                    whoIsClient.request(getSelectedDevice(), dnet, dadr);
                }
                else if (specifier.contains("-")) {
                    String[] lowhigh = specifier.split("-");
                    whoIsClient.request(getSelectedDevice(), dnet, dadr, Integer.parseUnsignedInt(lowhigh[0]), Integer.parseUnsignedInt(lowhigh[1]));
                }
                else {
                    int instance = Integer.parseUnsignedInt(specifier);
                    expectingMoreThanOneAnswer = false;
                    whoIsClient.request(getSelectedDevice(), dnet, dadr, instance, instance);
                }
                return false; // we are waiting for answers, so return false to keep shell prompt at bay
            }
            catch (NumberFormatException e) { // array index out of bounds, number format, etc.
                Shell.println("Usage: "+synopsis);
            }
            catch (Exception ee) {
                Shell.println("Unexpected exception: "+ee);
            }
            return true;
        }
        @Override public void cancel() {
            log.debug("wi command cancelled");
            whoIsClient.cancel(getSelectedDevice());
        }
        private static WhoIsClient whoIsClient = new WhoIsClient() {
            @Override protected void success(Device device, Binding binding, AuthData auth) {
                Shell.println("Received I-Am "+binding);
                if (!expectingMoreThanOneAnswer) Shell.commandDone(); // keep the shell open only if we are expecting more then one answer
            }
        };
    }

    public static class WHCommand extends Command {
        public WHCommand() { super("wh", "wh <name>|<type>,<inst>  [<low>-<high>|all [<dnet>]]",
                "Initiates a Who-Has either globally or on a specific network.",
                "It will always wait for more than one answer.",
                "OPTIONS",
                "wh <name>        will ask for an object by name.",
                "wh <type>,<inst> will ask for the specified type (numeric) and instance.",
                "... <low>-<high> will restrict to a device instance range.",
                "... all          will be unrestricted (the default)",
                "... ... <dnet>   added after range or 'all' will make a directed broadcast on the specified network."
        ); }
        @Override public boolean execute(String[] words) {
            if (words.length < 2) { Shell.println("Usage: "+synopsis); return true; }
            try {
                String specifier = words[1]; // either <type>,<instance> or <name>
                String range = (words.length > 2)? words[2] : "all";  // <low>-<high> or all
                int    dnet  = (words.length > 3)? Integer.parseUnsignedInt(words[3]) : 65535;
                // now figure out what we were given, starting everything as "unknown"
                int    objectID = -1;
                String objectName = null;
                int    low = -1;
                int    high = -1;
                if (specifier.contains(",")) objectID = BACnetObjectIdentifier.parse(specifier);
                else objectName = specifier;
                if (!range.equals("all")) {
                    String[] lowhigh = range.split("-");
                    low = Integer.parseUnsignedInt(lowhigh[0]);
                    high = Integer.parseUnsignedInt(lowhigh[1]);
                }
                whoHasClient.request(getSelectedDevice(),dnet,new byte[0],objectID,objectName,low,high);
                expectingMoreThanOneAnswer = true;
                return false; // we are waiting for answers, so return false to keep shell prompt at bay
            }
            catch (Exception e) { // array index out of bounds, number format, etc.
                Shell.println("Usage: "+synopsis);
            }
            return true;
        }
        @Override public void cancel() {
            log.debug("wh command cancelled");
            whoHasClient.cancel(getSelectedDevice());
        }
        private static WhoHasClient whoHasClient = new WhoHasClient() {
            @Override protected void success(Device device, int deviceID, int objectID, String objectName, AuthData auth) {
                Shell.println("Received I-Have: dev="+BACnetObjectIdentifier.toString(deviceID)+" obj="+BACnetObjectIdentifier.toString(objectID)+" name="+objectName);
                if (!expectingMoreThanOneAnswer) Shell.commandDone(); // keep the shell open if we are expecting more then one answer
            }
        };
    }

    public static class TargetCommand extends Command {
        public TargetCommand() { super("target", "target [<net>:]<mac> [<type>,<instance> [<property> [<index>]]]",
                "Sets the target for future BACnet services, like the 'rp' ReadProperty command.",
                "For the address, the leading network number and colon are optional and will default to 0.",
                "The mac format is flexible:",
                "   d            one decimal number will become one octet.",
                "   hhhhhhhhhhhh 12 hex digits will become 6 octets.",
                "   0xhh...      variable length hex will become 6 octets filled from low end.",
                "   d.d.d.d      will be parsed as an IP address with port 47808",
                "   d.d.d.d:d    will be parsed as an IP address and port",
                "The optional <type>,<instance> defaults to the Device type with wildcard instance.",
                "The optional <property> and <index> default to Object_Name with no index.",
                "At the moment, <type> and <property> are numeric only."
        ); }
        @Override public boolean execute(String[] words) {
            if (words.length < 2) { Shell.println("Usage: "+synopsis); return true; }
            try {
                String address = words[1];
                String parts[] = address.split(":");
                if (parts.length == 1) { // no colon, so no network, just a number, like nn or 0x...
                    targetDnet = 0;
                    targetDadr = Formatting.fromMacString(address);
                }
                else if (parts.length == 2) { // either net:number or n.n.n.n:p
                    if (parts[0].contains(".")) { // n.n.n.n:p
                        targetDnet = 0;
                        targetDadr = Formatting.fromMacString(address);
                    }
                    else { // net:number
                        targetDnet = Integer.parseUnsignedInt(parts[0]);
                        targetDadr = Formatting.fromMacString(parts[1]);
                    }
                }
                else if (parts.length == 3) { // net:n.n.n.n:p
                    targetDnet = Integer.parseUnsignedInt(parts[0]);
                    targetDadr = Formatting.fromMacString(parts[1]+":"+parts[2]);
                }
                else { Shell.println("Usage: "+synopsis); return true; }
                if (words.length>2) targetObjectID   = BACnetObjectIdentifier.parse(words[2]);
                else                targetObjectID   = BACnetObjectIdentifier.combine(8,0x3FFFFF);
                if (words.length>3) targetPropertyID = Integer.parseUnsignedInt(words[3]);
                else                targetPropertyID = BACnetPropertyIdentifier.OBJECT_NAME;
                if (words.length>4) targetIndex      = Integer.parseUnsignedInt(words[4]);
                else                targetIndex      = -1;
                Shell.println("Target set to "+
                        targetDnet+ ":0x"+Formatting.toHex(targetDadr)+" "+
                        BACnetObjectIdentifier.toString(targetObjectID)+" "+
                        targetPropertyID+(targetIndex!=-1?("["+targetIndex+"]"):""));
            }
            catch (Exception e) {
                Shell.println(e.toString());
                Shell.println("Usage: "+synopsis);
            }
            return true;
        }
    }

    public static class RPCommand extends Command {
        public RPCommand() { super("rp", "rp",
                "Reads the property specified by 'target' command.",
                "This will send the ReadProperty service request to the target device and wait for the answer."); }
        @Override public boolean execute(String[] words) {
            if (targetDnet == -1 || targetDadr == null) { Shell.println("No target specified, use 'target' command first"); return true; }
            if (readPropertyClient.request(getSelectedDevice(),targetDnet,targetDadr,targetObjectID,targetPropertyID,targetIndex)) {
                // normal exit, message sent, tell the shell to wait for results
                return false;
            }
            else {
                Shell.println("Failed to send: " + readPropertyClient.failure);
                return true;
            }
        }
        private static ReadPropertyClient readPropertyClient = new ReadPropertyClient() {
            @Override protected void  success(Device device, BACnetData value, AuthData auth) { complete("Result="+value+", auth="+auth); }
            @Override protected void  failure(Device device, Failure failure, AuthData auth)  { complete("Failure="+failure+", auth="+auth); }
            private void complete(String s) { Shell.println(s); Shell.commandDone(); }
        };
    }

    public static class WPCommand extends Command {
        public WPCommand() { super("wp", "wp <type> <data>",
                "Writes <data>, of primitive type <type>, to the property specified by 'target' command.",
                "<type> is one of \"n\", \"b\", \"u\", \"i\", \"r\", \"D\", \"os\", \"cs\", \"bs\", \"e\", \"d\", \"t\", \"oi\"",
                "Note that double, since it is rare, is a capital 'D' and date is lower case 'd'.",
                "Formats: b true  os 12AB  bs TFTF  e 42  d 2021-12-31  t 23:59:59.99  oi 8,1234",
                "This will send the WriteProperty service request to the target device and wait for the answer."); }
        @Override public boolean execute(String[] words) {
            if (targetDnet == -1 || targetDadr == null) { Shell.println("No target specified, use 'target' command first"); return true; }
            String type = words[1];
            if (words.length < 3 && !type.equals("n")) { Shell.println("No data specified"); return true; }
            String value = type.equals("n") ? null: words[2];
            BACnetData data;
            try {
                switch (type) {
                    case "n":  data = new BACnetNull(); break;
                    case "b":  data = new BACnetBoolean(value); break;
                    case "u":  data = new BACnetUnsigned(value); break;
                    case "i":  data = new BACnetInteger(value); break;
                    case "r":  data = new BACnetReal(value); break;
                    case "D":  data = new BACnetDouble(value); break;
                    case "os": data = new BACnetOctetString(value); break;
                    case "cs": data = new BACnetCharacterString(value); break;
                    case "bs": data = new BACnetBitString(value); break;
                    case "e":  data = new BACnetEnumerated(value); break;
                    case "d":  data = new BACnetDate(value); break;
                    case "t":  data = new BACnetTime(value); break;
                    case "oi": data = new BACnetObjectIdentifier(value); break;
                    default: { Shell.println("Invalid type specifier"); return true; }
                }
            }
            catch (Failure.Error e) { Shell.println("Invalid value for type: "+e.description); return true; }
            if (writePropertyClient.request(getSelectedDevice(),targetDnet,targetDadr,targetObjectID,targetPropertyID,targetIndex,data)) {
                // normal exit, message sent, tell the shell to wait for results
                return false;
            }
            else {
                Shell.println("Failed to send: " + writePropertyClient.failure);
                return true;
            }
        }
        private static WritePropertyClient writePropertyClient = new WritePropertyClient() {
            @Override protected void  success(Device device, AuthData auth) { complete("Success, auth="+auth); }
            @Override protected void  failure(Device device, Failure failure, AuthData auth)  { complete("Failure="+failure+", auth="+auth); }
            private void complete(String s) { Shell.println(s); Shell.commandDone(); }
        };
    }

    public static class BindingsCommand extends Command {
        public BindingsCommand() { super("bindings", "bindings all|requested|discovered|<instance>",
                "Show the bindings that the selected device has already learned.",
                "OPTIONS",
                "'bindings requested'  will show only bindings requested by code through wantDeviceAddress().",
                "'bindings discovered' will show only discovered bindings (e.g., discovered with the 'wi' command).",
                "'bindings all'        will show both requested and discovered.",
                "'bindings <instance>' will show only the specific instance, or 'not found'."); }
        @Override public boolean execute(String[] words) {
            if (words.length < 2) { Shell.println("Usage: "+synopsis); return true; }
            String scope = words[1];
            switch (scope) {
                case "requested":
                    Map<Integer,Binding> bindings = getSelectedDevice().applicationLayer.getRequestedBindings();
                    for (Integer key: bindings.keySet()) Shell.println(bindings.get(key).toString());
                    break;
                case "discovered":
                    bindings = getSelectedDevice().applicationLayer.getDiscoveredBindings();
                    for (Integer key: bindings.keySet()) Shell.println(bindings.get(key).toString());
                    break;
                case "all":
                    bindings = getSelectedDevice().applicationLayer.getAllBindings();
                    for (Integer key: bindings.keySet()) Shell.println(bindings.get(key).toString());
                    break;
                default:
                    int instance = Formatting.parseInteger(scope,-1);
                    if (instance != -1) {
                        Binding binding = getSelectedDevice().applicationLayer.getBinding(instance);
                        if (binding != null)  Shell.println(binding.toString());
                        else Shell.println("not found");
                    }
                    else Shell.println("Usage: "+synopsis);
                    break;
            }
            return true;
        }
    }

    public static class RoutesCommand extends Command {
        public RoutesCommand() { super("routes", "routes all|direct|discovered|<network>",
                "Show the routes that the selected device knows.",
                "OPTIONS",
                "'routes direct'     will show only direct networks (for routers)",
                "'routes discovered' will show only discovered bindings.",
                "'routes all'        will show both direct and discovered.",
                "'routes <network >' will show only the specific network number, or 'not found'."); }
        @Override public boolean execute(String[] words) {
            if (words.length < 2) { Shell.println("Usage: "+synopsis); return true; }
            String scope = words[1];
            Device device = getSelectedDevice();
            switch (scope) {
                case "direct":
                    if (!device.networkLayer.isRouter()) Shell.println("Device is not a router");
                    else { for (Datalink datalink : device.networkLayer.datalinks) Shell.println("Datalink "+datalink.getName()+" is network "+datalink.getNetwork()); }
                    break;
                case "discovered":
                    for (NetworkLayer.Route route : device.networkLayer.routes) {
                        Shell.println("Network "+route.network+ " via "+Formatting.toNetMac(route.datalink.getNetwork(),route.router) +
                                (route.unreachable? " is unreachable":"") + (route.busyTimer.remaining()!=0? " is busy for "+route.busyTimer.remaining():"") );
                    }
                    break;
                case "all":
                    if (!device.networkLayer.isRouter()) Shell.println("Device is not a router");
                    else { for (Datalink datalink : device.networkLayer.datalinks) Shell.println("Datalink "+datalink.getName()+" is network "+datalink.getNetwork()); }
                    for (NetworkLayer.Route route : device.networkLayer.routes) {
                        Shell.println("Network "+route.network+ " via "+Formatting.toNetMac(route.datalink.getNetwork(),route.router) +
                                (route.unreachable? " is unreachable":"") + (route.busyTimer.remaining()!=0? " is busy for "+route.busyTimer.remaining():"") );
                    }
                    break;
                default:
                    int network = Formatting.parseInteger(scope,-1);
                    if (network != -1) {
                        Datalink datalink = device.networkLayer.findDirectNetwork(network);
                        if (datalink != null) Shell.println("Directly available via datalink "+datalink.getName());
                        else {
                            NetworkLayer.Route route = device.networkLayer.findRemoteNetwork(network);
                            if (route != null) Shell.println("Network "+route.network+ " via router at "+Formatting.toNetMac(route.datalink.getNetwork(),route.router)+
                                    (route.unreachable? " is unreachable":"") + (route.busyTimer.remaining()!=0? " is busy for "+route.busyTimer.remaining():""));
                            else Shell.println("not found");
                        }
                    }
                    else Shell.println("Usage: "+synopsis);
                    break;
            }
            return true;
        }
    }

    public static class DatalinkCommand extends Command {
        public DatalinkCommand() { super("datalink", "datalink list|start|stop|report [<network>]",
                "The 'datalink' command performs actions on a BACnet datalink in the selected device.",
                "If there is more than one datalink in the device (e.g., for routers), then the optional <network>",
                "argument will select the datalink.",
                "OPTIONS",
                "'datalink list'    lists the datalinks in the device along with their network numbers.",
                "'datalink start'   starts the datalink.",
                "'datalink stop'    stops the datalink.",
                "'datalink report'  reports status of the datalink.",
                "'... [<network>]'  adding network on the end will select which datalink to operate on."
        ); }
        @Override public boolean execute(String[] words) {
            if (words.length < 2) { Shell.println("Usage: "+synopsis); return true; }
            String action = words[1];
            if (action.equals("list")) {
                for (Datalink datalink : getSelectedDevice().networkLayer.datalinks) {
                    Shell.println(datalink.getNetwork()+": "+datalink.getName());
                }
                return true;
            }
            int network = -1;
            if (words.length > 2) try {
                network = Integer.parseUnsignedInt(words[2]);
            } catch (NumberFormatException e) {
                Shell.println("<network> argument must be a number");
                return true;
            }
            List<Datalink> datalinks = getSelectedDevice().networkLayer.datalinks;
            if (datalinks.size() > 1 && network == -1) {
                Shell.println("There is more than one datalink in the selected device. Use the optional <network> argument to select");
                return true;
            }
            Datalink datalink = null;
            for (Datalink dl : datalinks) {
                if (dl.getNetwork() == network) {
                    datalink = dl;
                    break;
                }
            }
            if (datalink == null) {
                Shell.println("A datalink with network number "+network+" was not found in selected device.  Use 'datalink list'.");
                return true;
            }
            switch (action) {
                case "start":
                    datalink.start();
                    break;
                case "stop":
                    datalink.stop();
                    break;
                case "report":
                    datalink.dump("");
                    break;
                default:
                    Shell.println("Usage: "+synopsis);
                    break;
            }
            return true;
        }
    }

    private static class IRTQCommand extends Command {
        public IRTQCommand() { super("irtq","irtq","Initialize-Routing-Table Query - sends a query to target device");}
        @Override public boolean execute(String[] words) {
            NPDU npdu = new NPDU();
            npdu.messageType = NetworkLayer.INITIALIZE_ROUTING_TABLE;
            npdu.payload = new byte[]{0};
            if (targetDnet == -1 || targetDadr == null || targetDadr.length == 0) {
                Shell.println("'target' not set yet.");
                return true;
            }
            getSelectedDevice().networkLayer.sendNPDU(BACnetCommands.targetDnet,BACnetCommands.targetDadr,npdu,AuthData.makeSecurePath());
            return true;
        }
    }
    private static class WIRTNCommand extends Command {
        public WIRTNCommand() { super("wirtn","wirtn [<dnet>]","Who-Is-Router-To-Network test - sends a WIRTN out all datalinks of selected device");}
        @Override public boolean execute(String[] words) {
            try {
                int dnet = (words.length >1 )? Integer.parseUnsignedInt(words[1]) : 65535;
                NetworkLayer nl = getSelectedDevice().networkLayer;
                for (Datalink dl : nl.datalinks) nl.sendWhoIsRouterToNetwork(dl,dnet,0);
                // there is no tap into the network leyer at this point so you just have to look at the logs
            }
            catch (NumberFormatException e) { // array index out of bounds, number format, etc.
                Shell.println("Usage: "+synopsis);
            }
            return true;
        }
    }



}
