// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.bacnet.bacnetip;

import dev.bscs.bacnet.stack.*;
import dev.bscs.common.*;
import dev.bscs.events.EventListener;
import dev.bscs.events.EventLoop;
import dev.bscs.events.EventType;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static dev.bscs.events.EventLoop.EVENT_MAINTENANCE;

/**
 * An implementation of the Datalink interface for BACnet/IPv4, to be attached to a {@link NetworkLayer}.
 * The configuration of this datalink is controlled by the ip.xxx configuration properties.
 * @author drobin
 */
public class IPDatalink implements Datalink, EventListener {

    private static BACnetLog log = new BACnetLog(IPDatalink.class);

    public static final int  MAX_BVLC_LENGTH  = 1497 + 10;
    public static final int  MIN_FOREIGN_REFRESH = 20; // barely sane minimum value (really should be much more!)

    private String                name;
    public  IPDatalinkProperties  properties;
    private Device                device;
    private int                   network;
    private InetSocketAddress     bindSocketAddress;
    private DatagramSocket        bindSocket;
    private ReceiveThread         bindThread;
    private InetSocketAddress     broadcastSocketAddress;
    private DatagramSocket        broadcastSocket;
    private ReceiveThread         broadcastThread;
    private Timer                 timer = new Timer();
    private InetSocketAddress     foreignSocketAddress;
    private Timer                 foreignTimer = new Timer();
    private State                 state = State.IDLE;
    private enum State            { IDLE, STARTED }

    // these are used internally to turn asynchronous method calls into queued state machine events
    private static final EventType EVENT_DATAGRAM_MESSAGE      = new EventType("datagram_message");
    private static final EventType EVENT_DATALINK_START        = new EventType("datalink_start");
    private static final EventType EVENT_DATALINK_STOP         = new EventType("datalink_stop");
    private static final EventType EVENT_DATALINK_STATE_CHANGE = new EventType("datalink_state_change");

    //////// constructors /////////

    public IPDatalink(String name, IPDatalinkProperties properties, Device device, int network)  {
        this.name        = name;
        this.properties  = properties;
        this.device      = device;
        this.network     = network;
        device.networkLayer.addDatalink(this);
    }

