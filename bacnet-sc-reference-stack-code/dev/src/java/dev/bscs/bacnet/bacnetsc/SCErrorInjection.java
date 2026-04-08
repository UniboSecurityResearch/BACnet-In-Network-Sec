// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.bacnetsc;

import dev.bscs.bacnet.stack.constants.ErrorCode;
import dev.bscs.common.Formatting;
import dev.bscs.websocket.BSWebSocket;

/**
 * This class provides a mechanism to inject errors into incoming or outgoing messages for testing.
 * In a few strategic places in the general code, calls are made to hooks here to modify incoming or outgoing things.
 * Generally, the error to inject is specified with the "inject" manual command, and most(all?) are "one time" events.
 * There is only one error lying in wait at a time. Some modifications have "match" criteria that wait till the match
 * occurs. Others just operate on the *next* call to the hook. They clear themselves after triggering, so none are
 * intended to be persistent.
 * @author drobin
 */
public class SCErrorInjection {

    // all methods and vars are static

    private static final SCLog log = new SCLog(SCErrorInjection.class);

    public static final String[] injections = new String[] {
            //
            // NOTE: the format here is not arbitrary.  This is parsed by the "inject" command, q.v.
            //
            "i-cr-drop: drop next incoming Connect-Request",
            "i-cr-dup:  nak next incoming Connect-Request as duplicate VMAC",
            "i-cr-nak:  nak next incoming Connect-Request as ErrorCode \"OTHER\"",
            "i-as-drop: drop next incoming Adv-Sol request",
            "i-as-nak:  nak next incoming Adv-Sol request",
            "i-ar-drop: drop next incoming Adr-Res request",
            "i-ar-nak:  nak next incoming Adr-Res request",
            "i-hr-drop: drop next incoming heartbeat request",
            "i-hr-nak:  drop next incoming heartbeat request",
            "o-cr-drop: drop next outgoing Connect-Request",
            "o-cr-vmac(<vmac>): next outgoing Connect-Request with specified <vmac>",
            "o-ha-drop: drop next outbound heartbeat request",
            "o-sp-swap: send swapped subprotocol (dc for hc and vice versa)",
            "o-sp-bad:  send bogus subprotocol (neither dc nor hc)",
            "o-destopt-ut:   send next outgoing with unknown dest option with must understand true",
            "o-destopt-uf:   send next outgoing with unknown dest option with must understand false",
            "o-destopt-uf2:  send next outgoing with two unknown dest options with must understand false",
            "o-destopt-ufd:  send next outgoing with unknown dest option with must understand false with data",
            "o-destopt-ufd2: send next outgoing with two unknown dest options with must understand false with data",
            "o-destopt-pt:   send next outgoing with proprietary dest option with must understand true",
            "o-destopt-pf:   send next outgoing with proprietary dest option with must understand false",
            "o-destopt-pfd:  send next outgoing with proprietary dest option with must understand false and data",
            "o-destopt-ce(<bytes>): send next outgoing with dest option with hand-crafted custom encoding",
            "o-dataopt-ut:   send next outgoing NPDU with unknown data option with must understand true",
            "o-dataopt-uf:   send next outgoing NPDU with unknown data option with must understand false",
            "o-dataopt-ufd:  send next outgoing NPDU with unknown data option with must understand false and data",
            "o-dataopt-pt:   send next outgoing NPDU with proprietary data option with must understand true",
            "o-dataopt-pf:   send next outgoing NPDU with proprietary data option with must understand false",
            "o-dataopt-pfd:  send next outgoing NPDU with proprietary data option with must understand false and data",
            "o-dataopt-big(<size>): send next outgoing NPDU with proprietary data option with must understand false and large data",
            "o-dataopt-sp2:  send next outgoing NPDU with two Secure Path",
            "o-dataopt-spd:  send next outgoing NPDU with Secure Path with bogus data",
            "o-dataopt-nn:   send next outgoing non-NPDU with a data option",
            "o-dataopt-ce(<bytes>): send next outgoing with hand-crafted custom encoding",
            "o-fc-i:         send next outgoing with illegal function",
            "o-fc-p:         send next outgoing with proprietary function",
            "o-d-a:          send next outgoing with destination absent",
            "o-d-p:          send next outgoing with destination present as zeros",
            "o-d-pa(<vmac>): send next outgoing with destination present as address <vmac>",
            "o-d-pb:         send next outgoing with destination present as broadcast",
            "o-o-a:          send next outgoing with originating absent",
            "o-o-p:          send next outgoing with originating present as zeros",
            "o-o-pa(<vmac>): send next outgoing with originating present as address <vmac>",
            "o-o-pb:         send next outgoing with originating present as broadcast",
            "o-ws-nb:        send next outgoing with non-binary WebSocket frame",
            "o-pay(<bytes>): set next payload to <bytes>. Can be empty",
    };

