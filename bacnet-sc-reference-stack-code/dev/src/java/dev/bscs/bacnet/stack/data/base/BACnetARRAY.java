// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.stack.data.base;

import dev.bscs.bacnet.stack.Failure;
import dev.bscs.bacnet.stack.constants.ErrorClass;
import dev.bscs.bacnet.stack.constants.ErrorCode;

import java.util.ArrayList;

public class BACnetARRAY extends BACnetData {

    public ArrayList<BACnetData> value;

    public BACnetARRAY(BACnetData...  values)    { this.value = new ArrayList<>(values.length); for (BACnetData data:values) value.add(data); }

    public int        getDataType()              { return BACnetDataType.ARRAY;   }

    public BACnetData getMember(int index)  throws Failure.Error {
        if (index == 0) return new BACnetUnsigned(value.size());
        if (index > value.size()) throw new Failure.Error(ErrorClass.PROPERTY, ErrorCode.INVALID_ARRAY_INDEX);
        else return value.get(index-1);
    }

    public String toString()  { return value != null? "BACnetARRAY["+value.size()+"]" : "(no value)"; }
}
