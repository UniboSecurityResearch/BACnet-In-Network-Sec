// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.stack.data.base;

public class BACnetNull extends BACnetData {

    public BACnetNull() {  }

    public int  getDataType()  { return BACnetDataType.NULL; }

    public String toString()   { return "NULL"; }

}
