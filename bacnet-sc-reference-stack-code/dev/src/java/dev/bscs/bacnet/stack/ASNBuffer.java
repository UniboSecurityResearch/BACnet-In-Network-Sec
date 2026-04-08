// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.stack;

import dev.bscs.bacnet.stack.constants.AbortReason;
import dev.bscs.bacnet.stack.constants.ErrorClass;
import dev.bscs.bacnet.stack.constants.ErrorCode;
import dev.bscs.bacnet.stack.constants.RejectReason;
import dev.bscs.bacnet.stack.data.base.*;
import dev.bscs.common.Formatting;

import java.math.BigInteger;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * An extension of the ByteBuffer interface that provides BACnet ASN encoding and decoding.
 * The familiar ByteBuffer methods: limit(), mark(), reset(), flip(), and remaining() all act as expected.
 * @author drobin
 */
public class ASNBuffer  {

    private ByteBuffer buf;
    private boolean    tagRead;
    private int        tagClass           = -1;
    private int        tagNumber          = -1;
    private int        tagLengthValueType = -1;
    private int        tagLength          = -1;

    public ASNBuffer(ByteBuffer buf)  { this.buf = buf; }

    public ASNBuffer(byte[] buf)      { this.buf = ByteBuffer.wrap(buf); }

    public ASNBuffer(int size)        { this.buf = ByteBuffer.allocate(size); }

    public ASNBuffer()                { this.buf = ByteBuffer.allocate(1497); }

    // These allow access to the same-named methods of the underlying ByteBuffer, q.v., for restarting, marking, flipping, etc.
    public int     limit()            { return buf.limit(); }
    public void    mark()             { buf.mark(); }
    public void    reset()            { buf.reset(); }
    public void    flip()             { buf.flip(); }
    public int     remaining()        { return buf.remaining(); }

    public String  toString()          { return "ASN:"+Formatting.toHex(getBytes()); }

    public byte[]  getBytes()          {
        byte[] bytes = new byte[buf.limit()];
        System.arraycopy(buf.array(),0,bytes,0,buf.limit());
        return bytes;
    }

    public void    peekTag() throws Failure.Reject {
        if (!tagRead) readTag();
    }

    public boolean peekContextTag(int number) throws Failure.Reject {
        peekTag();
        return tagClass == 1 && tagNumber == number && tagLengthValueType <= 6;
    }

    public boolean peekOpenTag(int context) throws Failure.Reject {
        peekTag();
        return tagClass == 1 && tagNumber == context && tagLengthValueType == 6;
    }

    public boolean peekCloseTag(int context) throws Failure.Reject {
        peekTag();
        return tagClass == 1 && tagNumber == context && tagLengthValueType == 7;
    }

    public boolean peekCloseTag() throws Failure.Reject {  // any context
        peekTag();
        return tagClass == 1 && tagLengthValueType == 7;
    }

    public void readApplicationTag(int number) throws Failure.Reject {
        peekTag();
        if (tagClass != 0 || tagNumber != number) throw new Failure.Reject(RejectReason.INVALID_TAG,"At octet #%d expecting application tag type %d found %d", buf.position(), number, tagNumber );
        tagRead = false; // arms trigger for next read/peek
    }

    public void readContextTag(int context) throws Failure.Reject {
        peekTag();
        if (tagClass != 1 || tagNumber != context) throw new Failure.Reject(RejectReason.INVALID_TAG,"At octet #%d expecting context tag %d found %d", buf.position(), context, tagNumber );
        tagRead = false; // arms trigger for next read/peek
    }

    public void writeOpenTag(int context) throws Failure.Abort {
        writeTag(context,1,6);
    }
    public void writeCloseTag(int context) throws Failure.Abort {
        writeTag(context,1,7);
    }

    public void readOpenTag(int context) throws Failure.Reject {
        peekTag();
        if (tagClass != 1 || tagNumber != context || tagLengthValueType != 6) throw new Failure.Reject(RejectReason.INVALID_TAG,"At octet #%d expecting context open tag %d found %d", buf.position(), context, tagNumber );
        tagRead = false; // arms trigger for next read/peek
    }
    public void readCloseTag(int context) throws Failure.Reject {
        peekTag();
        if (tagClass != 1 || tagNumber != context || tagLengthValueType != 7) throw new Failure.Reject(RejectReason.INVALID_TAG,"At octet #%d expecting context close tag %d found %d", buf.position(), context, tagNumber );
        tagRead = false; // arms trigger for next read/peek
    }

