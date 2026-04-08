// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.bacnetsc;

import dev.bscs.bacnet.stack.Device;
import dev.bscs.bacnet.stack.constants.ErrorClass;
import dev.bscs.bacnet.stack.constants.ErrorCode;
import dev.bscs.common.Shell;
import dev.bscs.events.EventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * An implementation of the BACnet/SC "Node Switch" which accepts connections from {@link SCDirectConnector} that are used initiate
 * direct connections to this node. Like {@link SCHubFunction}, it is a specialization of {@link SCServer}, but it is
 * considerably more complicated because it not only needs the list of incoming connections provided by SCServer, it also
 * needs to maintain a list of outgoing {@link SCDirectConnector} connections to other node switches. Because it has its own
 * list of connections in addition to the server connections, is need to provide the total {@link SCConnectionOwner} context
 * for existing VMACs and UUIDs. The key methods here for direct connections are, of course, {@link #establishDirectConnection}
 * and {@link #disconnectDirectConnection}. But equally important is {@link #sendMessage} which is what the SCNode uses to route
 * outgoing messages either through an established direct connection, if one exists, or by default, sending to the hub connector.
 * TODO need a way to decide when to remove or reuse SCDirectConnectors
 * @author drobin
 */
public class SCNodeSwitch extends SCServer implements SCConnectionOwner, EventListener {

    private static final SCLog  log = new SCLog(SCNodeSwitch.class);

    public List<SCDirectConnector> connectors = new ArrayList<>();

    public SCNodeSwitch(String name, Device device, SCProperties properties, SCNode node) {
        super(name,device,"NS",properties,node,properties.directConnectBindURI,"dc.bsc.bacnet.org",true);
    }

    ////////////// SCConnection Owner interface //////////////

    @Override public SCConnection findConnectionFor(UUID uuid) {
        for (SCDirectConnector connector : connectors) if (connector.connection.peerUUID.equals(uuid) && connector.isConnected()) return connector.connection;
        return super.findConnectionFor(uuid);
    }

    @Override public SCConnection findConnectionFor(SCVMAC vmac) {
        for (SCDirectConnector connector : connectors) if (connector.peerVMAC.equals(vmac) && connector.isConnected()) return connector.connection;
        return super.findConnectionFor(vmac);
    }

    @Override public void incoming(SCConnection connection, SCMessage message) {
        log.info(device,name,"-->incoming() from "+connection.name+ ": " +message);
        message.originating = connection.peerVMAC;
        if (message.destination != null && message.destination.equals(SCVMAC.BROADCAST)) {
            log.protocol(device,name,"Node Switch received broadcast from peer - discarding "+message);
        }
        else {
            node.incoming(connection,message);
        }
    }

    /////////////// Specific behavior to SCNodeSwitch /////////////////////

    public void establishDirectConnection(SCVMAC destination) { establishDirectConnection(destination,false,null); }

    public void establishDirectConnection(SCVMAC destination, boolean force, String[] urls) {
        if (!force) { // 'force' will force a new connection even if one already exists (mostly for testing)
            SCConnection connection = super.findConnectionFor(destination);  // first check if an inbound connection has already been made
            if (connection != null) {
                log.info(device,name,"establishDirectConnection() is ignoring command to connect to "+destination+" when inbound already exists");
                return;
            }
            SCDirectConnector connector = findConnector(destination);  // then check if an existing outbound connection has already been made
            if (connector != null) {
                if (connector.isConnected()) {
                    log.info(device,name,"establishDirectConnection() is ignoring command to connect to " + destination + " when outbound already connected");
                    return;
                }
                if (connector.isIdle()) {
                    log.info(device,name,"establishDirectConnection() is reestablishing idle/disconnected connector " + destination);
                    connector.connect(destination,urls);
                    return;
                }
                log.info(device,name,"establishDirectConnection() is restarting non-idle connector to " + destination);
                connector.close();
                connector.connect(destination,urls);
                return;
            }
        }
        // if neither inbound or outbound exists, or if "forced", then we will make our own outbound connector
        SCDirectConnector connector = new SCDirectConnector("DC:"+destination,properties,node);
        connectors.add(connector);
        log.info(device,name,"establishDirectConnection() starting new outbound connector to "+destination);
        connector.connect(destination,urls);  // hope that worked!
    }

    public void disconnectDirectConnection(SCVMAC destination) {
        SCDirectConnector connector = findConnector(destination);  // check if an existing outbound connection has already been made
        if (connector != null) {
            log.info(device,name,"disconnectDirectConnection() is stopping outbound connection to "+destination);
            connector.disconnect();
        }
        else log.info(device,name,"disconnectDirectConnection() didn't find an existing connection to "+destination);
    }

    public void deleteDirectConnection(SCVMAC destination) {
        SCDirectConnector connector = findConnector(destination);
        if (connector != null) {
            log.info(device,name,"deleteDirectConnection() is deleting connection to "+destination);
            connector.disconnect();
            connectors.remove(connector);
        }
    }

    public void sendMessage(SCMessage message) {
        // Here, the node switch checks if this is broadcast or unicast.
        // If broadcast, it will just send it to the hub connector (and NOT to the direct connects).
        // If unicast it will check if it has a direct connection for the destination and send it through there.
        // Otherwise, it will hand it to the hub connector to handle
        if (message.destination == null || message.destination.equals(SCVMAC.BROADCAST)) {
            node.hubConnector.sendMessage(message);
        }
        else {
            SCConnection connection = findConnection(message.destination);
            if (connection != null && connection.isConnected()) {
                // woohoo we have a direct connection TO US, let's use it
                message.destination = null; // we have to clear this
                message.originating = null; // this was probably already absent
                connection.sendMessage(message);
            }
            else {
                SCDirectConnector connector = findConnector(message.destination);
                if (connector != null && connector.isConnected()) {
                    // woohoo we have a direct connection FROM US, let's use it
                    message.destination = null; // we have to clear this
                    message.originating = null; // this was probably already absent
                    connector.sendMessage(message);
                }
                else node.hubConnector.sendMessage(message); // no dc, just use hub
            }
        }
    }

    public SCConnection findConnection(SCVMAC destination) {
        for (SCConnection connection : connections) if (connection.peerVMAC!=null && connection.peerVMAC.equals(destination)) return connection;
        return null;
    }

    public SCDirectConnector findConnector(SCVMAC destination) {
        for (SCDirectConnector connector : connectors) if (connector.peerVMAC.equals(destination)) return connector;
        return null;
    }

    public void dump(String prefix) {
        Shell.println(prefix+"--- Node Switch ---");
        Shell.println(prefix+"name:\""+name+"\" state:"+state+" timer:"+timer+" failure:\""+failure+"\"");
        if (!connections.isEmpty()) Shell.println(prefix+"incoming:");
        for (SCConnection      c: connections) c.dump(prefix+"   ");
        if (!connectors.isEmpty()) Shell.println(prefix+"outgoing:");
        for (SCDirectConnector c: connectors)  c.dump(prefix+"   ");
    }

    @Override public String toString() {
        return "SCNodeSwitch "+name;
    }

}