    private static String injectionType;
    private static String injectionData;
    private static int    matchConnectionNumber;
    private static int    matchFunctionCode;

    // if n/a, connectionNumber and functionCode can be -1 and injectionData can be null
    public static void setInjection(String injectionType, String injectionData, int connectionNumber, int functionCode) throws Exception {
        checkValidity(injectionType, injectionData, connectionNumber, functionCode);
        SCErrorInjection.injectionType         = injectionType;
        SCErrorInjection.injectionData         = injectionData;
        SCErrorInjection.matchConnectionNumber = connectionNumber;
        SCErrorInjection.matchFunctionCode     = functionCode;
    }

    public static void checkValidity(String injectionType, String injectionData, int connectionNumber, int functionCode) throws Exception {
        boolean found = false;
        for (String s : injections) {
            String signature = s.split(":")[0];      // get function signature: "o-o-pa(<vmac>)" from the "<signature>: description" line format
            String name = signature.split("\\(")[0]; // get just function name: "o-o-pa"
            if (injectionType.equals(name)) {
                if (signature.contains("(")) {
                    if (injectionData == null) { throw new Exception("Injection: \""+name+"\" requires arguments"); }
                    checkArguments(injectionType, injectionData);
                }
                found = true;
                break;
            }
        }
        if (!found) throw new Exception("Injection \""+injectionType+"\" not found.");
        // TODO check connectionNumber and functionCode
    }

    public static void checkArguments(String injectionType, String injectionData) throws Exception {
        // for those few injectionTypes that take arguments,
        // check them here to provide early feedback to users if they are setting this in the shell
        switch (injectionType) {
            case "o-d-pa":
            case "o-o-pa":
            case "o-cr-vmac":
                try { new SCVMAC(injectionData);}
                catch (Exception e) { throw new Exception(injectionData+" is not a valid VMAC"); }
                break;
            case "o-dataopt-ce":
            case "o-destopt-ce":
            case "o-pay":
                try { Formatting.fromHex(injectionData);}
                catch (Exception e) { throw new Exception(injectionData+" is not a valid hex"); }
                break;
            case "o-dataopt-big":
                try { Integer.parseInt(injectionData);}
                catch (Exception e) { throw new Exception(injectionData+" is not a valid decimal number"); }
                break;

        }
    }

    public static String checkSubProtocol(String subprotocol) {
        if (injectionType != null) switch (injectionType) {
            case "o-sp-swap":
                if      (subprotocol.equals("dc.bsc.bacnet.org"))  subprotocol = "hub.bsc.bacnet.org";
                else if (subprotocol.equals("hub.bsc.bacnet.org")) subprotocol = "dc.bsc.bacnet.org";
                reset();
                break;
            case "o-sp-bad":
                subprotocol = "bogus.bsc.bacnet.org";
                reset();
                break;
        }
        return subprotocol;
    }

    public static boolean checkWrite(SCConnection connection, BSWebSocket socket, byte[] bytes, String info) {
        if (injectionType != null) switch (injectionType) {
            case "o-ws-nb":
                if (socket != null && !socket.isClosed()) {
                    log.info(connection,"SENT AS NON-BINARY: "+info);
                    socket.write(Formatting.toHex(bytes));  // invalidly attempts to write is as a string (non-binary)
                    reset();
                    return true;
                }
                else return false;
        }
        return false;
    }