    public void write(BACnetData data) throws Failure.Abort,Failure.Error {
        switch (data.getDataType()) {
            case BACnetDataType.NULL:              writeNull(); break;
            case BACnetDataType.BOOLEAN:           writeBoolean(data.asBoolean().value); break;
            case BACnetDataType.UNSIGNED:          writeUnsigned(data.asUnsigned().value); break;
            case BACnetDataType.INTEGER:           writeInteger(data.asInteger().value); break;
            case BACnetDataType.REAL:              writeReal(data.asReal().value); break;
            case BACnetDataType.DOUBLE:            writeDouble(data.asDouble().value); break;
            case BACnetDataType.OCTET_STRING:      writeOctetString(data.asOctetString().value); break;
            case BACnetDataType.CHARACTER_STRING:  writeCharacterString(data.asCharacterString().value); break;
            case BACnetDataType.BIT_STRING:        writeBitString(data.asBitString().value); break;
            case BACnetDataType.ENUMERATED:        writeEnumerated(data.asEnumerated().value); break;
            case BACnetDataType.DATE:              writeDate(data.asDate().value); break;
            case BACnetDataType.TIME:              writeTime(data.asTime().value); break;
            case BACnetDataType.OBJECT_IDENTIFIER: writeObjectIdentitfier(data.asObjectIdentifier().value); break;
            case BACnetDataType.ARRAY:             for (BACnetData member : data.asArray().value) write(member); break;
            case BACnetDataType.LIST:              for (BACnetData member : data.asList().value) write(member); break;
            default: throw new Failure.Error(ErrorClass.DEVICE, ErrorCode.INTERNAL_ERROR,"Implementation: ASNBuffer.write() application tag %d not valid", tagNumber );
        }
    }

    public BACnetData readPrimitive() throws Failure.Reject,Failure.Error {
        peekTag();
        if (tagClass != 0) throw new Failure.Error(ErrorClass.DEVICE, ErrorCode.INTERNAL_ERROR,"ASNBuffer.readPrimitive() Unsupported BACnetData. Expecting application tagged primitive data, found tag class %d number %d", tagClass, tagNumber);
        switch (tagNumber) {
            case BACnetDataType.NULL:              readNull(); return new BACnetNull();
            case BACnetDataType.BOOLEAN:           return new BACnetBoolean(readBoolean());
            case BACnetDataType.UNSIGNED:          return new BACnetUnsigned(readUnsigned());
            case BACnetDataType.INTEGER:           return new BACnetInteger(readInteger());
            case BACnetDataType.REAL:              return new BACnetReal(readReal());
            case BACnetDataType.DOUBLE:            return new BACnetDouble(readDouble());
            case BACnetDataType.OCTET_STRING:      return new BACnetOctetString(readOctetString());
            case BACnetDataType.BIT_STRING:        return new BACnetBitString(readBitString());
            case BACnetDataType.CHARACTER_STRING:  return new BACnetCharacterString(readCharacterString());
            case BACnetDataType.ENUMERATED:        return new BACnetEnumerated(readEnumerated());
            case BACnetDataType.DATE:              return new BACnetDate(readDate());
            case BACnetDataType.TIME:              return new BACnetTime(readTime());
            case BACnetDataType.OBJECT_IDENTIFIER: return new BACnetObjectIdentifier(readObjectIdentitfier());
            default: throw new Failure.Error(ErrorClass.DEVICE, ErrorCode.INVALID_DATA_TYPE,"ASNBuffer.readPrimitive() application tag %d not valid", tagNumber);
        }
    }

    public BACnetData readWrappedPrimitive(int context) throws Failure.Reject,Failure.Error {
        readOpenTag(context);
        BACnetData data = readPrimitive();
        readCloseTag(context);
        return data;
    }

    ///////////////////  NULL  ///////////////////////////

    public void readNull() throws Failure.Reject {
        readApplicationTag(BACnetDataType.NULL);
        if (tagLength != 0) throw new Failure.Reject(RejectReason.INVALID_TAG,"Bad tag: wrong length for Null");
    }
    public void readNull(int context) throws Failure.Reject {
        readContextTag(context);
        if (tagLength != 0) throw new Failure.Reject(RejectReason.INVALID_TAG,"Bad tag: wrong length for Null");
    }
    public void writeNull() throws Failure.Abort {
        writeTag(BACnetDataType.NULL,0,0);
    }
    public void writeNull(int context) throws Failure.Abort {
        writeTag(context,1,0);
    }

