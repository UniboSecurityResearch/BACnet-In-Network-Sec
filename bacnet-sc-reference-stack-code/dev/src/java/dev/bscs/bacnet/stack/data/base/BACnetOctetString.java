// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.stack.data.base;

import dev.bscs.bacnet.stack.Failure;
import dev.bscs.common.Formatting;

public class BACnetOctetString extends BACnetData {

    public byte[] value;

    public BACnetOctetString(byte[] value) { this.value = new byte[value.length]; System.arraycopy(value,0,this.value,0,value.length); }

    public BACnetOctetString(String value) throws Failure.Error { this.value = Formatting.fromHex(value); }

    public int  getDataType()          { return BACnetDataType.OCTET_STRING; }

    public String toString()  { return Formatting.toHex(value); }


}