    @Override public void handleEvent(Object source, EventType eventType, Object... args) {
        switch(state) {
            case IDLE:
                if (eventType == EVENT_DATALINK_START) {
                    try {
                        InetAddress bindAddress = getInetAddrFor(properties.bindAddress); // resolve things like "en0" and "loopback" to actual address
                        bindSocketAddress = new InetSocketAddress(bindAddress,properties.bindPort);
                        bindSocket = new DatagramSocket(null);
                        bindSocket.setBroadcast(true);
                        bindSocket.setReuseAddress(true);
                        bindSocket.setSoTimeout(5000);
                        bindSocket.bind(bindSocketAddress);
                        bindThread = new ReceiveThread("IP Receive Thread",bindSocket);
                        bindThread.start();
                        broadcastSocketAddress = null; // temporarily null, acts as a "found" flag for the loop below
                        NetworkInterface networkInterface = NetworkInterface.getByInetAddress(bindAddress);
                        if (networkInterface != null) {
                            for (InterfaceAddress interfaceAddress : networkInterface.getInterfaceAddresses()) {
                                if (interfaceAddress.getAddress().equals(bindAddress)) {
                                    broadcastSocketAddress = new InetSocketAddress(interfaceAddress.getBroadcast(), properties.bindPort);
                                    log.info(device,name,"Detected broadcast address for " + properties.bindAddress + " is " + broadcastSocketAddress);
                                    break;
                                }
                            }
                        }
                        if (broadcastSocketAddress == null) broadcastSocketAddress = new InetSocketAddress(properties.bindPort); // give up and bind to *any* IP address - yuck
                        if (Configuration.isUnixBased()) { // that was easy, but unix-based systems need help receiving broadcasts, so we'll need to find the right thing to bind to for that
                            broadcastSocket = new DatagramSocket(null);
                            broadcastSocket.setBroadcast(true);
                            broadcastSocket.setReuseAddress(true);
                            broadcastSocket.setSoTimeout(5000);
                            broadcastSocket.bind(broadcastSocketAddress);
                            broadcastThread = new ReceiveThread("IP Broadcast Thread",broadcastSocket);
                            broadcastThread.start();
                        }
                        log.info(device,name,"IP bound to \"" + properties.bindAddress + "\" at "+ bindSocketAddress.getAddress()+":"+ properties.bindPort);
                        if (properties.foreignRefresh != 0) {
                            if (properties.foreignRefresh < MIN_FOREIGN_REFRESH) {
                                log.configuration(device,name,"Setting foreignRefresh to a minimum value of "+MIN_FOREIGN_REFRESH+" seconds (was "+properties.foreignRefresh+")");
                                properties.foreignRefresh = MIN_FOREIGN_REFRESH;
                            }
                            try {
                                InetAddress foreignAddress = getInetAddrFor(properties.foreignAddress);
                                foreignSocketAddress = new InetSocketAddress(foreignAddress, properties.foreignPort);
                            } catch (Exception e) {
                                log.configuration(device,name,"Could not parse foreign address \"" + properties.foreignAddress + "\":"+properties.foreignPort+" - "+e.getLocalizedMessage());
                                properties.foreignRefresh = 0; foreignSocketAddress = null; // disable until problem is fixed
                                // TODO how to indicate this other than the log?
                            }
                        }
                        setState(State.STARTED,"bound");
                        device.networkLayer.sendIAmRouterToNetworkForDirectlyConnectedNetworks(this);
                        device.networkLayer.sendNetworkNumberIs(this);
                    } catch (Exception e) {
                        log.configuration(device,name,"Could not bind to address \"" + properties.bindAddress + "\":"+properties.bindPort+" - "+e.getLocalizedMessage());
                        // TODO how to indicate this other than the log?
                    }
                }
                break;
            case STARTED:
                if (eventType == EVENT_DATALINK_STOP) {
                    if(bindThread != null)      { bindThread.stop = true;      bindThread.interrupt();      bindThread = null; }
                    if(broadcastThread != null) { broadcastThread.stop = true; broadcastThread.interrupt(); broadcastThread = null; }
                    if(bindSocket != null)      { bindSocket.close();       bindSocket = null; }
                    if(broadcastSocket != null) { broadcastSocket.close();  broadcastSocket = null; }
                    setState(State.IDLE,"stop() event hander");
                }
                else if (eventType == EVENT_DATAGRAM_MESSAGE) {
                    DatagramPacket packet = (DatagramPacket) args[0];
                    if (packet != null) receivePacket(packet);
                }
                else if (eventType == EVENT_MAINTENANCE){
                     if (properties.foreignRefresh != 0) {
                         if (foreignTimer.remaining() == 0) {
                             sendRegisterForeignDevice(foreignSocketAddress,properties.foreignRefresh);
                             foreignTimer.start(properties.foreignRefresh * 1000L);   // timer values are millis, property is seconds
                         }
                     }
                }
                break;
        }
    }

    //////// DataLink interface /////////

    @Override public NetworkLayer getNetworkLayer()             { return device.networkLayer; }
    @Override public void         setNetwork(int network)       { this.network = network; }
    @Override public int          getNetwork()                  { return network; }
    @Override public String       getName()                     { return name; }
    @Override public byte[]       getMac()                      { return toMac(bindSocketAddress.getAddress(), properties.bindPort); }
    @Override public String       getMacAsString()              { return Formatting.toIP(bindSocketAddress.getAddress().getAddress())+":"+properties.bindPort; }
    @Override public String       macToString(byte[] bytes)     { return Formatting.toIPAndPort(bytes); }

    @Override public boolean start() {
        log.debug(device,name,"start() called, emitting event");
        EventLoop.emit(this,this,EVENT_DATALINK_START);
        return true;
    }

