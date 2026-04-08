// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.stack;

import dev.bscs.common.Formatting;
import dev.bscs.common.Timer;
import dev.bscs.events.EventListener;
import dev.bscs.events.EventLoop;
import dev.bscs.events.EventType;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Basic *routing* network layer. Supports the minimum required functionality of a compliant router (which is actually
 * quite a lot!).  It is generally "state free", but it does support one outstanding request while waiting for
 * I-Am-Router responses, so this requires that it listen to maintenance events to send a reject if the route is not found.
 * Yes, things here are public to simplify user's code - these things are all always available and never made on the fly.
 * @author drobin
 */
public class NetworkLayer implements EventListener {

    private static final BACnetLog log = new BACnetLog(NetworkLayer.class);

    public static final int WHO_IS_ROUTER_TO_NETWORK         = 0x00;
    public static final int I_AM_ROUTER_TO_NETWORK           = 0x01;
    public static final int I_COULD_BE_ROUTER_TO_NETWORK     = 0x02;
    public static final int REJECT_MESSAGE_TO_NETWORK        = 0x03;
    public static final int ROUTER_BUSY_TO_NETWORK           = 0x04;
    public static final int ROUTER_AVAILABLE_TO_NETWORK      = 0x05;
    public static final int INITIALIZE_ROUTING_TABLE         = 0x06;
    public static final int INITIALIZE_ROUTING_TABLE_ACK     = 0x07;
    public static final int WHAT_IS_NETWORK_NUMBER           = 0x12;
    public static final int NETWORK_NUMBER_IS                = 0x13;

    public  Device         device;
    public  List<Datalink> datalinks    = new ArrayList<>();
    public  Datalink       homeDatalink = null; // first datalink becomes "home"
    public  int            waitForIAmRouterTimeout; // from properties - how long does QueuedNPDU last

    public static class Route {
        public int      network;
        public Datalink datalink;
        public byte[]   router;
        public Timer    busyTimer = new Timer();
        public boolean  unreachable = false; // REJECT reason 1 = permanent, sets this flag;  2 = temporary, sets busyTimer
        // note: we don't really do anything with 'unreachable''. Marking something *permanenthy* unreachable just because some
        // router returned a "1" to us because it couldn't find a route, some time in the past, is a stupid idea.
        public Route(int network, Datalink datalink, byte[] router) { this.network = network; this.datalink = datalink; this.router = router; }
    }
    public final List<Route> routes = new ArrayList<>();

    private static class QueuedNPDU {
        public  NPDU           npdu;
        public  AuthData       auth;
        public  Datalink       sourceDatalink ;
        public  byte[]         sourceAddress;
        public  Timer          timer = new Timer(); // if this times out, we will send reject to source
        public QueuedNPDU(NPDU npdu , AuthData auth, Datalink sourceDatalink, byte[]  sourceAddress, int timeout) {
            this.npdu           = npdu;
            this.auth           = auth;
            this.sourceDatalink = sourceDatalink;
            this.sourceAddress  = sourceAddress;
            this.timer.start(timeout);
        }
    }
    public  QueuedNPDU queuedNPDU = null; // very minimal implementation here: we remember *one* outstanding NPDU while waiting for an I-am-router // TODO make this an actual Queue


    public NetworkLayer(Device device) {
        this.device = device;
        this.waitForIAmRouterTimeout = device.configProperites.getInteger("stack.waitForIAmRouterTimeout",2000);
    }

    public void addDatalink(Datalink datalink) {
        datalinks.add(datalink);
        if (homeDatalink == null) homeDatalink = datalink; // first one added becomes "home"
    }

    public List<Datalink> getDatalinks() { return datalinks; }

    public int getPortNumberFor(Datalink datalink) {
        for (int i=1; i < datalinks.size(); i++) if (datalinks.get(i) == datalink) return i;
        return 0;
    }

    public boolean isRouter() { return datalinks.size() > 1; }

    ///////// interface to Datalink Layer ///////

    public void dlUnitdataIndication(Datalink datalink, byte[] sa, boolean broadcast, byte[] npduBytes, int priority, boolean der, AuthData auth) {
        log.info(device,"NL","-->dlUnitdataIndication() from "+datalink.getNetwork()+" sa="+datalink.macToString(sa)+" auth="+auth+" npdu={"+new NPDU(npduBytes)+"}");
        fromDatalink(datalink, sa, broadcast, npduBytes, auth);
    }