    ///////////////////  BOOLEAN  ///////////////////////////

    public boolean readBoolean() throws Failure.Reject {
        readApplicationTag(BACnetDataType.BOOLEAN);
        return (tagLengthValueType & 1) != 0;
    }
    public boolean readBoolean(int context) throws Failure.Reject {
        readContextTag(context);
        return get() != 0;
    }
    public boolean readBoolean(int context, boolean defaultValue) throws Failure.Reject {
        return peekContextTag(context)? readBoolean(context) : defaultValue;
    }
    public void writeBoolean(boolean value) throws Failure.Abort {
        writeTag(BACnetDataType.BOOLEAN,0,value?1:0);
    }
    public void writeBoolean(int context, boolean value) throws Failure.Abort {
        writeTag(context,1,value?1:0);
    }

    ///////////////////  UNSIGNED  ///////////////////////////

    public long readUnsigned() throws Failure.Reject {
        readApplicationTag(BACnetDataType.UNSIGNED);
        return _readUnsigned();
    }
    public long readUnsigned(int context) throws Failure.Reject {
        readContextTag(context);
        return _readUnsigned();
    }
    public long readUnsigned(int context, long defaultValue) throws Failure.Reject {
        if (peekContextTag(context)) return readUnsigned(context);
        else return defaultValue;
    }
    public void writeUnsigned(long value) throws Failure.Abort {
        int length = _getUnsignedLength(value);
        writeTag(BACnetDataType.UNSIGNED,0,length);
        for (int i=length-1; i>=0; i--) put(value>>(i*8));
    }
    public void writeUnsigned(int context, long value) throws Failure.Abort {
        int length = _getUnsignedLength(value);
        writeTag(context,1,length);
        for (int i=length-1; i>=0; i--) put(value>>(i*8));
    }

    ///////////////////  INTEGER  ///////////////////////////

    public long readInteger() throws Failure.Reject {
        readApplicationTag(BACnetDataType.INTEGER);
        return _readSigned();
    }
    public long readInteger(int context) throws Failure.Reject {
        readContextTag(context);
        return _readSigned();
    }
    public long readInteger(int context, long defaultValue) throws Failure.Reject {
        if (peekContextTag(context)) return readInteger(context);
        else return defaultValue;
    }
    public void writeInteger(long value) throws Failure.Abort {
        int length = _getSignedLength(value);
        writeTag(BACnetDataType.INTEGER,0,length);
        for (int i=length-1; i>=0; i--) put(value>>(i*8));
    }
    public void writeInteger(int context, long value) throws Failure.Abort {
        int length = _getSignedLength(value);
        writeTag(context,1,length);
        for (int i=length-1; i>=0; i--) put(value>>(i*8));
    }

    ///////////////////  REAL  ///////////////////////////

    public float readReal() throws Failure.Reject {
        readApplicationTag(BACnetDataType.REAL);
        if (tagLength != 4) throw new Failure.Reject(RejectReason.INVALID_TAG,"Bad tag: wrong length for Real");
        return getFloat();
    }
    public float readReal(int context) throws Failure.Reject {
        readContextTag(context);
        if (tagLength != 4) throw new Failure.Reject(RejectReason.INVALID_TAG,"Bad tag: wrong length for Real");
        return getFloat();
    }
    public float readReal(int context, float defaultValue) throws Failure.Reject {
        if (peekContextTag(context)) return readReal(context);
        else return defaultValue;
    }
    public void writeReal(float value) throws Failure.Abort {
        writeTag(BACnetDataType.REAL,0,4);
        int bits = Float.floatToIntBits(value);
        for (int i=3; i>=0; i--) put(bits>>(i*8));
    }
    public void writeReal(int context, float value) throws Failure.Abort {
        writeTag(context,1,4);
        int bits = Float.floatToIntBits(value);
        for (int i=3; i>=0; i--) put(bits>>(i*8));
    }

    ///////////////////  DOUBLE  ///////////////////////////