    @Override public void stop() {
        log.debug(device,name,"stop() called, emitting event");
        EventLoop.emit(this,this,EVENT_DATALINK_STOP);
    }

    @Override public void close() {  // rude device shutdown
        log.debug(device,name,"close() called, emitting event");
        if(bindThread != null)      { bindThread.stop = true;      bindThread.interrupt();      bindThread = null; }
        if(broadcastThread != null) { broadcastThread.stop = true; broadcastThread.interrupt(); broadcastThread = null; }
        if(bindSocket != null)      { bindSocket.close();          bindSocket = null; }
        if(broadcastSocket != null) { broadcastSocket.close();     broadcastSocket = null; }
    }

    @Override public void   dlUnitdataRequest(byte[] da, byte[] npdu, int priority, boolean der, AuthData notUsed) {
        if (state != State.STARTED) {
            log.error(device,name,getMacAsString() + " NOT STARTED  dlUnitdataRequest()<-- dadr=" + macToString(da) + " {" + new NPDU(npdu) + "} [" + Formatting.toHex(npdu) + "]");
            return;
        }
        log.info(device,name,getMacAsString()+" dlUnitdataRequest()<-- dadr="+ macToString(da)+" {"+new NPDU(npdu)+"} ["+ Formatting.toHex(npdu)+"]");
        byte bvlcFunction;
        InetSocketAddress sockAddr;
        if (da == null || da.length == 0) {
            if (foreignSocketAddress != null) {
                bvlcFunction = BVLC_FUNC_DISTRIBUTE_BROADCAST_TO_NETWORK;
                sockAddr     = foreignSocketAddress;
            }
            else {
                bvlcFunction = BVLC_FUNC_ORIGINAL_BROADCAST;
                sockAddr     = broadcastSocketAddress;
            }
        }
        else {
            bvlcFunction = BVLC_FUNC_ORIGINAL_UNICAST;
            sockAddr     = new InetSocketAddress(getInetAddressOfMac(da), getPortOfMac(da));
        }
        byte[] bvlc = new byte[npdu.length + 4];
        ByteBuffer buf = ByteBuffer.wrap(bvlc);
        buf.put(BVLC_TYPE);
        buf.put(bvlcFunction);
        buf.putShort((short)bvlc.length);
        buf.put(npdu);
        DatagramPacket packet = new DatagramPacket(bvlc, bvlc.length, sockAddr);
        log.info(device,name,getMacAsString()+" sendPacket()<-- da="+ macToString(da) +" ["+ Formatting.toHex(packet.getData(),50)+"]"); // $$$
        try { bindSocket.send(packet); }
        catch (IOException ignored) {  } // $$$ do anything? nothing for now
    }

    public void   sendBVLCResult(byte[] da, int resultCode) {
        if (state != State.STARTED) {
            log.error(device,name,getMacAsString() + " NOT STARTED  sendBVLCResult()<-- dadr=" + macToString(da));
            return;
        }
        log.info(device,name,getMacAsString()+" sendBVLCResult()<-- dadr="+ macToString(da)+" code="+resultCode);
        byte[] bvlc = new byte[6]; // fixed size
        ByteBuffer buf = ByteBuffer.wrap(bvlc);
        buf.put(BVLC_TYPE);
        buf.put(BVLC_FUNC_RESULT);
        buf.putShort((short)bvlc.length);
        buf.putShort((short)resultCode);
        InetSocketAddress sockAddr = new InetSocketAddress(getInetAddressOfMac(da), getPortOfMac(da));
        DatagramPacket packet = new DatagramPacket(bvlc, bvlc.length, sockAddr);
        try { bindSocket.send(packet); }
        catch (IOException ignored) {  } // $$$ do anything? nothing for now
    }

