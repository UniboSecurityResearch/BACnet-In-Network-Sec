// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.stack.data.base;

import dev.bscs.bacnet.stack.Failure;
import dev.bscs.bacnet.stack.constants.ErrorClass;
import dev.bscs.bacnet.stack.constants.ErrorCode;

public class BACnetReal extends BACnetData {

    public float value;

    public BACnetReal(float value)  { this.value = value; }

    public BACnetReal(String value) throws Failure.Error {
        try { this.value = Float.parseFloat(value);}
        catch (NumberFormatException e) { throw new Failure.Error(ErrorClass.PROPERTY, ErrorCode.VALUE_OUT_OF_RANGE, e.getLocalizedMessage()); }
    }

    public int   getDataType()      { return BACnetDataType.REAL; }

    public String toString()        { return Float.toString(value); }

}
