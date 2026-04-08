// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.stack;

import dev.bscs.bacnet.stack.constants.DCCMode;
import dev.bscs.bacnet.stack.constants.ReinitializeDeviceState;
import dev.bscs.bacnet.stack.data.BACnetObjectType;
import dev.bscs.bacnet.stack.data.base.BACnetObjectIdentifier;
import dev.bscs.bacnet.stack.objects.BACnetObject;
import dev.bscs.bacnet.stack.objects.DeviceObject;
import dev.bscs.bacnet.stack.objects.DeviceObjectProperties;
import dev.bscs.common.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * A Device instance is, naturally, the holder of everything for a BACnet Device: application layer, network layer, and objects.
 * Yes, things here are public to simplify user's code - these things are all always available and never made on the fly.
 * @author drobin
 */
public class Device {

    private static final BACnetLog log = new BACnetLog(Device.class);

    public Configuration      configProperites;
    public DeviceObject       deviceObject;
    public NetworkLayer       networkLayer;
    public ApplicationLayer   applicationLayer;
    public List<BACnetObject> objects = new ArrayList<>();

    public static final List<Device> devices = new ArrayList<>(); // for applications that contain more than one device

    public Device(Configuration configuration) {
        this(configuration,new DeviceObjectProperties(configuration));
    }

    public Device(Configuration configuration, DeviceObjectProperties deviceObjectProperties) {
        configProperites = configuration;
        applicationLayer = new ApplicationLayer(this);
        networkLayer     = new NetworkLayer(this);
        deviceObject     = new DeviceObject(deviceObjectProperties);
        objects.add(deviceObject);
        devices.add(this); // for applications that contain more than one device, add us to the static list
    }

    public void reinitialize(int state) {
        if (state == ReinitializeDeviceState.COLDSTART || state == ReinitializeDeviceState.WARMSTART) {
            log.info(this,"Device","reinitialize() is doing it's thing"+state);
            for (Datalink datalink: networkLayer.datalinks) { datalink.stop(); }
            applicationLayer.setDCCMode(DCCMode.ENABLE,-1);
            // Anything else to do here?
            for (Datalink datalink: networkLayer.datalinks) { datalink.start(); }
        }
        else log.implementation(this,"Device","reinitialize() was given unsupported state: "+state);
    }

    public static Device  getDefaultDevice() { return devices.get(0); } // first one becomes "default", e.g., for manual commands

    ////////// OBJECT LOOKUP /////////

    private static final int deviceWildcard =   BACnetObjectIdentifier.combine(BACnetObjectType.DEVICE,4194303);

    public BACnetObject findObject(int objectIdentifier) {
        if (objectIdentifier == deviceObject.objectIdentifier || objectIdentifier == deviceWildcard) return deviceObject;
        else return null;
    }
    public BACnetObject findObject(String objectName) {
        if (objectName.equals(deviceObject.objectName)) return deviceObject;
        else return null;
    }

}