    public void   sendRegisterForeignDevice(InetSocketAddress sockAddr, int duration) {
        if (state != State.STARTED) {
            log.error(device,name,getMacAsString() + " NOT STARTED  sendRegisterForeignDevice()<-- dadr=" + sockAddr);
            return;
        }
        log.info(device,name,getMacAsString()+" sendRegisterForeignDevice()<-- dadr="+ sockAddr);
        byte[] bvlc = new byte[6]; // fixed size
        ByteBuffer buf = ByteBuffer.wrap(bvlc);
        buf.put(BVLC_TYPE);
        buf.put(BVLC_FUNC_REGISTER_FOREIGN_DEVICE);
        buf.putShort((short)bvlc.length);
        buf.putShort((short)duration);
        DatagramPacket packet = new DatagramPacket(bvlc, bvlc.length, sockAddr);
        try { bindSocket.send(packet); }
        catch (IOException ignored) {  } // $$$ do anything? nothing for now
    }

    //////////////////// HELPER THREAD ////////////////////////

    /**
     * This thread blocks on receiving datagrams and then emits them as Events so the main thread can receive them.
     */
    private class ReceiveThread extends Thread
    {
        boolean stop;
        DatagramSocket socket;

        ReceiveThread(String name, DatagramSocket socket) { super(name); this.socket = socket;}

        public void run() {
            try { int size = socket.getReceiveBufferSize(); log.info(device,name,"IP Receive Buffer Size: "+size); } catch (Exception ignore){} // $$$ remove
            while(!socket.isClosed() && !stop) {
                try {
                    byte[] buffer = new byte[MAX_BVLC_LENGTH];
                    DatagramPacket datagram = new DatagramPacket(buffer,buffer.length);
                    socket.receive(datagram); // blocking call
                    EventLoop.emit(this,IPDatalink.this,EVENT_DATAGRAM_MESSAGE,datagram);
                }
                catch(InterruptedIOException ignored) { } // InterruptedIOException includes SocketTimeoutException.  expected, don't complain
                catch(Exception e) { log.error(device,name,"IPDatalink whinged about something: "+e+" "+e.getLocalizedMessage()); } // nothing else to really do, keep looping
            }
            log.info(device,name,"exiting thread with isClosed()="+socket.isClosed()+" and stop="+stop );
        }
    }

    ///////// private methods /////////

    private static final byte BVLC_TYPE                                       = (byte) 0x81;

    private static final byte BVLC_FUNC_RESULT                                = (byte) 0X00;
    private static final byte BVLC_FUNC_WRITE_BROADCAST_DISTRIBUTION_TABLE    = (byte) 0X01;
    private static final byte BVLC_FUNC_READ_BROADCAST_DISTRIBUTION_TABLE     = (byte) 0X02;
    private static final byte BVLC_FUNC_READ_BROADCAST_DISTRIBUTION_TABLE_ACK = (byte) 0X03;
    private static final byte BVLC_FUNC_FORWARDED_BROADCAST                   = (byte) 0X04;
    private static final byte BVLC_FUNC_REGISTER_FOREIGN_DEVICE               = (byte) 0X05;
    private static final byte BVLC_FUNC_READ_FOREIGN_DEVICE_TABLE             = (byte) 0X06;
    private static final byte BVLC_FUNC_READ_FOREIGN_DEVICE_TABLE_ACK         = (byte) 0X07;
    private static final byte BVLC_FUNC_DELETE_FOREIGN_DEVICE_TABLE_ENTRY     = (byte) 0X08;
    private static final byte BVLC_FUNC_DISTRIBUTE_BROADCAST_TO_NETWORK       = (byte) 0X09;
    private static final byte BVLC_FUNC_ORIGINAL_UNICAST                      = (byte) 0x0A;
    private static final byte BVLC_FUNC_ORIGINAL_BROADCAST                    = (byte) 0x0B;

    private static final int  BVLC_RESULT_OK                                     = 0x0000;
    private static final int  BVLC_RESULT_WRITE_BROADCAST_DISTRIBUTION_TABLE_NAK = 0x0010;
    private static final int  BVLC_RESULT_READ_BROADCAST_DISTRIBUTION_TABLE_NAK  = 0x0020;
    private static final int  BVLC_RESULT_REGISTER_FOREIGN_DEVICE_NAK            = 0x0030;
    private static final int  BVLC_RESULT_READ_FOREIGN_DEVICE_TABLE_NAK          = 0x0040;
    private static final int  BVLC_RESULT_DELETE_FOREIGN_DEVICE_TABLE_ENTRY_NAK  = 0x0050;
    private static final int  BVLC_RESULT_DISTRIBUTE_BROADCAST_TO_NETWORK_NAK    = 0x0060;