    public double readDouble() throws Failure.Reject {
        readApplicationTag(BACnetDataType.DOUBLE);
        if (tagLength != 8) throw new Failure.Reject(RejectReason.INVALID_TAG,"Bad tag: wrong length for Double");
        return getDouble();
    }
    public double readDouble(int context) throws Failure.Reject {
        readContextTag(context);
        if (tagLength != 8) throw new Failure.Reject(RejectReason.INVALID_TAG,"Bad tag: wrong length for Double");
        return getDouble();
    }
    public double readDouble(int context, double defaultValue) throws Failure.Reject {
        if (peekContextTag(context)) return readDouble(context);
        else return defaultValue;
    }
    public void writeDouble(double value) throws Failure.Abort {
        writeTag(BACnetDataType.DOUBLE,0,8);
        long bits = Double.doubleToLongBits(value);
        for (int i=7; i>=0; i--) put(bits>>(i*8));
    }
    public void writeDouble(int context, double value) throws Failure.Abort {
        writeTag(context,1,8);
        long bits = Double.doubleToLongBits(value);
        for (int i=7; i>=0; i--) put(bits>>(i*8));
    }

    ///////////////////  OCTET STRING  ///////////////////////////

    public byte[] readOctetString() throws Failure.Reject {
        readApplicationTag(BACnetDataType.OCTET_STRING);
        return _readBytes();
    }
    public byte[] readOctetString(int context) throws Failure.Reject {
        readContextTag(context);
        return _readBytes();
    }
    public byte[] readOctetString(int context, byte[] defaultValue) throws Failure.Reject {
        if (peekContextTag(context)) return readOctetString(context);
        else return defaultValue;
    }
    public void writeOctetString(byte[] value) throws Failure.Abort {
        writeTag(BACnetDataType.OCTET_STRING,0,value.length);
        put(value);
    }
    public void writeOctetString(int context, byte[] value) throws Failure.Abort {
        writeTag(context,1,value.length);
        put(value);
    }

    ///////////////////  CHARACTER STRING  ///////////////////////////


    public String readCharacterString() throws Failure.Reject,Failure.Error {
        readApplicationTag(BACnetDataType.CHARACTER_STRING);
        return _readCharacterString();
    }
    public String readCharacterString(int context) throws Failure.Reject,Failure.Error {
        readContextTag(context);
        return _readCharacterString();
    }
    public String readCharacterString(int context, String defaultValue) throws Failure.Reject,Failure.Error {
        if (peekContextTag(context)) return readCharacterString(context);
        else return defaultValue;
    }
    public void writeCharacterString(String value) throws Failure.Abort {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeTag(BACnetDataType.CHARACTER_STRING,0,bytes.length+1);
        put(0);
        put(bytes);
    }
    public void writeCharacterString(int context, String value) throws Failure.Abort {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeTag(context,1,bytes.length+1);
        put(0);
        put(bytes);
    }

    ///////////////////  BIT STRING  ///////////////////////////

    public byte[] readBitString() throws Failure.Reject {
        readApplicationTag(BACnetDataType.BIT_STRING);
        return _readBytes();
    }
    public byte[] readBitString(int context) throws Failure.Reject {
        readContextTag(context);
        return _readBytes();
    }
    public byte[] readBitString(int context, byte[] defaultValue) throws Failure.Reject {
        if (peekContextTag(context)) return readBitString(context);
        else return defaultValue;
    }
    public void writeBitString(byte[] value) throws Failure.Abort {
        writeTag(BACnetDataType.BIT_STRING,0,value.length);
        put(value);
    }
    public void writeBitString(int context, byte[] value) throws Failure.Abort {
        writeTag(context,1,value.length);
        put(value);
    }

    ///////////////////  ENUMERATED  ///////////////////////////

    public int readEnumerated() throws Failure.Reject {
        readApplicationTag(BACnetDataType.ENUMERATED);
        return (int)_readUnsigned();
    }
    public int readEnumerated(int context) throws Failure.Reject {
        readContextTag(context);
        return (int)_readUnsigned();
    }
    public int readEnumerated(int context, int defaultValue) throws Failure.Reject {
        if (peekContextTag(context)) return readEnumerated(context);
        else return defaultValue;
    }
    public void writeEnumerated(int value) throws Failure.Abort {
        int length = _getUnsignedLength(value);
        writeTag(BACnetDataType.ENUMERATED,0,length);
        for (int i=length-1; i>=0; i--) put(value>>(i*8));
    }
    public void writeEnumerated(int context, int value) throws Failure.Abort {
        int length = _getUnsignedLength(value);
        writeTag(context,1,length);
        for (int i=length-1; i>=0; i--) put(value>>(i*8));
    }

    ///////////////////  DATE  ///////////////////////////

