// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.stack.data.base;

import dev.bscs.bacnet.stack.Failure;

public class BACnetBoolean extends BACnetData {

    public boolean value;

    public BACnetBoolean(boolean value)        { this.value = value; }

    public BACnetBoolean(String value)         { this.value = value.equals("true") || value.equals("t") || value.equals("TRUE") || value.equals("T"); }

    public int     getDataType()               { return BACnetDataType.BOOLEAN; }

    public void    setValue(boolean value)     { this.value = value; }

    public boolean getValue()                  { return value; }

    public String  toString()  { return Boolean.toString(value); }

}
