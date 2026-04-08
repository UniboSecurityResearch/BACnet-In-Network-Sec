// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.bacnetsc;

import dev.bscs.bacnet.stack.BACnetLog;
import dev.bscs.bacnet.stack.Device;
import dev.bscs.common.Log;

import static dev.bscs.bacnet.bacnetsc.SCConnection.State.IDLE;

/**
 * An extension of the common {@link Log} class that accepts an SCConnection as an argument to simplify logging calls
 * where a connection is involved.
 * @author drobin
 */
public class SCLog extends BACnetLog {

    public SCLog(Class clazz) { super(clazz); }

    public  void   debug(SCConnection c, String text)                  { debug(logName(c),text); }

    public  void   info(SCConnection c, String text)                   { info(logName(c),text); }

    public  void   warning(SCConnection c, String text)                { warn(logName(c),text); }

    public  void   error(SCConnection c, String text)                  { error(logName(c),text); }

    public  void   configuration(SCConnection c, String text)          { configuration(logName(c),text); }

    public  void   protocol(SCConnection c, String text)               { protocol(logName(c),text); }
    public  void   protocol(SCConnection c, SCMessage m, String text)  { protocol(logName(c),text +"["+m+"]"); }

    public  void   implementation(SCConnection c, String text)         { implementation(logName(c),text); }

    public  String logName(SCConnection c) {
        if (c == null) return "NULL"; // not expecting, just defensive
        Device device = c.node.datalink.getNetworkLayer().device;
        return  c.name + " " +
                c.properties.vmac + (c.state==IDLE? "-": c.initiated ?">":"<") + (c.peerVMAC==null?"(pending)":c.peerVMAC) +
                " in " + logName(device,c.node.name);
    }

}
