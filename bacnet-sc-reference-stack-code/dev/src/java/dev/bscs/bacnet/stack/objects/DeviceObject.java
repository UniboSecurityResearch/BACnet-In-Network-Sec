// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.stack.objects;

import dev.bscs.bacnet.stack.data.*;
import dev.bscs.bacnet.stack.data.base.*;

import java.time.LocalDate;
import java.time.LocalTime;

public class DeviceObject extends BACnetObject {

    public DeviceObjectProperties properties;

    // These final constants are characteristics of the code and are not "configurable".

    public final int  segmentationSupported      =  3;  // 3 = no segmentation
    public final int  protocolRevision           = 16;  // maybe? // TODO this needs verification

    public final BACnetBitString protocolServicesSupported  = new BACnetProtocolServicesSupported(
            BACnetProtocolServicesSupported.DEVICE_COMMUNICATION_CONTROL,
            BACnetProtocolServicesSupported.REINITIALIZE_DEVICE,
            BACnetProtocolServicesSupported.READ_PROPERTY,
            BACnetProtocolServicesSupported.WRITE_PROPERTY,
            BACnetProtocolServicesSupported.READ_PROPERTY_MULTIPLE,
            BACnetProtocolServicesSupported.WHO_IS,
            BACnetProtocolServicesSupported.WHO_HAS,
            BACnetProtocolServicesSupported.I_AM
    );

    public final BACnetBitString protocolObjectTypeSupported  = new BACnetObjectTypesSupported(
            BACnetObjectTypesSupported.DEVICE
    );

    public final BACnetARRAY propertyList = new BACnetARRAY(
            new BACnetEnumerated(BACnetPropertyIdentifier.SYSTEM_STATUS),
            new BACnetEnumerated(BACnetPropertyIdentifier.VENDOR_NAME),
            new BACnetEnumerated(BACnetPropertyIdentifier.VENDOR_IDENTIFIER),
            new BACnetEnumerated(BACnetPropertyIdentifier.MODEL_NAME),
            new BACnetEnumerated(BACnetPropertyIdentifier.FIRMWARE_REVISION),
            new BACnetEnumerated(BACnetPropertyIdentifier.APPLICATION_SOFTWARE_VERSION),
            new BACnetEnumerated(BACnetPropertyIdentifier.LOCATION),
            new BACnetEnumerated(BACnetPropertyIdentifier.DESCRIPTION),
            new BACnetEnumerated(BACnetPropertyIdentifier.PROTOCOL_VERSION),
            new BACnetEnumerated(BACnetPropertyIdentifier.PROTOCOL_REVISION),
            new BACnetEnumerated(BACnetPropertyIdentifier.PROTOCOL_SERVICES_SUPPORTED),
            new BACnetEnumerated(BACnetPropertyIdentifier.PROTOCOL_OBJECT_TYPES_SUPPORTED),
            new BACnetEnumerated(BACnetPropertyIdentifier.OBJECT_LIST),
            new BACnetEnumerated(BACnetPropertyIdentifier.MAX_APDU_LENGTH_ACCEPTED),
            new BACnetEnumerated(BACnetPropertyIdentifier.SEGMENTATION_SUPPORTED),
            new BACnetEnumerated(BACnetPropertyIdentifier.APDU_TIMEOUT),
            new BACnetEnumerated(BACnetPropertyIdentifier.UTC_OFFSET),
            new BACnetEnumerated(BACnetPropertyIdentifier.NUMBER_OF_APDU_RETRIES),
            new BACnetEnumerated(BACnetPropertyIdentifier.DEVICE_ADDRESS_BINDING),
            new BACnetEnumerated(BACnetPropertyIdentifier.DATABASE_REVISION)
    );

    public DeviceObject(DeviceObjectProperties properties) {
        this.properties = properties;
        // set super class properties
        objectType                 = BACnetObjectType.DEVICE;
        objectIdentifier           = BACnetObjectIdentifier.combine(BACnetObjectType.DEVICE, properties.instance);
        objectName                 = properties.namePrefix+properties.instance;
        properties.objectList.value.add(new BACnetObjectIdentifier(BACnetObjectType.DEVICE,properties.instance));
    }