    public static SCMessage checkOutgoing(SCConnection connection, SCMessage message) {
        if (injectionType != null &&
                (matchFunctionCode == -1 || matchFunctionCode == message.function) &&
                (matchConnectionNumber == -1 || matchConnectionNumber == connection.number)) {
            switch (injectionType) {
                case "o-cr-drop":
                    if (message.function == SCMessage.CONNECT_REQUEST) {
                        logBefore(connection,message);
                        message = null;
                        logAfter(connection,message);
                    }
                    reset();
                    break;
                case "o-cr-vmac":
                    if (message.function == SCMessage.CONNECT_REQUEST) {
                        logBefore(connection,message);
                        SCPayloadConnectRequest request = new SCPayloadConnectRequest(message.payload);
                        try { request.vmac = new SCVMAC(injectionData); } catch (Exception ignore) {} // formatting of injectionData already checked
                        message.payload = request.generate();
                        logAfter(connection,message);
                    }
                    reset();
                    break;
                case "o-ha-drop":
                    if (message.function == SCMessage.HEARTBEAT_REQUEST) {
                        logBefore(connection,message);
                        message = null;
                        logAfter(connection,message);
                    }
                    reset();
                    break;
                case "o-destopt-ut":
                    logBefore(connection,message);
                    message.addDestOption(new SCOption(11,true));
                    logAfter(connection,message);
                    reset();
                    break;
                case "o-destopt-uf":
                    logBefore(connection,message);
                    message.addDestOption(new SCOption(11,false));
                    logAfter(connection,message);
                    reset();
                    break;
                case "o-destopt-uf2":
                    logBefore(connection,message);
                    message.addDestOption(new SCOption(11,false));
                    message.addDestOption(new SCOption(22,false));
                    logAfter(connection,message);
                    reset();
                    break;
                case "o-destopt-ufd":
                    logBefore(connection,message);
                    message.addDestOption(new SCOption(11,false,new byte[]{1,2,3,4}));
                    logAfter(connection,message);
                    reset();
                    break;
                case "o-destopt-ufd2":
                    logBefore(connection,message);
                    message.addDestOption(new SCOption(11,false,new byte[]{1,2,3,4}));
                    message.addDestOption(new SCOption(22,false,new byte[]{1,2,3,4}));
                    logAfter(connection,message);
                    reset();
                    break;
                case "o-destopt-pt":
                    logBefore(connection,message);
                    message.addDestOption(new SCOption(31,true,new byte[]{555>>8,555&0xFF,11}));
                    logAfter(connection,message);
                    reset();
                    break;
                case "o-destopt-pf":
                    logBefore(connection,message);
                    message.addDestOption(new SCOption(31,false,new byte[]{555>>8,555&0xFF,11}));
                    logAfter(connection,message);
                    reset();
                    break;
                case "o-destopt-pfd":
                    logBefore(connection,message);
                    message.addDestOption(new SCOption(31,false,new byte[]{555>>8,555&0xFF,11,1,2,3,4}));
                    logAfter(connection,message);
                    reset();
                    break;
                case "o-destopt-ce":
                    logBefore(connection,message);
                    message.clearDestOptions();
                    // inject an option type -1. This makes the injectionData then *entire* encoded option, so any header flags are possible, etc.
                    try { message.addDestOption(new SCOption(-1,true,Formatting.fromHex(injectionData))); } catch (Exception ignore) {} // formatting already checked
                    logAfter(connection,message);
                    reset();
                    break;
                case "o-dataopt-ut":
                    if (message.function == SCMessage.ENCAPSULATED_NPDU) {
                        logBefore(connection,message);
                        message.addDataOption(new SCOption(11,true));
                        logAfter(connection,message);
                    }
                    reset();
                    break;
                case "o-dataopt-uf":
                    if (message.function == SCMessage.ENCAPSULATED_NPDU) {
                        logBefore(connection,message);
                        message.addDataOption(new SCOption(11,false));
                        logAfter(connection,message);
                    }
                    reset();
                    break;
                case "o-dataopt-ufd":
                    if (message.function == SCMessage.ENCAPSULATED_NPDU) {
                        logBefore(connection,message);
                        message.addDataOption(new SCOption(11,false,new byte[]{1,2,3,4}));
                        logAfter(connection,message);
                    }
                    reset();
                    break;
                case "o-dataopt-pf":
                    if (message.function == SCMessage.ENCAPSULATED_NPDU) {
                        logBefore(connection,message);
                        message.addDataOption(new SCOption(31,false,new byte[]{555>>8,555&0xFF,11}));
                        logAfter(connection,message);
                    }
                    reset();
                    break;
                case "o-dataopt-pt":
                    if (message.function == SCMessage.ENCAPSULATED_NPDU) {
                        logBefore(connection,message);
                        message.addDataOption(new SCOption(31,true,new byte[]{555>>8,555&0xFF,11}));
                        logAfter(connection,message);
                    }
                    reset();
                    break;
                case "o-dataopt-pfd":
                    if (message.function == SCMessage.ENCAPSULATED_NPDU) {
                        logBefore(connection,message);
                        message.addDataOption(new SCOption(31,false,new byte[]{555>>8,555&0xFF,0x55,1,2,3,4}));
                        logAfter(connection,message);
                    }
                    reset();
                    break;
                case "o-dataopt-big":
                    if (message.function == SCMessage.ENCAPSULATED_NPDU) {
                        logBefore(connection,message);
                        int size = Integer.parseInt(injectionData); // the (<size>) specifies the entire payload size
                        byte[] payload = new byte[size]; // all zeros except for the three-octet proprietary header
                        payload[0] = 555>>8;   // vendor high
                        payload[1] = 555&0xFF; // vendor low
                        payload[2] = 0x55;     // proprietary type (arbitrary, but same as o-dataopt-pfd)
                        message.addDataOption(new SCOption(31,false,payload));
                        logAfter(connection,message);
                    }
                    reset();
                    break;
                case "o-dataopt-sp2":
                    if (message.function == SCMessage.ENCAPSULATED_NPDU) {
                        logBefore(connection,message);
                        message.clearDataOptions();
                        message.addDataOption(new SCOption(1,true));
                        message.addDataOption(new SCOption(1,true));
                        logAfter(connection,message);
                    }
                    reset();
                    break;
                case "o-dataopt-spd":
                    if (message.function == SCMessage.ENCAPSULATED_NPDU) {
                        logBefore(connection,message);
                        message.clearDataOptions();
                        message.addDataOption(new SCOption(1,true,new byte[]{1,2,3,4}));
                        logAfter(connection,message);
                    }
                    reset();
                    break;
                case "o-dataopt-ce":
                    if (message.function == SCMessage.ENCAPSULATED_NPDU) {
                        logBefore(connection,message);
                        message.clearDataOptions();
                        // inject an option type -1. This makes the injectionData then *entire* encoded option, so any header flags are possible, etc.
                        try { message.addDataOption(new SCOption(-1,true,Formatting.fromHex(injectionData))); } catch (Exception ignore) {} // formatting already checked
                        logAfter(connection,message);
                        reset();
                    }
                    break;
                case "o-dataopt-nn":
                    if (message.function != SCMessage.ENCAPSULATED_NPDU) {
                        logBefore(connection,message);
                        message.addDataOption(new SCOption(1,true));
                        logAfter(connection,message);
                    }
                    reset();
                    break;
                case "o-fc-i":
                    logBefore(connection,message);
                    message.function = 13;
                    logAfter(connection,message);
                    reset();
                    break;
                case "o-fc-p":
                    logBefore(connection,message);
                    message.function = 12;
                    message.payload = new byte[]{555>>8,555&0xFF,11,1,2,3,4};
                    logAfter(connection,message);
                    reset();
                    break;
                case "o-d-p":
                    logBefore(connection,message);
                    message.setDestination(new SCVMAC(new byte[]{0,0,0,0,0,0}));
                    logAfter(connection,message);
                    reset();
                    break;
                case "o-d-pa":
                    logBefore(connection,message);
                    try { message.setDestination(new SCVMAC(injectionData));}
                    catch (Exception e) { log.error("BAD INJECTION: " +injectionData+" is not a valid VMAC"); }
                    logAfter(connection,message);
                    reset();
                    break;
                case "o-d-pb":
                    logBefore(connection,message);
                    message.setDestination(SCVMAC.BROADCAST);
                    logAfter(connection,message);
                    reset();
                    break;
                case "o-d-a":
                    logBefore(connection,message);
                    message.setDestination(null);
                    logAfter(connection,message);
                    reset();
                    break;
                case "o-o-p":
                    logBefore(connection,message);
                    message.setOriginating(new SCVMAC(new byte[]{0,0,0,0,0,0}));
                    logAfter(connection,message);
                    reset();
                    break;
                case "o-o-pa":
                    logBefore(connection,message);
                    try { message.setOriginating(new SCVMAC(injectionData));}
                    catch (Exception e) { log.error("BAD INJECTION: " +injectionData+" is not a valid VMAC"); }
                    logAfter(connection,message);
                    reset();
                    break;
                case "o-o-pb":
                    logBefore(connection,message);
                    message.setOriginating(SCVMAC.BROADCAST);
                    logAfter(connection,message);
                    reset();
                    break;
                case "o-o-a":
                    logBefore(connection,message);
                    message.setOriginating(null);
                    logAfter(connection,message);
                    reset();
                    break;
                case "o-pay":  // specify next payload
                    logBefore(connection,message);
                    try { message.payload = Formatting.fromHex(injectionData);} catch (Exception ignore){} // successfully parsed earlier
                    logAfter(connection,message);
                    reset();
                    break;
            }
        }
        return message;
    }