    // outgoing message coming down from our own application layer
    public void sendNPDU(int dnet, byte[] dadr, NPDU npdu, AuthData auth) {
        if (dnet < 0 || dnet > 65535 || npdu == null) { log.implementation(device,"NL","CAN'T SEND sendNPDU()<-- dnet="+dnet+" dadr="+ Formatting.toMac(dadr)+" npdu={"+npdu+"}"); return; }
        log.info(device,"NL","sendNPDU()<-- dnet="+dnet+" dadr="+ Formatting.toMac(dadr)+" npdu={"+npdu+"}");
        queuedNPDU = null; // we're "nice" but only to a point! we will buffer *one* outstanding NPDU waiting on an i-am-router
        if (dnet == 0) {
            if (homeDatalink == null) throw new RuntimeException("No home datalink defined!");
            npdu.dnet = 0;
            npdu.dadr = null;
            byte da[] = dadr;
            homeDatalink.dlUnitdataRequest(da,npdu.generate(),npdu.priority,npdu.der,auth);
            // and also give to ourselves if broadcast
            if (da.length==0) device.applicationLayer.nUnitdataIndication(0, homeDatalink.getMac(), true, npdu.payload, 0, false, auth);
        }
        else if (dnet == 0xFFFF) {
            npdu.dnet = 0xFFFF;
            npdu.dadr = null;
            byte[] da = null;
            for (Datalink datalink: datalinks) {
                datalink.dlUnitdataRequest(da,npdu.generate(),npdu.priority,npdu.der,auth);
            }
            // and also give to ourselves ("from" our home datalink)
            device.applicationLayer.nUnitdataIndication(0, homeDatalink.getMac(), true, npdu.payload, 0, false, auth);
        }
        else {
            Datalink datalink = findDirectNetwork(dnet);
            if (datalink != null) { // it's for directly connected network
                npdu.dnet = 0;
                npdu.dadr = null;
                byte[] da = dadr;
                datalink.dlUnitdataRequest(da, npdu.generate(), npdu.priority, npdu.der, auth); // send as local
            }
            else { // it's for a remote network, so add dnet,dadr,hop before sending
                npdu.dnet     = dnet;
                npdu.dadr     = dadr;
                npdu.hopCount = 255;
                Route route = findRemoteNetwork(dnet);
                if (route != null) {
                    route.datalink.dlUnitdataRequest(route.router, npdu.generate(),npdu.priority,npdu.der,auth);
                }
                else {
                    // network is not known... remember this NPDU and issue a who-is-router
                    log.debug("Queuing internally generated NPDU while finding route to it's dnet. "+npdu);
                    queuedNPDU = new QueuedNPDU(npdu,auth,null,null,waitForIAmRouterTimeout); // null means internal sender - no one to reject
                    // EventLoop.addMaintenance(this); // intenal sender - we don't send rejections to internal senders (at this time)
                    for (Datalink dl : datalinks) sendWhoIsRouterToNetwork(dl,dnet,npdu.priority);
                    // if an I-Am-Router comes in, we will send the pending NPDU on its way
                }
            }
        }
    }


