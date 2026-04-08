// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.stack.data.base;

import dev.bscs.bacnet.stack.Failure;
import dev.bscs.bacnet.stack.constants.ErrorClass;
import dev.bscs.bacnet.stack.constants.ErrorCode;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;

public class BACnetTime extends BACnetData {

    public byte[] value = new byte[4];

    public BACnetTime(String value) throws Failure.Error {
        try {
            LocalTime time = LocalTime.parse(value);
            this.value[0] = (byte)time.getHour();
            this.value[1] = (byte)time.getMinute();
            this.value[2] = (byte)time.getSecond();
            this.value[3] = (byte)(time.getNano()/10000000);
        }
        catch (DateTimeParseException e) { throw new Failure.Error(ErrorClass.PROPERTY, ErrorCode.VALUE_OUT_OF_RANGE, e.getLocalizedMessage()); }
    }

    public BACnetTime(int hour, int minute, int second, int hundredths) {
        this.value[0] = (byte)hour;
        this.value[1] = (byte)minute;
        this.value[2] = (byte)second;
        this.value[3] = (byte)hundredths;
    }

    public BACnetTime(LocalTime time) {
        this.value[0] = (byte)time.getHour();
        this.value[1] = (byte)time.getMinute();
        this.value[2] = (byte)time.getSecond();
        this.value[3] = (byte)(time.getNano()/10000000);
    }

    public BACnetTime(byte[] v) { value = new byte[v.length]; System.arraycopy(v,0,value,0,v.length);}

    public int  getDataType()  { return BACnetDataType.TIME; }

    public String toString()   { return value[0]+":"+value[1]+":"+value[2]+"."+value[3]; }

}
