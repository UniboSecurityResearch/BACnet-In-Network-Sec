// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.websocket;

import dev.bscs.events.EventListener;
import dev.bscs.events.EventType;
import dev.bscs.websocket.providers.java_websocket.JWS_BSWebSocket;

import javax.net.ssl.SSLContext;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;

/**
 * This is an abstraction layer that will aid in swapping out the WebSockets library if needed.
 * At the moment, it does not use a runtime injection of a factory or library search trickery for defining which provider
 * to choose, it just creates a new {@link JWS_BSWebSocket}. But that DEPENDENCY is restricted to one line of code here
 * that can be changed if needed.
 * @author drobin
 */
public abstract class BSWebSocket {

    // these events are fired by sockets
    public static final EventType EVENT_SOCKET_OPEN    = new EventType("socket_open");
    public static final EventType EVENT_SOCKET_CLOSE   = new EventType("socket_close");
    public static final EventType EVENT_SOCKET_MESSAGE = new EventType("socket_message");
    public static final EventType EVENT_SOCKET_ERROR   = new EventType("socket_error");

    public    String        name;
    public    EventListener listener;
    public    Object        attachment;
    public    boolean       connected;
    public    boolean       closed;
    public    int           closeCode;
    public    String        closeReason;
    public    boolean       closeRemote;

    // From RFC 6455 Section 7.4.1
    public static final int CLOSE_CODE_CLOSED_BY_PEER      = 1000; // "normal" I'm-done-this-this
    public static final int CLOSE_CODE_ENDPOINT_LEAVES     = 1001; // "going away" "shutting down", etc.
    public static final int CLOSE_CODE_PROTOCOL_ERROR      = 1002;
    public static final int CLOSE_CODE_DATA_NOT_ACCEPTED   = 1003; // e.g., text frame received in a binary only protocol
    public static final int CLOSE_CODE_NONE_RECEIVED       = 1005; // never sent on the wire; indicates no code received
    public static final int CLOSE_CODE_CLOSED_ABNORMALLY   = 1006; // never sent on the wire; indicates abrupt termination
    public static final int CLOSE_CODE_DATA_INCONSISTENT   = 1007; // e.g., non-UTF8 in a text message
    public static final int CLOSE_CODE_DATA_AGAINST_POLICY = 1008;
    public static final int CLOSE_CODE_FRAME_TOO_LONG      = 1009;
    public static final int CLOSE_CODE_EXTENSION_MISSING   = 1010; // for negotiated websocket extensions, not *our* extensions
    public static final int CLOSE_CODE_REQUEST_UNAVAILABLE = 1011; // server unavailable for some higher level reason

    // client configuration
    public SSLContext   sslContext;
    public boolean      clientValidation    = true;
    public String[]     enabledProtocols    = null; // null = all
    public String[]     enabledCipherSuites = null; // null = all

    // DEPENDENCY: make this an injectable factory?
    public static BSWebSocket newInstance(String name, EventListener listener) { return new JWS_BSWebSocket(name,listener); }

    public Object getAttachment()                     { return attachment; }
    public void   setAttachment(Object attachment)    { this.attachment = attachment; }

    public void   setListener(EventListener listener) { this.listener = listener; }

    // enabledProtocols and enabledCipherSuites can either/both be null
    public void setSSLOptions(SSLContext sslContext, boolean clientValidation, String[] enabledProtocols, String[] enabledCipherSuites) {
        this.sslContext          = sslContext;
        this.clientValidation    = clientValidation;
        this.enabledProtocols    = enabledProtocols;
        this.enabledCipherSuites = enabledCipherSuites;
    }

    public abstract void          connect(URI uri, String subprotocol); // non-blocking
    public abstract void          write(ByteBuffer buf);
    public abstract void          write(byte[] bytes);
    public abstract void          write(String s);
    public abstract void          close();
    public abstract void          close(int code, String reason);
    public abstract boolean       isConnected();
    public abstract boolean       isClosed();
    public abstract boolean       isClosedRemotely();
    public abstract int           getCloseCode() ;
    public abstract String        getCloseReason();
    public abstract SocketAddress getRemoteAddress();

    public String toString()  { return "BWebSocket_"+name; }
}
