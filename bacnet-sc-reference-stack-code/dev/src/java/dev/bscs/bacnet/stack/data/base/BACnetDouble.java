// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.stack.data.base;

import dev.bscs.bacnet.stack.Failure;
import dev.bscs.bacnet.stack.constants.ErrorClass;
import dev.bscs.bacnet.stack.constants.ErrorCode;

public class BACnetDouble extends BACnetData {

    public double value;

    public BACnetDouble(double value)  { this.value = value; }

    public BACnetDouble(String value) throws Failure.Error {
        try { this.value = Double.parseDouble(value);}
        catch (NumberFormatException e) { throw new Failure.Error(ErrorClass.PROPERTY, ErrorCode.VALUE_OUT_OF_RANGE,e.getLocalizedMessage()); }
    }

    public int   getDataType()      { return BACnetDataType.DOUBLE; }

    public String toString()        { return Double.toString(value); }

}
