// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.stack.data.base;

import dev.bscs.bacnet.stack.Failure;
import dev.bscs.bacnet.stack.constants.ErrorClass;
import dev.bscs.bacnet.stack.constants.ErrorCode;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

public class BACnetDate extends BACnetData {

    public byte[] value = new byte[4];

    public BACnetDate(int year, int month, int day) {  this(year,month,day, -1); }

    public BACnetDate(String value) throws Failure.Error {
        try {
            LocalDate date = LocalDate.parse(value);
            this.value[0] = (byte)(date.getYear()-1900);
            this.value[1] = (byte)date.getMonth().getValue();
            this.value[2] = (byte)date.getDayOfMonth();
            this.value[3] = (byte)date.getDayOfWeek().getValue();
        }
        catch (DateTimeParseException e) { throw new Failure.Error(ErrorClass.PROPERTY, ErrorCode.VALUE_OUT_OF_RANGE, e.getLocalizedMessage()); }
    }

    public BACnetDate(int year, int month, int day, int dow) {
        this.value[0] = (byte)year;
        this.value[1] = (byte)month;
        this.value[2] = (byte)day;
        this.value[3] = (byte)(dow == -1? LocalDate.of(year, month,day).getDayOfWeek().getValue() : dow); // if not given dow, compute it
    }

    public BACnetDate(LocalDate date) {
        this.value[0] = (byte)(date.getYear()-1900);
        this.value[1] = (byte)date.getMonth().getValue();
        this.value[2] = (byte)date.getDayOfMonth();
        this.value[3] = (byte)date.getDayOfWeek().getValue();
    }


    public BACnetDate(byte[] v) { value = new byte[v.length]; System.arraycopy(v,0,value,0,v.length);}

    public int  getDataType()  { return BACnetDataType.DATE; }

    public String toString()   { return (value[0]+1900)+"-"+value[1]+"-"+value[2]; }

}
