// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.stack.data.base;

import dev.bscs.bacnet.stack.Failure;
import dev.bscs.bacnet.stack.constants.ErrorClass;
import dev.bscs.bacnet.stack.constants.ErrorCode;

public class BACnetEnumerated extends BACnetData {

    public int value;

    public BACnetEnumerated(int value) { this.value = value; }

    public BACnetEnumerated(String value) throws Failure.Error {
        try { this.value = Integer.parseInt(value);}
        catch (NumberFormatException e) { throw new Failure.Error(ErrorClass.PROPERTY, ErrorCode.VALUE_OUT_OF_RANGE, e.getLocalizedMessage()); }
    }

    public int  getDataType()        { return BACnetDataType.ENUMERATED; }

    public void setValue(int value)  { this.value = value; }

    public int  getValue()           { return value; }

    public String toString()  { return Integer.toString(value); }

}
