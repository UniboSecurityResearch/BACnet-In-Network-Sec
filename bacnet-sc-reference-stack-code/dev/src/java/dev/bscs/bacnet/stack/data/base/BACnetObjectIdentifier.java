// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.stack.data.base;

import dev.bscs.bacnet.stack.Failure;
import dev.bscs.bacnet.stack.constants.ErrorClass;
import dev.bscs.bacnet.stack.constants.ErrorCode;

public class BACnetObjectIdentifier extends BACnetData {

    public int value;

    public BACnetObjectIdentifier(int value)               { this.value = value; }
    public BACnetObjectIdentifier(int type, int instance)  { this.value = combine(type,instance); }
    public BACnetObjectIdentifier(String s) throws Failure.Error { this.value = parse(s); }

    public int getDataType()                              { return BACnetDataType.OBJECT_IDENTIFIER; }

    public int  type()                                     { return typeOf(value); }
    public int  instance()                                 { return instanceOf(value); }
    public int asInt()                                    { return value; }

    public static int typeOf(int objectIdentifier)        { return (objectIdentifier >> 22) & 0x3FF; }
    public static int instanceOf(int objectIdentifier)    { return objectIdentifier & 0x3FFFFF; }
    public static int combine(int type, int instance)     { return (type<<22) | instance; }

    public static int parse(String s) throws Failure.Error {
        String[] parts = s.split(",");
        try { return combine(Integer.parseUnsignedInt(parts[0]),Integer.parseUnsignedInt(parts[1])); }
        catch (IndexOutOfBoundsException | NumberFormatException e) { throw new Failure.Error(ErrorClass.PROPERTY, ErrorCode.VALUE_OUT_OF_RANGE, "Malformed object identifier string"); }
    }

    public static String toString(int id)  { return typeOf(id)+","+instanceOf(id); }

    public String toString()        { return toString(value); }


}