    private InetAddress getInetAddressOfMac(byte[] given) {
        // this looks weird, but we need to make absolutely sure this doesn't throw an exception by sanitizing the given mac
        byte[] sane = new byte[4]; System.arraycopy(given, 0, sane, 0, Math.min(given.length,4));
        try { return InetAddress.getByAddress(sane); }
        catch (UnknownHostException e) { throw new RuntimeException("java is broken"); } // will not be thrown if you give it 4 or 16 bytes
    }

    private int getPortOfMac(byte[] mac) {
        return (mac[4]&0xFF) << 8 | (mac[5]&0xFF);
    }

    private void receivePacket(DatagramPacket packet)  {
        byte[] data = new byte[packet.getLength()]; // make a new byte[] that is limited to the received length
        System.arraycopy(packet.getData(), 0, data, 0, packet.getLength());
        if (packet.getAddress().equals(bindSocketAddress.getAddress()) && packet.getPort() == bindSocketAddress.getPort()) {
            log.info(device,name,getMacAsString()+" -->receivePacket() IGNORING ECHO sa="+ macToString(toMac(packet.getAddress(),packet.getPort()))+" ["+ Formatting.toHex(data)+"]");
            return;
        }
        byte[] sadr = toMac(packet.getAddress(),packet.getPort());
        log.info(device,name,getMacAsString()+" -->receivePacket() sa="+ macToString(sadr)+" ["+ Formatting.toHex(data)+"]");
        ByteBuffer buf = ByteBuffer.wrap(data);
        if (buf.get() != BVLC_TYPE) {
            log.error(device,name,"BACnetIP Datalink: First octet is not 0x81 from "+ Formatting.toHex(sadr)+" in ["+ Formatting.toHex(data)+"]");
            return;
        }
        int function = buf.get()&0xFF;
        int length   = buf.getShort()&0xFFFF;
        if (length != data.length) {
            log.error(device,name,"BACnetIP Datalink: Bad BVLC length from "+ Formatting.toHex(sadr)+" in ["+ Formatting.toHex(data)+"]");
            return;
        }
        if (function == BVLC_FUNC_FORWARDED_BROADCAST) buf.get(sadr); // Forwarded-NPDU, get the original sender address
        switch (function) {
            case BVLC_FUNC_FORWARDED_BROADCAST:
            case BVLC_FUNC_ORIGINAL_UNICAST:
            case BVLC_FUNC_ORIGINAL_BROADCAST:
                boolean broadcast = (function == BVLC_FUNC_FORWARDED_BROADCAST || function == BVLC_FUNC_ORIGINAL_BROADCAST);
                byte[] npduBytes = new byte[buf.remaining()]; // now get the npdu
                buf.get(npduBytes);
                device.networkLayer.dlUnitdataIndication(this,sadr,broadcast,npduBytes,0,false,null);
                break;
            case BVLC_FUNC_WRITE_BROADCAST_DISTRIBUTION_TABLE:
                sendBVLCResult(sadr,BVLC_RESULT_WRITE_BROADCAST_DISTRIBUTION_TABLE_NAK);
                break;
            case BVLC_FUNC_READ_BROADCAST_DISTRIBUTION_TABLE:
                sendBVLCResult(sadr,BVLC_RESULT_READ_BROADCAST_DISTRIBUTION_TABLE_NAK);
                break;
            case BVLC_FUNC_REGISTER_FOREIGN_DEVICE:
                sendBVLCResult(sadr,BVLC_RESULT_REGISTER_FOREIGN_DEVICE_NAK);
                break;
            case BVLC_FUNC_READ_FOREIGN_DEVICE_TABLE:
                sendBVLCResult(sadr,BVLC_RESULT_READ_FOREIGN_DEVICE_TABLE_NAK);
                break;
            case BVLC_FUNC_DELETE_FOREIGN_DEVICE_TABLE_ENTRY:
                sendBVLCResult(sadr,BVLC_RESULT_DELETE_FOREIGN_DEVICE_TABLE_ENTRY_NAK);
                break;
            case BVLC_FUNC_DISTRIBUTE_BROADCAST_TO_NETWORK:
                sendBVLCResult(sadr,BVLC_RESULT_DISTRIBUTE_BROADCAST_TO_NETWORK_NAK);
                break;
            default:  // includes BVLC_FUNC_READ_BROADCAST_DISTRIBUTION_TABLE_ACK, BVLC_FUNC_READ_FOREIGN_DEVICE_TABLE_ACK, and any other bogus ones
                log.error(device,name,"IP Datalink: Ignoring function code "+function+" from "+ macToString(sadr)+" in ["+ Formatting.toHex(data)+"]");
                break;
        }
    }


