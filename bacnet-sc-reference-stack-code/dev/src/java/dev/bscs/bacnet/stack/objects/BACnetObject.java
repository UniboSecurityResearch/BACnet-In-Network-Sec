// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.stack.objects;

import dev.bscs.bacnet.stack.AuthData;
import dev.bscs.bacnet.stack.Failure;
import dev.bscs.bacnet.stack.constants.ErrorClass;
import dev.bscs.bacnet.stack.constants.ErrorCode;
import dev.bscs.bacnet.stack.data.BACnetPropertyIdentifier;
import dev.bscs.bacnet.stack.data.base.*;

public abstract class BACnetObject {

    public int    objectType       = -1;
    public int    objectIdentifier = -1;
    public String objectName       = "";

    public BACnetData findProperty(int propertyIdentifier)  { // override this!
        switch (propertyIdentifier) {
            case BACnetPropertyIdentifier.OBJECT_TYPE:       return new BACnetEnumerated(objectType);
            case BACnetPropertyIdentifier.OBJECT_IDENTIFIER: return new BACnetObjectIdentifier(objectIdentifier);
            case BACnetPropertyIdentifier.OBJECT_NAME:       return new BACnetCharacterString(objectName);
            default:                                         return null;
        }
    }

    public abstract int[] getAllPropertyIdentifiers();
    public abstract int[] getRequiredPropertyIdentifiers();
    public abstract int[] getOptionalPropertyIdentifiers();

    public boolean canReadProperty(int propertyIdentifier, AuthData auth) {
        return propertyIdentifier != BACnetPropertyIdentifier.LOG_BUFFER;  //  TODO make this more formal
    }

    public void  setProperty(int propertyIdentifier, BACnetData data) throws Failure.Error {
        switch (propertyIdentifier) {

            case BACnetPropertyIdentifier.OBJECT_TYPE:
                throw new Failure.Error(ErrorClass.PROPERTY, ErrorCode.WRITE_ACCESS_DENIED);

            case BACnetPropertyIdentifier.OBJECT_IDENTIFIER:
                if (data.getDataType() != BACnetDataType.OBJECT_IDENTIFIER) throw new Failure.Error(ErrorClass.PROPERTY, ErrorCode.INVALID_DATA_TYPE);
                if (data.asObjectIdentifier().type() != objectType)   throw new Failure.Error(ErrorClass.PROPERTY, ErrorCode.VALUE_OUT_OF_RANGE);
                objectIdentifier = data.asObjectIdentifier().value;
                break;

            case BACnetPropertyIdentifier.OBJECT_NAME:
                objectName = data.toString();
                break;

            default:
                throw new Failure.Error(ErrorClass.OBJECT, ErrorCode.UNKNOWN_PROPERTY);
        }

    }
}