    public byte[] readDate() throws Failure.Reject {
        readApplicationTag(BACnetDataType.DATE);
        return _readBytes();
    }
    public byte[] readDate(int context) throws Failure.Reject {
        readContextTag(context);
        return _readBytes();
    }
    public byte[] readDate(int context, byte[] defaultValue) throws Failure.Reject {
        if (peekContextTag(context)) return readDate(context);
        else return defaultValue;
    }
    public void writeDate(byte[] value) throws Failure.Abort {
        writeTag(BACnetDataType.DATE,0,value.length);
        put(value);
    }
    public void writeDate(int context, byte[] value) throws Failure.Abort {
        writeTag(context,1,value.length);
        put(value);
    }

    ///////////////////  TIME  ///////////////////////////


    public byte[] readTime() throws Failure.Reject {
        readApplicationTag(BACnetDataType.TIME);
        return _readBytes();
    }
    public byte[] readTime(int context) throws Failure.Reject {
        readContextTag(context);
        return _readBytes();
    }
    public byte[] readTime(int context, byte[] defaultValue) throws Failure.Reject {
        if (peekContextTag(context)) return readTime(context);
        else return defaultValue;
    }
    public void writeTime(byte[] value) throws Failure.Abort {
        writeTag(BACnetDataType.TIME,0,value.length);
        put(value);
    }
    public void writeTime(int context, byte[] value) throws Failure.Abort {
        writeTag(context,1,value.length);
        put(value);
    }

    ///////////////////  OBJECT IDENTIFIER  ///////////////////////////

    public int readObjectIdentitfier() throws Failure.Reject {
        readApplicationTag(BACnetDataType.OBJECT_IDENTIFIER);
        return (int)_readUnsigned();
    }
    public int readObjectIdentitfier(int context) throws Failure.Reject{
        readContextTag(context);
        return (int)_readUnsigned();
    }
    public int readObjectIdentitfier(int context, int defaultValue) throws Failure.Reject {
        if (peekContextTag(context)) return readObjectIdentitfier(context);
        else return defaultValue;
    }
    public void writeObjectIdentitfier(int value) throws Failure.Abort {
        writeTag(BACnetDataType.OBJECT_IDENTIFIER,0,4);
        for (int i=3; i>=0; i--) put(value>>(i*8));
    }
    public void writeObjectIdentitfier(int context, int value) throws Failure.Abort {
        writeTag(context,1,4);
        for (int i=3; i>=0; i--) put(value>>(i*8));
    }
    public void writeObjectIdentitfier(BACnetObjectIdentifier value)  throws Failure.Abort {
        writeObjectIdentitfier(value.asInt());
    }
    public void writeObjectIdentitfier(int context, BACnetObjectIdentifier value) throws Failure.Abort {
        writeObjectIdentitfier(context,value.asInt());
    }

    ///////////////////  ERROR PAIR  ///////////////////////////

    public void writeError(int errorClass, int errorCode) throws Failure.Abort {
        writeEnumerated(errorClass);
        writeEnumerated(errorCode);
    }


    /////////////////// PRIVATE HELPERS /////////////////////////////////

    private void readTag() throws Failure.Reject {
        //|-----|-----|-----|-----|-----|-----|-----|-----|
        //|      Tag Number       |Class|Length/Value/Type|
        //|-----|-----|-----|-----|-----|-----|-----|-----|
        if (buf.remaining()!=0) {
            int first = get();
            tagClass = (first >> 3) & 0x01;
            tagNumber = first >> 4;
            if (tagNumber == 15) tagNumber = get(); // extended tag is in following octet
            tagLengthValueType = first & 0x07;
            if (tagClass == 0 && tagNumber == 1) tagLength = 0; // application boolean has no length and value is *in* tagLengthValueType
            else {
                tagLength = tagLengthValueType;  // 1-4 is normal, 5 is extended
                if (tagLengthValueType == 5) {
                    tagLength = get();
                    if (tagLength == 254) {
                        tagLength = get();
                        tagLength = (tagLength << 8) | get();
                    } else if (tagLength == 255) {
                        tagLength = get();
                        tagLength = (tagLength << 8) | get();
                        tagLength = (tagLength << 8) | get();
                        tagLength = (tagLength << 8) | get();
                    }
                }
            }
        }
        else {   // out of buffer, no tag
            tagClass = tagNumber = tagLengthValueType = tagLength = -1;
        }
        tagRead = true;
    }

