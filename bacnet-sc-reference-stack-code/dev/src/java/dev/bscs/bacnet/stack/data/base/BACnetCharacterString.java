// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.stack.data.base;

public class BACnetCharacterString extends BACnetData {

    public String value;

    public BACnetCharacterString(String value)  { this.value = value; }

    public int  getDataType()                   { return BACnetDataType.CHARACTER_STRING;  }

    public String toString()                    { return value != null? value : "(no value)"; }
}
