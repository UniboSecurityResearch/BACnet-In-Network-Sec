// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.stack.services;

import dev.bscs.bacnet.stack.*;
import dev.bscs.bacnet.stack.constants.UnconfirmedServiceChoice;
import dev.bscs.bacnet.stack.data.BACnetObjectType;
import dev.bscs.bacnet.stack.data.base.BACnetObjectIdentifier;
import dev.bscs.bacnet.stack.objects.DeviceObject;
import dev.bscs.bacnet.stack.objects.DeviceObjectProperties;
import dev.bscs.common.Formatting;
import dev.bscs.common.Log;

public class IAmService {

    private static final BACnetLog log = new BACnetLog(IAmService.class);

    public static void request(Device device, int dnet, byte[] dadr) {
        try {
            DeviceObject deviceObject = device.deviceObject;
            DeviceObjectProperties deviceProperties = deviceObject.properties;
            ASNBuffer response = new ASNBuffer(100);
            response.writeObjectIdentitfier(deviceObject.objectIdentifier);
            response.writeUnsigned(deviceProperties.maxAPDULengthAccepted);
            response.writeEnumerated(deviceObject.segmentationSupported);
            response.writeUnsigned(deviceProperties.vendorIdentifier);
            response.flip();
            APDU apdu = new APDU(APDU.UNCONF_REQ, UnconfirmedServiceChoice.IA, response);
            device.applicationLayer.unconfirmedRequest(dnet, dadr, apdu);
        }
        catch (Failure e) {
            log.implementation(device,"I-AM","request failed! "+e);
        }
    }

    public static void indication(Device device, int snet, byte[] sadr, APDU apdu, AuthData auth) {
        ASNBuffer serviceAck = apdu.serviceRequest;
        try {
            Binding binding = parse(snet,sadr,serviceAck);
            device.applicationLayer.addAddressBinding(binding, auth);
        }
        catch (Failure e) {
            log.error(device,"I-AM","Malformed I-Am from "+snet+":"+ Formatting.toMac(sadr)+" ["+Formatting.toHex(serviceAck.getBytes())+"]");
        }
    }

    public static Binding parse(int snet, byte[] sadr, ASNBuffer serviceAck) throws Failure {
        Binding binding = new Binding();
        binding.dnet                   = snet;
        binding.dadr                   = sadr;
        binding.instance               = serviceAck.readObjectIdentitfier()&0x3FFFFF;
        binding.maxAPDULengthAccepted  = serviceAck.readUnsigned();
        binding.segmentationSupported  = serviceAck.readEnumerated();
        binding.vendorID               = (int)serviceAck.readUnsigned();
        return binding;
    }

}
