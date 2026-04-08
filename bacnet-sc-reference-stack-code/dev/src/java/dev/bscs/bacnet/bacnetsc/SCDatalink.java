// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.bacnetsc;

import dev.bscs.bacnet.stack.*;
import dev.bscs.common.Formatting;
import dev.bscs.common.Shell;

/**
 * An implementation of the Datalink interface for BACnet/SC, to be attached to a {@link NetworkLayer}.
 * This is really just a wrapper for {@link SCNode} since the "node" is really the heart of an SC datalink.
 * The configuration of this datalink and its node is controlled by the sc.xxx configuration properties.
 * @author drobin
 */
public class SCDatalink implements Datalink {

    private static final SCLog log = new SCLog(SCDatalink.class);

    public String        name;
    public int           network;  // set&read by network layer - we don't use it here
    public Device        device;
    public SCNode        node;
    public SCProperties  properties;

    //////////// constructors //////////////

    public SCDatalink(String name, SCProperties properties, Device device, int network) {
        this.name       = name;
        this.properties = properties;
        this.device     = device;
        this.network    = network;
        this.node       = new SCNode(name,properties,this);
        device.networkLayer.addDatalink(this);
        // node does all the work, this is just a wrapper, so this doesn't need to be EventListener
    }

    ////////// Datalink interface ////////////////
    @Override public void          setNetwork(int network)       { this.network = network; }
    @Override public int           getNetwork()                  { return network; }
    @Override public NetworkLayer  getNetworkLayer()             { return device.networkLayer; }
    @Override public String        getName()                     { return name; }
    @Override public byte[]        getMac()                      { return properties.vmac.toBytes(); }
    @Override public String        getMacAsString()              { return properties.vmac.toString(); }
    @Override public String        macToString(byte[] bytes)     { return SCVMAC.toString(bytes); }
    @Override public boolean       start()                       { return node.start(); }
    @Override public void          stop()                        { node.stop(); }   // request to stop - normal async state machine
    @Override public void          close()                       { node.close(); }  // rude halt - synchronous shutdown
    @Override public void          dlUnitdataRequest(byte[] da, byte[] data, int priority, boolean der, AuthData auth) {
        log.info(device,name,"dlUnitdataRequest()<-- da="+ Formatting.toMac(da) + " npdu={" + new NPDU(data) + "} priority="+priority+" der="+der+" auth="+auth);
        SCVMAC destination = (da==null || da.length==0)? SCVMAC.BROADCAST : new SCVMAC(da);
        SCMessage message = new SCMessage(null, destination, SCMessage.ENCAPSULATED_NPDU, data);
        if (auth != null) message.addDataOptions(auth.getOptions());
        node.sendMessage(message);
    }

    public void onIncomingNPDU(SCMessage message) {
        device.networkLayer.dlUnitdataIndication(this,
                    message.originating != null? message.originating.toBytes() : new byte[0],
                    message.destination != null && message.destination.isBroadcast(),
                    message.payload, 0, false, new AuthData(message.dataOptions));
    }

    public String toString() {
        return "SC Datalink "+name;
    }

    public void dump(String prefix) {
        Shell.println(prefix+"--- SC Datalink ---");
        Shell.println(prefix+"name:\""+name+"\" network:"+ network);
        node.dump(prefix+"   ");
    }


}