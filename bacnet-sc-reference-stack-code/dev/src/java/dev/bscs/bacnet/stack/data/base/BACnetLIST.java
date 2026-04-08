// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.stack.data.base;

import java.util.ArrayList;
import java.util.Arrays;

public class BACnetLIST extends BACnetData {

    public ArrayList<BACnetData> value;

    public BACnetLIST(BACnetData...  values)  {
        value = new ArrayList<>(values.length);
        value.addAll(Arrays.asList(values));
    }
    public BACnetLIST(BACnetLIST startingWith, BACnetData...  values)  {
        value = new ArrayList<>(startingWith.size() + values.length);
        value.addAll(startingWith.value);
        value.addAll(Arrays.asList(values));
    }
    public int  getDataType()   { return BACnetDataType.LIST; }

    public int    size()        { return value.size(); }

    public String toString()  { return value != null? "BACnetLIST["+value.size()+"]" : "(no value)"; }

}