    @Override public BACnetData findProperty(int propertyIdentifier)  {
        switch (propertyIdentifier) {
            case BACnetPropertyIdentifier.SYSTEM_STATUS:                   return new BACnetEnumerated(properties.systemStatus);
            case BACnetPropertyIdentifier.VENDOR_NAME:                     return new BACnetCharacterString(properties.vendorName);
            case BACnetPropertyIdentifier.VENDOR_IDENTIFIER:               return new BACnetUnsigned(properties.vendorIdentifier);
            case BACnetPropertyIdentifier.MODEL_NAME:                      return new BACnetCharacterString(properties.modelName);
            case BACnetPropertyIdentifier.FIRMWARE_REVISION:               return new BACnetCharacterString(properties.firmwareRevision);
            case BACnetPropertyIdentifier.APPLICATION_SOFTWARE_VERSION:    return new BACnetCharacterString(properties.applicationSoftwareVersion);
            case BACnetPropertyIdentifier.LOCATION:                        return new BACnetCharacterString(properties.location);
            case BACnetPropertyIdentifier.DESCRIPTION:                     return new BACnetCharacterString(properties.description);
            case BACnetPropertyIdentifier.PROTOCOL_VERSION:                return new BACnetUnsigned(1);
            case BACnetPropertyIdentifier.PROTOCOL_REVISION:               return new BACnetUnsigned(protocolRevision);
            case BACnetPropertyIdentifier.PROTOCOL_SERVICES_SUPPORTED:     return protocolServicesSupported;
            case BACnetPropertyIdentifier.PROTOCOL_OBJECT_TYPES_SUPPORTED: return protocolObjectTypeSupported;
            case BACnetPropertyIdentifier.OBJECT_LIST:                     return properties.objectList;
            case BACnetPropertyIdentifier.MAX_APDU_LENGTH_ACCEPTED:        return new BACnetUnsigned(properties.maxAPDULengthAccepted);
            case BACnetPropertyIdentifier.SEGMENTATION_SUPPORTED:          return new BACnetEnumerated(BACnetSegmentationSupported.SEGMENTED_NONE);
            case BACnetPropertyIdentifier.APDU_TIMEOUT:                    return new BACnetUnsigned(properties.apduTimeout);
            case BACnetPropertyIdentifier.LOCAL_DATE:                      return new BACnetDate(LocalDate.now());
            case BACnetPropertyIdentifier.LOCAL_TIME:                      return new BACnetTime(LocalTime.now());
            case BACnetPropertyIdentifier.UTC_OFFSET:                      return new BACnetInteger(properties.utcOffset);
            case BACnetPropertyIdentifier.NUMBER_OF_APDU_RETRIES:          return new BACnetUnsigned(properties.numberOfAPDURetries);
            case BACnetPropertyIdentifier.DEVICE_ADDRESS_BINDING:          return properties.deviceAddressBinding;
            case BACnetPropertyIdentifier.DATABASE_REVISION:               return new BACnetUnsigned(properties.databaseRevision);
            case BACnetPropertyIdentifier.PROPERTY_LIST:                   return propertyList;
            default:                                                       return super.findProperty(propertyIdentifier);
        }
    }

    @Override public int[] getAllPropertyIdentifiers() {
        return new int[]{
                BACnetPropertyIdentifier.OBJECT_TYPE,
                BACnetPropertyIdentifier.OBJECT_IDENTIFIER,
                BACnetPropertyIdentifier.OBJECT_NAME,
                BACnetPropertyIdentifier.SYSTEM_STATUS,
                BACnetPropertyIdentifier.VENDOR_NAME,
                BACnetPropertyIdentifier.VENDOR_IDENTIFIER,
                BACnetPropertyIdentifier.MODEL_NAME,
                BACnetPropertyIdentifier.FIRMWARE_REVISION,
                BACnetPropertyIdentifier.APPLICATION_SOFTWARE_VERSION,
                BACnetPropertyIdentifier.LOCATION,
                BACnetPropertyIdentifier.DESCRIPTION,
                BACnetPropertyIdentifier.PROTOCOL_VERSION,
                BACnetPropertyIdentifier.PROTOCOL_REVISION,
                BACnetPropertyIdentifier.PROTOCOL_SERVICES_SUPPORTED,
                BACnetPropertyIdentifier.PROTOCOL_OBJECT_TYPES_SUPPORTED,
                BACnetPropertyIdentifier.OBJECT_LIST,
                BACnetPropertyIdentifier.MAX_APDU_LENGTH_ACCEPTED,
                BACnetPropertyIdentifier.SEGMENTATION_SUPPORTED,
                BACnetPropertyIdentifier.APDU_TIMEOUT,
                BACnetPropertyIdentifier.LOCAL_DATE,
                BACnetPropertyIdentifier.LOCAL_TIME,
                BACnetPropertyIdentifier.UTC_OFFSET,
                BACnetPropertyIdentifier.NUMBER_OF_APDU_RETRIES,
                BACnetPropertyIdentifier.DEVICE_ADDRESS_BINDING,
                BACnetPropertyIdentifier.DATABASE_REVISION
        };
    }
    @Override public int[] getRequiredPropertyIdentifiers() {
        return new int[]{
                BACnetPropertyIdentifier.OBJECT_TYPE,
                BACnetPropertyIdentifier.OBJECT_IDENTIFIER,
                BACnetPropertyIdentifier.OBJECT_NAME,
                BACnetPropertyIdentifier.SYSTEM_STATUS,
                BACnetPropertyIdentifier.VENDOR_NAME,
                BACnetPropertyIdentifier.VENDOR_IDENTIFIER,
                BACnetPropertyIdentifier.MODEL_NAME,
                BACnetPropertyIdentifier.FIRMWARE_REVISION,
                BACnetPropertyIdentifier.APPLICATION_SOFTWARE_VERSION,
                BACnetPropertyIdentifier.PROTOCOL_VERSION,
                BACnetPropertyIdentifier.PROTOCOL_REVISION,
                BACnetPropertyIdentifier.PROTOCOL_SERVICES_SUPPORTED,
                BACnetPropertyIdentifier.PROTOCOL_OBJECT_TYPES_SUPPORTED,
                BACnetPropertyIdentifier.OBJECT_LIST,
                BACnetPropertyIdentifier.MAX_APDU_LENGTH_ACCEPTED,
                BACnetPropertyIdentifier.SEGMENTATION_SUPPORTED,
                BACnetPropertyIdentifier.APDU_TIMEOUT,
                BACnetPropertyIdentifier.NUMBER_OF_APDU_RETRIES,
                BACnetPropertyIdentifier.DEVICE_ADDRESS_BINDING,
                BACnetPropertyIdentifier.DATABASE_REVISION
        };
    }
    @Override public int[] getOptionalPropertyIdentifiers() {
        return new int[]{
                BACnetPropertyIdentifier.LOCATION,
                BACnetPropertyIdentifier.DESCRIPTION,
                BACnetPropertyIdentifier.LOCAL_DATE,
                BACnetPropertyIdentifier.LOCAL_TIME,
                BACnetPropertyIdentifier.UTC_OFFSET,
        };
    }

}