    private static void logBefore(SCConnection connection, SCMessage message) {
        log.info("INJECTING " + injectionType + " into " + connection.name + " BEFORE: " + message);
    }
    private static void logAfter(SCConnection connection, SCMessage message) {
        log.info("INJECTING " + injectionType + " into " + connection.name + " AFTER:  " + message);
    }
    public static SCMessage checkIncoming(SCConnection connection, SCMessage message) {
        if (injectionType != null &&
                (matchFunctionCode == -1 || matchFunctionCode == message.function) &&
                (matchConnectionNumber == -1 || matchConnectionNumber == connection.number)) {
            switch (injectionType) {
                case "i-hr-nak":
                    if (message.function == SCMessage.HEARTBEAT_REQUEST) {
                        logBefore(connection,message);
                        message = connection.sendError(message, ErrorCode.OTHER, "Bogus Error (injected)");
                        logAfter(connection,message);
                        message = null;
                        reset();

                    }
                    break;
                case "i-hr-drop":
                    if (message.function == SCMessage.HEARTBEAT_REQUEST) {
                        logBefore(connection,message);
                        message = null;
                        logAfter(connection,message);
                        reset();
                    }
                    break;
                case "i-ar-nak":
                    if (message.function == SCMessage.ADDRESS_RESOLUTION) {
                        logBefore(connection,message);
                        message = connection.sendError(message, ErrorCode.OTHER, "Bogus Error (injected)");
                        logAfter(connection,message);
                        message = null;
                        reset();
                    }
                    break;
                case "i-ar-drop":
                    if (message.function == SCMessage.ADDRESS_RESOLUTION) {
                        logBefore(connection,message);
                        message = null;
                        logAfter(connection,message);
                        reset();
                    }
                    break;
                case "i-as-nak":
                    if (message.function == SCMessage.ADVERTISEMENT_SOLICITATION) {
                        logBefore(connection,message);
                        message = connection.sendError(message, ErrorCode.OTHER, "Bogus Error (injected)");
                        logAfter(connection,message);
                        message = null;
                        reset();
                    }
                    break;
                case "i-as-drop":
                    if (message.function == SCMessage.ADVERTISEMENT_SOLICITATION) {
                        logBefore(connection,message);
                        message = null;
                        logAfter(connection,message);
                        reset();
                    }
                    break;
                case "i-cr-nak":
                    if (message.function == SCMessage.CONNECT_REQUEST) {
                        logBefore(connection,message);
                        message = connection.sendError(message, ErrorCode.OTHER, "Bogus Error (injected)");
                        logAfter(connection,message);
                        message = null;
                        reset();
                    }
                    break;
                case "i-cr-drop":
                    if (message.function == SCMessage.CONNECT_REQUEST) {
                        logBefore(connection,message);
                        message = null;
                        logAfter(connection,message);
                        reset();
                    }
                    break;
                case "i-cr-dup":
                    if (message.function == SCMessage.CONNECT_REQUEST) {
                        logBefore(connection,message);
                        message = connection.sendError(message, ErrorCode.NODE_DUPLICATE_VMAC, "Duplicate VMAC (injected)");
                        logAfter(connection,message);
                        connection.disconnect();
                        message = null;
                        reset();
                    }
                    break;
            }
        }
        return message;
    }

    public static void reset() {
        injectionType = null;
        injectionData = null;
        matchConnectionNumber = -1;
        matchFunctionCode = -1;
    }
}