    // incoming message received from a datalink (to be routed to us or others)
    void fromDatalink(Datalink datalink, byte[] sa, boolean broadcast, byte[] npduBytes, AuthData auth) {
        NPDU npdu = new NPDU(npduBytes);
        if (npdu.parseError) { log.error(device,"NetworkLayer","Error parsing NPDU - dropping: "+npdu); return; }
        if (npdu.hopCount > 0) npdu.hopCount--;
        if (npdu.dnet != 0 && npdu.hopCount == 0) return;
        // first learn the router for the remote network if we haven't already seen it
        if (npdu.snet != 0 && findDirectNetwork(npdu.snet)==null) addRoute(npdu.snet, datalink, sa);
        if (npdu.dnet != 0 && --npdu.hopCount <= 0) return; // if this one's been around a while, let it die.
        if (npdu.snet == 0) {  // if it originated locally, then add snet,sadr
            npdu.snet = datalink.getNetwork(); // this could still be zero for non-routing nodes
            npdu.sadr = sa;
        }
        if (npdu.dnet == 0) {   // no dnet, so it's for us
            messageForUs(datalink, sa, broadcast, npdu, auth);
        }
        else if (npdu.dnet == 0xFFFF) { // global broadcast? distribute to all datalinks except origin
            for (Datalink outlink : datalinks) {
                if (outlink != datalink) outlink.dlUnitdataRequest(null, npdu.generate(), 0, false, auth);
            }
            messageForUs(datalink, sa, broadcast, npdu, auth); // and also give to ourselves, of course
        }
        else { // not global broadcast, so find destination network
            Datalink directLink = findDirectNetwork(npdu.dnet); // first see if the destination is a directly connected network
            if (directLink != null) {
                if (directLink == datalink) {
                    // it came in on the directly connected network that *is* the dnet! This is obviously an error and since the general feeling
                    // on the committee is, *currently*, that routers should *not* be "helpful" to confused senders, we will send a reject (but only if
                    // it's not a broadcast, because that would result in *every* router on this network to send a reject)
                    if (broadcast) {
                        log.error("Confused device "+Formatting.toMac(sa)+" is sending with DNET == local network on "+datalink+ "(dropping broadcast)");
                    }
                    else {
                        log.error("Confused device "+Formatting.toMac(sa)+" is sending with DNET == local network on "+datalink+ "(rejecting unicast)");
                        sendRejectMessageToNetwork(datalink,sa,npdu.snet,npdu.sadr,0,npdu.dnet);
                    }
                }
                else {
                    byte[] da = npdu.dadr;            // this is the last hop, so the dadr becomes the da
                    npdu.dnet = 0; npdu.dadr = null;  // and dnet/dadr disappear for the local network
                    // yay, we routed something to the local network
                    directLink.dlUnitdataRequest(da, npdu.generate(), 0, false, auth);
                }
            }
            else { // no direct link found, so find a remote router to send it to next hop
                Route route = findRemoteNetwork(npdu.dnet);
                if (route != null) {
                    // We found another router to the dnet; and it is hopefully on another datalink
                    if (route.datalink != datalink) {
                        if (route.busyTimer.remaining() != 0) {
                            sendRejectMessageToNetwork(datalink,sa,npdu.snet,npdu.sadr,2,route.network);
                        }
                        else {
                            // yay, we routed something to another router
                            route.datalink.dlUnitdataRequest(route.router, npdu.generate(), 0, false, auth);
                        }
                    }
                    else {
                        // it came in on same network that it needs to go out of. If it's a broadcast, that's OK because it's "6.5.3 case 4" of a lazy device not
                        // wanting to bother to find a route. But if it was unicast to us, then the sender is confused and the general feeling
                        // on the committee is, *currently*, that routers should *not* be "helpful" to confused senders and send the message to the correct router,
                        // so we will send a reject it instead.
                        if (broadcast) {
                            log.debug("Lazy device "+Formatting.toMac(sa)+" broadcasting remote traffic to "+npdu.dnet+" on "+datalink+ "(ignoring broadcast)");
                        }
                        else {
                            log.error("Confused device "+Formatting.toMac(sa)+" is sending us remote traffic to "+npdu.dnet+" that should be sent to another router on "+datalink+" (rejecting unicast)");
                            sendRejectMessageToNetwork(datalink,sa,npdu.snet,npdu.sadr,0,npdu.dnet);
                        }
                    }
                }
                else {
                    // save this NPDU for later and try to find the dnet for it
                    log.debug("Queuing external NPDU while finding route to it's dnet. {"+npdu+"}");
                    queuedNPDU = new QueuedNPDU(npdu,auth,datalink,sa,waitForIAmRouterTimeout); // null means internal sender - no one to reject
                    EventLoop.addMaintenance(this); // we need to send rejects to external senders after the wait expires
                    for (Datalink dl: datalinks) if (dl != datalink) sendWhoIsRouterToNetwork(dl,npdu.dnet,npdu.priority);
                }
            }
        }
    }

