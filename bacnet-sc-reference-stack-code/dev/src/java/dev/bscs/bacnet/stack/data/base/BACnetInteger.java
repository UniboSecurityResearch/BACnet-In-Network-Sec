// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.stack.data.base;

import dev.bscs.bacnet.stack.Failure;
import dev.bscs.bacnet.stack.constants.ErrorClass;
import dev.bscs.bacnet.stack.constants.ErrorCode;

public class BACnetInteger extends BACnetData {

    public long value;

    public BACnetInteger(long value)    { this.value = value; }

    public BACnetInteger(String value) throws Failure.Error {
        try { this.value = Long.parseLong(value);}
        catch (NumberFormatException e) { throw new Failure.Error(ErrorClass.PROPERTY, ErrorCode.VALUE_OUT_OF_RANGE, e.getLocalizedMessage()); }
    }

    public int  getDataType()           { return BACnetDataType.INTEGER; }

    public String toString()            { return Long.toString(value); }

}
