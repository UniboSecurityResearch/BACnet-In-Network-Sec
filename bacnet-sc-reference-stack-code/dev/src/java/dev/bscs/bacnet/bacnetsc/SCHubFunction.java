// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.bacnetsc;

import dev.bscs.bacnet.stack.Device;
import dev.bscs.common.Formatting;
import dev.bscs.common.Shell;
import dev.bscs.events.EventListener;

/**
 * An implementation of the BACnet/SC "Hub Function' which accepts connections from "Hub Connectors" that are used initiate
 * connections to this hub function. It is a specialization of {@link SCServer} that accepts new WebSocket instances and
 * attempts to make a connected {@link SCConnection} from them while acting as their {@link SCConnectionOwner} to provide
 * the "connection context" for queries about existing VMACs and UUIDs.  This wrapper subclass simply provides the handling
 * of where to "send up" messages that are successfully received.
 * @author drobin
 */
public class SCHubFunction extends SCServer implements SCConnectionOwner, EventListener {

    private static final SCLog  log = new SCLog(SCHubFunction.class);

    public SCHubFunction(String name, Device device, SCProperties properties, SCNode node) {
        super(name,device,"HF",properties,node,properties.hubFunctionBindURI,"hub.bsc.bacnet.org",false);
    }

    ////////////// remainder of SCConnectionOwner interface //////////////
    //  findConnectionFor(SCVMAC) and findConnectionFor(UUID) are implemented by scServer

    @Override public void incoming(SCConnection connection, SCMessage message) {
        log.info(device,name,"-->incoming() " + message);
        message.originating = connection.peerVMAC;
        if (message.destination.equals(SCVMAC.BROADCAST)) {
            broadcast(message,connection);
        }
        else {
            unicast(message);
        }
    }

    /////////////// Specific behavior to SCHubFunction /////////////////////

    private void broadcast(SCMessage message, SCConnection except)  {
        log.info(device,name,"broadcast()<-- " + message);
        message.destination = SCVMAC.BROADCAST;
        for (SCConnection connection : connections) {
            if (connection == except) continue;
            if (connection.isConnected()) connection.sendMessage(message);
        }
    }

    private void unicast(SCMessage message) {
        log.info(device,name,"unicast()<-- " + message);
        SCConnection connection = findConnectionFor(message.destination);
        if (connection == null) {
            log.info(device,name,"DROPPING message (HF can't find connection for destination)" + message);
        }
        else {
            message.destination = null;
            connection.sendMessage(message);
        }
    }

    ////////////////////////////////////////////////////

    public void dump(String prefix) {
        Shell.println(prefix+"--- Hub Function ---");
        Shell.println(prefix+"name:\""+name+"\" state:"+state+" timer:"+timer+" failure:\""+failure+"\"");
        for (SCConnection c: connections) c.dump(prefix+"   ");
    }

    public String toString() {
        return "SCHubFunction "+name;
    }


}