    private void messageForUs(Datalink datalink, byte[] sa, boolean broadcast, NPDU npdu, AuthData auth) { // no dnet, so it's for us
        // now assign snet and sadr if not already from remote network
        if (npdu.snet == 0) {
            npdu.snet = datalink.getNetwork();
            npdu.sadr = sa;
        }
        // now process message for us
        if (npdu.isAPDU) { // for the app layer
            device.applicationLayer.nUnitdataIndication(npdu.snet, npdu.sadr, broadcast, npdu.payload, 0, false, auth);
        }
        else switch (npdu.messageType) { // for the net layer
            case WHO_IS_ROUTER_TO_NETWORK:
                if (!isRouter()) break;
                int query = 0xFFFF; // get network number or default to global request
                if (npdu.payload!=null && npdu.payload.length == 2) query = (npdu.payload[0]&0xFF)<<8 | (npdu.payload[1]&0xFF);
                List<Integer> answers = new ArrayList<>();
                if (query == 0xFFFF) {
                    // first add all direct networks (that are not the incoming network)
                    for (Datalink dl : datalinks) if (dl != datalink) answers.add(dl.getNetwork());
                    // then add all learned routes (that are not from the incoming network)
                    for (Route route : routes) if (route.datalink != datalink) answers.add(route.network);
                }
                else { // looking for a single network - see if we know about it, or continue the search for it
                    Datalink dl = findDirectNetwork(query); // first check direct networks
                    if (dl!= null && dl != datalink) answers.add(query);  // if found then done
                    else {  // it's not a direct link, so check learned routes (that are not from the incoming network)
                        boolean found = false;
                        for (Route route : routes) if (route.datalink != datalink && route.network == query) { found=true; break; }
                        if (found) answers.add(query);
                    }
                    if (answers.isEmpty()) {
                        // here's where it gets tricky. we have to determine if the reason we are not answering is
                        // because we don't know or because the answer can't go back out on the same datalink.
                        // If we don't *know*, then we have to propagate the request.
                        // So we redo a search like above but this time don't check the incoming datalink
                        dl = findDirectNetwork(query);
                        if (dl == null) { // not direct so look in routing table
                            boolean found = false;
                            for (Route route : routes) if (route.network == query) { found=true; break; }
                            if (!found) { // not direct and not found in table
                                // OK, we really don't *know* at this point, so propagate the request out on all ports except the incoming one
                                for (Datalink out : datalinks) {
                                    if (out != datalink) sendWhoIsRouterToNetwork(out,query,npdu.priority);
                                }
                            }
                        }
                    }
                }
                if (answers.size()!=0) {
                    // now send response with the answers // TODO: LIMIT SIZE and repeat as necessary
                    ByteBuffer buf = ByteBuffer.wrap(new byte[answers.size() * 2]);
                    for (int network : answers) buf.putShort((short) network);
                    sendIAmRouterToNetwork(datalink,buf.array(),npdu.priority);
                }
                break;

            case I_AM_ROUTER_TO_NETWORK:
                ByteBuffer buf = ByteBuffer.wrap(npdu.payload);
                while (buf.remaining() != 0) {
                    int network = buf.getShort()&0xFFFF;
                    if (findDirectNetwork(network)==null) addRoute(network, datalink, sa);
                    // now see if we have an outstanding NPDU for this network...
                    if (queuedNPDU != null && !queuedNPDU.timer.expired() && queuedNPDU.npdu.dnet == network) {
                        datalink.dlUnitdataRequest(sa,queuedNPDU.npdu.generate(),queuedNPDU.npdu.priority,queuedNPDU.npdu.der,queuedNPDU.auth);
                        queuedNPDU = null;
                    }
                }
                // now we have to send it out to everyone else too
                for (Datalink dl : datalinks) {
                    if (dl != datalink) sendIAmRouterToNetwork(dl,npdu.payload,npdu.priority);
                }
                break;

            case I_COULD_BE_ROUTER_TO_NETWORK:
                // we don't care about this, but fromDataLink() will forward it if it has a destination other than us
                break;

            case WHAT_IS_NETWORK_NUMBER:
                if (!isRouter()) break;
                sendNetworkNumberIs(datalink);
                break;

            case REJECT_MESSAGE_TO_NETWORK:
                buf = ByteBuffer.wrap(npdu.payload);
                int rejectReason = buf.get()&0xFF;
                int network = buf.getShort()&0xFFFF;
                Route rejectedRoute = findRemoteNetwork(network);
                if (rejectedRoute != null) {
                    switch (rejectReason) {
                        case 1:
                            rejectedRoute.unreachable = true;
                            break;
                        case 2:
                            rejectedRoute.busyTimer.start(30000);
                            rejectedRoute.unreachable = false;
                            break;
                    }
                }
                break;

            case NETWORK_NUMBER_IS:
                // TODO if conflict... "report to network management entity"
                break;

            case ROUTER_BUSY_TO_NETWORK:
                if (!isRouter()) break;
                if (npdu.payload == null || npdu.payload.length == 0) {
                    // this is the yucky case... the sending router did not include a list of networks, so we have to compute it
                    answers = new ArrayList<>();
                    for (Route route : routes) {
                        if (Arrays.equals(sa,route.router)) {
                            answers.add(route.network);
                        }
                    }
                    if (answers.size() == 0) break; // what the...?  router said busy but we don't have any routes for that router
                    buf = ByteBuffer.wrap(new byte[answers.size() * 2]);
                    for (int n : answers) buf.putShort((short)n);
                    npdu.payload = buf.array();  // simply overwrite the original payload as if the sender had sent it!
                }
                for (Datalink dl : datalinks) { // now sent it to everyone else
                    if (dl != datalink) sendRouterBusyToNetwork(dl,npdu.payload,npdu.priority);
                }
                // now actually update the routing table with the busy status from the payload (see, aren't you glad we updated the payload in situ above?)
                buf = ByteBuffer.wrap(npdu.payload);
                while (buf.remaining() != 0) {
                    network = buf.getShort()&0xFFFF;
                    Route busyRoute = findRemoteNetwork(network);
                    if (busyRoute != null) busyRoute.busyTimer.start(30000); // 30 seconds
                }
                break;

            case ROUTER_AVAILABLE_TO_NETWORK:
                if (!isRouter()) break;
                if (npdu.payload == null || npdu.payload.length == 0) {
                    // this is the yucky case... the sending router did not include a list of networks, so we have to compute it
                    answers = new ArrayList<>();
                    for (Route route : routes) {
                        if (Arrays.equals(sa,route.router)) {
                            answers.add(route.network);
                        }
                    }
                    if (answers.size() == 0) break; // what the...?  router said busy but we don't have any routes for that router
                    buf = ByteBuffer.wrap(new byte[answers.size() * 2]);
                    for (int n : answers) buf.putShort((short)n);
                    npdu.payload = buf.array();  // simply overwrite the original payload as if the sender had sent it!
                }
                for (Datalink dl : datalinks) { // now sent it to everyone else
                    if (dl != datalink) sendRouterAvailableToNetwork(dl,npdu.payload,npdu.priority);
                }
                // now actually update the routing table with the busy status from the payload (see, aren't you glad we updated the payload in situ above?)
                buf = ByteBuffer.wrap(npdu.payload);
                while (buf.remaining() != 0) {
                    network = buf.getShort()&0xFFFF;
                    Route availableRoute = findRemoteNetwork(network);
                    if (availableRoute != null) availableRoute.busyTimer.clear();
                }
                break;

            case INITIALIZE_ROUTING_TABLE:
                if (!isRouter()) break;
                int numberOfPorts = 0;
                if (npdu.payload != null && npdu.payload.length == 1) numberOfPorts = npdu.payload[0]&0xFF;
                if (numberOfPorts != 0) { sendRejectMessageToNetwork(datalink,sa,npdu.snet,npdu.sadr,0,npdu.dnet);break; } // 0 = other
                // each entry is four bytes: network number, port number, info length (0)
                ByteBuffer result = ByteBuffer.wrap(new byte[(routes.size() + datalinks.size()) * 4]);
                // first add all direct networks
                for (Datalink dl : datalinks) {
                    result.putShort((short)dl.getNetwork());
                    result.put((byte)getPortNumberFor(dl));
                    result.put((byte)0);
                }
                // then add all learned routes
                for (Route route : routes) {
                    result.putShort((short)route.network);
                    result.put((byte)getPortNumberFor(route.datalink));
                    result.put((byte)0);
                }
                sendInitializeRoutingTableAck(datalink,sa,result.array(),npdu.priority);
                break;

            case INITIALIZE_ROUTING_TABLE_ACK:
                // we never initiate Reinitialize-Routing-Table, so we don't care
                break;

            default:
                if (!broadcast) sendRejectMessageToNetwork(datalink,sa,npdu.snet,npdu.sadr,3,0);
                break;
        }
    }

