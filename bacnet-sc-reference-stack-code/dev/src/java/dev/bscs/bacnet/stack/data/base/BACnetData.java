// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.stack.data.base;

public abstract class BACnetData {
    
    public abstract int           getDataType(); // see BACnetDataType

    public BACnetNull             asNull()             { if (this instanceof BACnetNull)             return (BACnetNull)this            ; else throw new RuntimeException("Invalid Data.asNull()"); }
    public BACnetBoolean          asBoolean()          { if (this instanceof BACnetBoolean)          return (BACnetBoolean)this         ; else throw new RuntimeException("Invalid Data.asBoolean()"); }
    public BACnetUnsigned         asUnsigned()         { if (this instanceof BACnetUnsigned)         return (BACnetUnsigned)this        ; else throw new RuntimeException("Invalid Data.asUnsigned()"); }
    public BACnetInteger          asInteger()          { if (this instanceof BACnetInteger)          return (BACnetInteger)this         ; else throw new RuntimeException("Invalid Data.asInteger()"); }
    public BACnetReal             asReal()             { if (this instanceof BACnetReal)             return (BACnetReal)this            ; else throw new RuntimeException("Invalid Data.asReal()"); }
    public BACnetDouble           asDouble()           { if (this instanceof BACnetDouble)           return (BACnetDouble)this          ; else throw new RuntimeException("Invalid Data.asDouble()"); }
    public BACnetOctetString      asOctetString()      { if (this instanceof BACnetOctetString)      return (BACnetOctetString)this     ; else throw new RuntimeException("Invalid Data.asOctetString()"); }
    public BACnetCharacterString  asCharacterString()  { if (this instanceof BACnetCharacterString)  return (BACnetCharacterString)this ; else throw new RuntimeException("Invalid Data.asCharacterString()"); }
    public BACnetBitString        asBitString()        { if (this instanceof BACnetBitString)        return (BACnetBitString)this       ; else throw new RuntimeException("Invalid Data.asBitString()"); }
    public BACnetEnumerated       asEnumerated()       { if (this instanceof BACnetEnumerated)       return (BACnetEnumerated)this      ; else throw new RuntimeException("Invalid Data.asEnumerated()"); }
    public BACnetDate             asDate()             { if (this instanceof BACnetDate)             return (BACnetDate)this            ; else throw new RuntimeException("Invalid Data.asDate()"); }
    public BACnetTime             asTime()             { if (this instanceof BACnetTime)             return (BACnetTime)this            ; else throw new RuntimeException("Invalid Data.asTime()"); }
    public BACnetObjectIdentifier asObjectIdentifier() { if (this instanceof BACnetObjectIdentifier) return (BACnetObjectIdentifier)this; else throw new RuntimeException("Invalid Data.asObjectIdentifier()"); }
    public BACnetARRAY            asArray()            { if (this instanceof BACnetARRAY)            return (BACnetARRAY)this           ; else throw new RuntimeException("Invalid Data.asArray()"); }
    public BACnetLIST             asList()             { if (this instanceof BACnetLIST)             return (BACnetLIST) this           ; else throw new RuntimeException("Invalid Data.asList()"); }
  //public BACnetConstructed      asConstructed()      { if (this instanceof BACnetConstructed)      return (BACnetConstructed)this     ; else throw new RuntimeException("Invalid asConstructed()"); }

}
