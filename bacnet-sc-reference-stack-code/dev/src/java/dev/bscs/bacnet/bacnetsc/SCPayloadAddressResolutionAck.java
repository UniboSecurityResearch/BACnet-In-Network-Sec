// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.bacnetsc;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * A data object that holds a payload of a Address-Resolution-ACK message, as defined in YY.2.7.
 * It's just a possibly empty array of strings.
 * This includes methods to parse from, and generate to, byte buffers.
 * @author drobin
 */
public class SCPayloadAddressResolutionAck {

    public String[] urls = null;

    public SCPayloadAddressResolutionAck(String[] urls) {
        this.urls = urls;
    }

    public SCPayloadAddressResolutionAck(byte[] payload) {
        parse(payload);
    }

    public void parse(byte[] payload) {
        urls = payload == null? new String[0] : new String(payload, StandardCharsets.UTF_8).split(" ");
    }

    public void parse(ByteBuffer buf) {
        parse(new byte[buf.remaining()]);
    }

    public byte[] generate() {
        return String.join(" ",urls).getBytes(StandardCharsets.UTF_8);
    }

    public void generate(ByteBuffer buf) {
        buf.put(generate());
    }

    public String toString() {
        return  "("+String.join(" ",urls)+")";
    }

}