    public void sendWhoIsRouterToNetwork(Datalink datalink, int network, int priority) {
        NPDU npdu = new NPDU();
        npdu.messageType = WHO_IS_ROUTER_TO_NETWORK;
        if (network == -1 || network == 0xFFFF) {
            npdu.payload = new byte[0];
        }
        else {
            npdu.payload = new byte[2];
            npdu.payload[0] = (byte)((network>>8)&0xFF);
            npdu.payload[1] = (byte)(network&0xFF);
        }
        datalink.dlUnitdataRequest(null,npdu.generate(),priority,false,AuthData.makeSecurePath());
    }

    public void sendInitializeRoutingTableAck(Datalink datalink, byte[] da, byte[] payload, int priority) {
        NPDU npdu = new NPDU();
        npdu.messageType = INITIALIZE_ROUTING_TABLE_ACK;
        npdu.payload = payload;
        datalink.dlUnitdataRequest(da,npdu.generate(),priority,false,AuthData.makeSecurePath());
    }

    public void sendIAmRouterToNetwork(Datalink datalink, byte[] payload, int priority) {
        NPDU npdu = new NPDU();
        npdu.messageType = I_AM_ROUTER_TO_NETWORK;
        npdu.payload = payload;
        datalink.dlUnitdataRequest(null,npdu.generate(),priority,false,AuthData.makeSecurePath());
    }

