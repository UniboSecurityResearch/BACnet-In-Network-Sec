// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.stack.objects;

import dev.bscs.bacnet.bacnetsc.SCLog;
import dev.bscs.bacnet.stack.data.base.BACnetARRAY;
import dev.bscs.bacnet.stack.data.base.BACnetLIST;
import dev.bscs.common.Configuration;

import java.util.UUID;

/**
 * This is a generic "data" object with all public members, so populate it any way you want.
 * For file-configured applications, there is convenience constructor that reads its values from a Properties object.
 * For embedded applications, the properties will come from somewhere else.
 * @author drobin
 */

public class DeviceObjectProperties implements Cloneable  {


    private static SCLog log = new SCLog(DeviceObjectProperties.class);

    //////////// CONFIGURATION PROPERTIES /////////////////

    public int        instance                   = 555001;           // read from <application-name>.properties
    public String     description                = "Generic Device"; // read from <application-name>.properties
    public String     location                   = "Somewhere";
    public String     modelName                  = "Generic Device"; // read from <application-name>.properties
    public String     vendorName                 = "Unknown Vendor"; // read from Common.properties
    public String     namePrefix                 = "Unknown-";       // read from Common.properties
    public int        vendorIdentifier           = 666;              // read from Common.properties
    public String     firmwareRevision           = "0.0 Unknown";    // read from Version.properties
    public String     applicationSoftwareVersion = "0.0 Unknown";    // read from Version.properties
    public int        utcOffset                  = 300;
    public int        apduTimeout                = 0;                 // fixed value: no retries
    public int        numberOfAPDURetries        = 0;                 // fixed value: retries not implemented yet
    public UUID       uuid                       = UUID.randomUUID(); // read from <application-name>.properties
    public int        maxAPDULengthAccepted      = 1497;

    //////////// STATUS PROPERTIES /////////////////

    public int         systemStatus              = 0;                 // 0=operational
    public BACnetARRAY objectList                = new BACnetARRAY(); // populated in constructor and beyond
    public BACnetLIST  deviceAddressBinding      = new BACnetLIST();
    public int         databaseRevision          = 1;                 // nothing is ever created/persisted (yet)

    // For file-based configuration, this constructor will get its values from a Configuration object, likely read from a file.
    public DeviceObjectProperties(Configuration properties) {
        instance                    = properties.getInteger("device.instance",instance);
        description                 = properties.getString( "device.description",description);
        location                    = properties.getString( "device.location",location);
        modelName                   = properties.getString( "device.modelName",modelName);
        vendorName                  = properties.getString( "device.vendorName",vendorName);
        vendorIdentifier            = properties.getInteger("device.vendorIdentifier", vendorIdentifier);
        namePrefix                  = properties.getString( "device.namePrefix",namePrefix);
        firmwareRevision            = properties.getString( "device.firmwareRevision",firmwareRevision);
        applicationSoftwareVersion  = properties.getString( "device.applicationSoftwareVersion",applicationSoftwareVersion);
        utcOffset                   = properties.getInteger("device.utcOffset",utcOffset);
        //apduTimeout                 = properties.getInteger("device.apduTimeout",apduTimeout); // not implemented yet
        //numberOfAPDURetries         = properties.getInteger("device.numberOfAPDURetries",numberOfAPDURetries); // not implemented yet
        uuid                        = properties.getUUID(   "device.uuid",uuid);
    }



}
