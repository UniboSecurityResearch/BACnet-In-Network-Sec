// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.stack;

import dev.bscs.common.Log;

/**
 * An extension of the common {@link Log} class that accepts an SCConnection as an argument to simplify logging calls
 * where a connection is involved.
 * @author drobin
 */
public class BACnetLog extends Log {

    public BACnetLog(Class clazz) { super(clazz); }

    public  void   debug(Device device, String name, String text)          { debug(logName(device,name),text); }

    public  void   info(Device device, String name, String text)           { info(logName(device,name),text); }

    public  void   warn(Device device, String name, String text)           { warn(logName(device,name),text); }

    public  void   error(Device device, String name, String text)          { error(logName(device,name),text); }

    public  void   configuration(Device device, String name, String text)  { configuration(logName(device,name),text); }

    public  void   protocol(Device device, String name, String text)       { protocol(logName(device,name),text); }

    public  void   implementation(Device device, String name, String text) { implementation(logName(device,name),text); }

    public  String logName(Device device, String name) {
        if (device == null) return name; // not expecting, just defensive
        // to keep logs clean, this adorns name with device name, but only if there is more than one device
        return Device.devices.size()>1 ? name+"("+device.deviceObject.objectName+")" : name;
    }

}