    public void sendRouterBusyToNetwork(Datalink datalink, byte[] payload, int priority) {
        NPDU npdu = new NPDU();
        npdu.messageType = ROUTER_BUSY_TO_NETWORK;
        npdu.payload = payload;
        datalink.dlUnitdataRequest(null,npdu.generate(),priority,false,AuthData.makeSecurePath());
    }

    public void sendRouterAvailableToNetwork(Datalink datalink, byte[] payload, int priority) {
        NPDU npdu = new NPDU();
        npdu.messageType = ROUTER_AVAILABLE_TO_NETWORK;
        npdu.payload = payload;
        datalink.dlUnitdataRequest(null,npdu.generate(),priority,false,AuthData.makeSecurePath());
    }

    public void sendIAmRouterToNetworkForDirectlyConnectedNetworks(Datalink datalink) {
        if (!isRouter()) return;
        List<Integer> answers = new ArrayList<>();
        // add all direct networks (that are not the given datalink)
        for (Datalink dl : datalinks) if (dl != datalink) answers.add(dl.getNetwork());
        if (answers.size()!=0) {
            ByteBuffer buf = ByteBuffer.wrap(new byte[answers.size() * 2]);
            for (int network : answers) buf.putShort((short) network);
            sendIAmRouterToNetwork(datalink,buf.array(),0);
        }
    }

    public void sendRejectMessageToNetwork(Datalink datalink, byte[] da, int dnet, byte[] dadr, int reason, int network) {
        NPDU npdu = new NPDU(dnet,dadr);
        npdu.messageType = REJECT_MESSAGE_TO_NETWORK;
        ByteBuffer buf = ByteBuffer.wrap(new byte[3]);
        buf.put((byte)reason);
        buf.putShort((short)network);
        npdu.payload = buf.array();
        datalink.dlUnitdataRequest(da,npdu.generate(),0,false,AuthData.makeSecurePath());
    }

    public void sendNetworkNumberIs(Datalink datalink) {
        int network = datalink.getNetwork();
        if (network != 0) {
            NPDU response = new NPDU();
            response.messageType = NETWORK_NUMBER_IS;
            ByteBuffer buf = ByteBuffer.wrap(new byte[3]);
            buf.putShort((short)network);
            buf.put((byte)1); // 1="configured"
            response.payload = buf.array();
            datalink.dlUnitdataRequest(null,response.generate(),0,false,AuthData.makeSecurePath()); // broadcast
        }
    }

    public Datalink findDirectNetwork(int network) {
        for (Datalink datalink: datalinks) if (datalink.getNetwork() == network) return datalink;
        return null;
    }

    public Route findRemoteNetwork(int network) {
        // TODO if this node ever *originates* remote traffic then it will need to do the Who-Is-Router stuff.
        // As it is now, it just learns from snets on incoming requests to be able to send back the response.
        for (Route route : routes) if (route.network == network) return route;
        return null;
    }

    public void addRoute(int network, Datalink datalink, byte[] router) {
        for (Route route : routes) {
            if (route.network == network) {
                route.datalink = datalink;
                route.router   = router;
                return;
            }
        }
        routes.add(new Route(network,datalink,router));
    }


    @Override public void handleEvent(Object source, EventType eventType, Object... args) {
        if (eventType == EventLoop.EVENT_MAINTENANCE) {
            if (queuedNPDU != null && queuedNPDU.timer.expired()) {
                // if the NPDU was from an external source, then send that source a reject message
                log.debug("Rejecting queued NPDU {"+queuedNPDU.npdu+"}");
                if (queuedNPDU.sourceDatalink != null && queuedNPDU.npdu.dnet != 0) sendRejectMessageToNetwork(queuedNPDU.sourceDatalink, queuedNPDU.sourceAddress, queuedNPDU.npdu.snet, queuedNPDU.npdu.sadr, 1, queuedNPDU.npdu.dnet);
                queuedNPDU = null;
                EventLoop.removeMaintenance(this);  // since we only currently have *one* queued NPDU, when it expires, we remove ourselves from the maintenance loop
            }
        }
    }


}
