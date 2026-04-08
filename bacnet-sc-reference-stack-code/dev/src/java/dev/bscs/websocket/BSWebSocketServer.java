// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.websocket;

import dev.bscs.events.EventListener;
import dev.bscs.events.EventType;
import dev.bscs.websocket.providers.java_websocket.JWS_BSWebSocket;
import dev.bscs.websocket.providers.java_websocket.JWS_BSWebSocketServer;

import javax.net.ssl.SSLContext;
import java.net.InetSocketAddress;

/**
 * This is an abstraction layer that will aid in swapping out the WebSockets library if needed.
 * At the moment, it does not use a runtime injection of a factory or library search trickery for defining which provider
 * to choose, it just creates a new {@link JWS_BSWebSocketServer}. But that DEPENDENCY is restricted to one line of code here
 * that can be changed if needed.
 * @author drobin
 */
public abstract class BSWebSocketServer {

    // these events are fired by servers for received sockets
    public static final EventType EVENT_SERVER_STARTED        = new EventType("server_started");
    public static final EventType EVENT_SERVER_STOPPED        = new EventType("server_stopped");
    public static final EventType EVENT_SERVER_SOCKET_OPEN    = new EventType("server_socket_open");
    public static final EventType EVENT_SERVER_SOCKET_CLOSE   = new EventType("server_socket_close");
    public static final EventType EVENT_SERVER_SOCKET_MESSAGE = new EventType("server_socket_message");
    public static final EventType EVENT_SERVER_SOCKET_ERROR   = new EventType("server_socket_error");

    public boolean      started;
    public boolean      stopped;

    public String        name;
    public EventListener listener;
    public SSLContext    sslContext;        // can remain null it not using TLS
    public boolean       clientValidation    = true;
    public String[]      enabledProtocols    = null; // null = all
    public String[]      enabledCipherSuites = null; // null = all

    // DEPENDENCY: make this an injectable factory?
    public static BSWebSocketServer newInstance(String name, EventListener listener) { return new JWS_BSWebSocketServer(name,listener); }

    // enabledProtocols and enabledCipherSuites can either/both be null
    public void setSSLOptions(SSLContext sslContext, boolean clientValidation, String[] enabledProtocols, String[] enabledCipherSuites) {
        this.sslContext = sslContext;
        this.clientValidation = clientValidation;
        this.enabledProtocols = enabledProtocols;
        this.enabledCipherSuites = enabledCipherSuites;
    }

    public abstract void start(InetSocketAddress localAddr, String subprotocol);
    public abstract void stop();
    public boolean       isStarted()  { return started; }
    public boolean       isStopped()  { return stopped; }

    @Override public String toString() {
        return "WebSocketServer_"+name;
    }
}