    private void writeTag(int tagNumber, int tagClass, int tagLengthValueType) throws Failure.Abort {
        //|-----|-----|-----|-----|-----|-----|-----|-----|
        //|      Tag Number       |Class|Length/Value/Type|
        //|-----|-----|-----|-----|-----|-----|-----|-----|
        int firstOctet      = 0;
        int numberExtension = 0;
        int lengthExtension = 0;
        if (tagClass != 0) firstOctet |= 0b00001000;
        if (tagNumber > 14) {
            firstOctet |= 0b11110000;
            numberExtension = tagNumber;
        }
        else firstOctet |= tagNumber << 4;
        if (tagClass == 0 && tagNumber == 1) { // application boolean has no body, so leave length = 0
            if (tagLengthValueType != 0) firstOctet |= 0x01; // application boolean has value in low order bit
        }
        else if (tagLengthValueType < 5) {
            firstOctet |= tagLengthValueType; // length 4 or less are in first octet
        }
        else if (tagClass == 1 && (tagLengthValueType == 6 || tagLengthValueType == 7 )) {
            firstOctet |= tagLengthValueType; // open 6 and close 7 are also in thirst octet
        }
        else {
            firstOctet |= 0b00000101; // L/V/T of 5 means lenth extension
            lengthExtension = tagLengthValueType;
        }
        // finally write out the stuff
        put(firstOctet);
        if (numberExtension > 0) put(numberExtension);
        if (lengthExtension > 65535) {
            put(255);
            put(tagLengthValueType>>24);
            put(tagLengthValueType>>16);
            put(tagLengthValueType>>8);
            put(tagLengthValueType);
        }
        else if (lengthExtension > 254) {
            put(254);
            put(tagLengthValueType>>8);
            put(tagLengthValueType);
        }
        else if (lengthExtension > 0) {
            put(tagLengthValueType);
        }
    }

    private long _readUnsigned() throws Failure.Reject {
        long value = 0;
        for (int i = 0; i < tagLength; i++) value = (value << 8) | (get());
        return value;
    }
    private long _readSigned() throws Failure.Reject {
        BigInteger bi = new BigInteger(_readBytes());
        return bi.intValue();
    }
    private byte[] _readBytes() throws Failure.Reject {
        byte[] value = new byte[tagLength];
        for (int i = 0; i < tagLength; i++) value[i] = (byte)get();
        return value;
    }
    private String _readCharacterString() throws Failure.Reject,Failure.Error {
        int charSet = get();
        if (charSet != 0) throw new Failure.Error(ErrorClass.PROPERTY, ErrorCode.CHARACTER_SET_NOT_SUPPORTED);
        byte[] bytes = new byte[tagLength-1];
        get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
    private int _getUnsignedLength(long value) {
        return (value < 256)? 1 : (value < 65536)? 2 : (value < 16777216)? 3 : 4;
    }
    private int _getSignedLength(long value) {
        return  (value < 127 && value > -128)? 1 : (value < 32767 && value > -32768)? 2 : (value < 8388607 && value > -8388608)? 3 : 4;
    }

    ////////////// centralized buffer wrappers to catch over/underruns ////////////////

    private void put(long b) throws Failure.Abort {
        try { buf.put((byte)b); }
        catch (BufferOverflowException e) { throw new Failure.Abort(AbortReason.BUFFER_OVERFLOW); }
    }
    private void put(byte[] ba) throws Failure.Abort {
        try { buf.put(ba); }
        catch (BufferOverflowException e) { throw new Failure.Abort(AbortReason.BUFFER_OVERFLOW); }
    }
    private int get() throws Failure.Reject {
        try { return buf.get()&0xFF; }
        catch (BufferUnderflowException e) { throw new Failure.Reject(RejectReason.INVALID_TAG); }
    }
    private int getSigned() throws Failure.Reject {
        try { return buf.get(); }
        catch (BufferUnderflowException e) { throw new Failure.Reject(RejectReason.INVALID_TAG); }
    }
    private float getFloat() throws Failure.Reject {
        try { return buf.getFloat(); }
        catch (BufferUnderflowException e) { throw new Failure.Reject(RejectReason.INVALID_TAG); }
    }
    private double getDouble() throws Failure.Reject {
        try { return buf.getDouble(); }
        catch (BufferUnderflowException e) { throw new Failure.Reject(RejectReason.INVALID_TAG); }
    }
    private void get(byte[] ba) throws Failure.Reject {
        try { buf.get(ba); }
        catch (BufferUnderflowException e) { throw new Failure.Reject(RejectReason.INVALID_TAG); }
    }

}
