// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.stack.data.base;

import dev.bscs.bacnet.stack.Failure;
import dev.bscs.bacnet.stack.constants.ErrorClass;
import dev.bscs.bacnet.stack.constants.ErrorCode;

public class BACnetUnsigned extends BACnetData {

    public long value;

    public BACnetUnsigned(long value)   { this.value = value; }

    public BACnetUnsigned(String value) throws Failure.Error {
        try { this.value = Long.parseLong(value); if (this.value < 0) throw new NumberFormatException("Unsigned values can't be negative"); }
        catch (NumberFormatException e) { throw new Failure.Error(ErrorClass.PROPERTY, ErrorCode.VALUE_OUT_OF_RANGE, e.getLocalizedMessage()); }
    }

    public int  getDataType()           { return BACnetDataType.UNSIGNED; }

    public String toString()            { return Long.toString(value); }

}