    //////// helpers //////////

    public static byte[] toMac(InetAddress inetAddress, int port) {
        return toMac(inetAddress.getAddress(),port);
    }

    public static byte[] toMac(byte[]address, int port) {
        byte[] mac = new byte[6];
        System.arraycopy(address, 0, mac, 0, 4);
        mac[4] = (byte)(port >> 8);
        mac[5] = (byte)port;
        return mac;
    }

    public static InetSocketAddress getSocketAddress(String something, int port) throws Exception {
        return new InetSocketAddress(getInetAddrFor(something),port);
    }

    public static InetAddress getInetAddrFor(String something) throws Exception {
        if (!something.equals("localhost") && !something.contains(".")) {  // no dots, so something like "en0" or "vmnet8"
            InetAddress addr = findInetAddrForInterface(something);  // find the primary IP address for the given adapter name
            if (addr != null) return addr;
        }
        return InetAddress.getByName(something);
    }

    public static InetAddress findInetAddrForInterface(String interfaceName) throws Exception {
        List<InetAddress> addresses = new ArrayList<>(); // list of IPv4 addresses for found adapter (hopefully one-and-only-one)
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (ni.getName().equals(interfaceName)) {
                    for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                        if (addr instanceof Inet4Address && !addr.isLoopbackAddress())
                            addresses.add(addr); // filtered list should end up with size 1
                    }
                    if (addresses.size()==0) throw new Exception("No IPv4 address available in interface "+interfaceName);
                    if (addresses.size() >1)  {
                        String error = "More than one IPv4 address is available in interface "+interfaceName+" can't choose between { ";
                        for (InetAddress addr: addresses) error += addr+" ";
                        error+="}";
                        throw new Exception(error);
                    }
                    return addresses.get(0);
                }
            }
            return null; // interface name not found
        }
        catch (Exception e) { throw new Exception("Error encountered enumerating interfaces "+e+" "+e.getMessage());}
    }

    public void  setState(State state, String reason) {
        setState(state,0,reason);
    }

    public void  setState(State state, int timeout, String reason) {
        log.info(device,name,"changing state to "+state+(timeout!=0?" for "+timeout:"")+" because: "+reason);
        this.state = state;
        if (timeout != 0) timer.start(timeout); else timer.clear();
        EventLoop.emit(this,this, EVENT_DATALINK_STATE_CHANGE);
        if (state == State.IDLE) EventLoop.removeMaintenance(this);
        else                     EventLoop.addMaintenance(this);
    }

    @Override public String toString() {
        return "IPDatalink "+name;
    }

    @Override public void dump(String prefix) {
        Shell.println(prefix+"--- IP Datalink ---");
        Shell.println(prefix+"name=\""+name+"\" network="+ network);
        Shell.println(prefix+"state="+(state==State.IDLE?"IDLE":"STARTED")+
                " bound to "+(bindSocketAddress==null?"none":bindSocketAddress));
    }


}