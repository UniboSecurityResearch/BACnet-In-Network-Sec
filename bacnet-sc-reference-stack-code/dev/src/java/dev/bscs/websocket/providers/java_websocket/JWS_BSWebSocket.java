// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.websocket.providers.java_websocket;

import dev.bscs.common.Log;
import dev.bscs.events.EventListener;
import dev.bscs.events.EventLoop;
import dev.bscs.websocket.BSWebSocket;
import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;
import org.java_websocket.protocols.Protocol;
import org.java_websocket.server.CustomSSLWebSocketServerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;

/**
 * A wrapper for the java_websocket library to provide the BWebSocket interface.
 * @author drobin
 */
public class JWS_BSWebSocket extends BSWebSocket {

    private static Log log = new Log(JWS_BSWebSocket.class);

    private WebSocket  jwsSocket;    // internal JWS WebSocket for both client and server

    JWS_BSWebSocket(String name, WebSocket  jwsSocket) { // makes a pre-connected server socket - package protected, used by JWS_BWebSocketServer
        this.name = name;
        this.jwsSocket = jwsSocket;
        this.connected = true;   // server sockets come pre-connected
    }

    public JWS_BSWebSocket(String name, EventListener listener) { // makes an unconnected client socket
        this.name = name;
        this.listener = listener;
    }

    @Override public void connect(URI uri, String subprotocol) {
        InternalClient client = new InternalClient(uri, subprotocol);
        client.connect();
        jwsSocket = client; // this is not connected yet. that takes time
    }

    @Override public void close() {
        if (jwsSocket != null && jwsSocket.isOpen()) jwsSocket.close();
    }

    @Override public void close(int code, String reason) {
        if (jwsSocket != null && jwsSocket.isOpen()) jwsSocket.close(code,reason);
    }

    @Override public void    write(ByteBuffer buf) { if (jwsSocket != null && jwsSocket.isOpen()) jwsSocket.send(buf); }
    @Override public void    write(byte[] bytes)   { if (jwsSocket != null && jwsSocket.isOpen()) jwsSocket.send(bytes); }
    @Override public void    write(String s)       { if (jwsSocket != null && jwsSocket.isOpen()) jwsSocket.send(s); }
    @Override public boolean isConnected()         { return connected; }
    @Override public boolean isClosed()            { return closed;  }
    @Override public boolean isClosedRemotely()    { return closeRemote; }
    @Override public int     getCloseCode()        { return closeCode; }
    @Override public String  getCloseReason()      { return closeReason; }

    @Override public SocketAddress getRemoteAddress() { return jwsSocket != null? jwsSocket.getRemoteSocketAddress() : null; }

    /////////////////////
    // Internal Client //
    /////////////////////

    // the default java_websockets library provides a pre-made CustomSSLWebSocketServerFactory that we use in
    // JWS_BSWebSocketServer, but there is no corresponding client side version, so we will make one here
    private class CustomSSLWebSocketClientFactory extends SSLSocketFactory  {
        private SSLParameters defaultSSLParameters;
        public CustomSSLWebSocketClientFactory(SSLContext sslContext, String[] enabledProtocols, String[] enabledCipherSuites, boolean clientValidation) {
            defaultSSLParameters = sslContext.getDefaultSSLParameters();
            if (enabledProtocols != null)    defaultSSLParameters.setProtocols(enabledProtocols);
            if (enabledCipherSuites != null) defaultSSLParameters.setCipherSuites(enabledCipherSuites);
        }
        private Socket tweak(Socket socket)  {
            ((SSLSocket)socket).setSSLParameters(defaultSSLParameters);
            return socket;
        }
        // this is the only one that is used by java_websockets in April 2020
        @Override public Socket createSocket() throws IOException { return tweak(sslContext.getSocketFactory().createSocket()); }
        // but we'll do all the others to just in case that changes in the future
        @Override public Socket createSocket(Socket s, InputStream consumed, boolean autoClose) throws IOException { return tweak(sslContext.getSocketFactory().createSocket(s, consumed, autoClose)); }
        @Override public Socket createSocket(Socket socket, String s, int i, boolean b) throws IOException { return tweak(sslContext.getSocketFactory().createSocket(socket,s,i,b)); }
        @Override public Socket createSocket(String s, int i) throws IOException, UnknownHostException { return tweak(sslContext.getSocketFactory().createSocket(s,i)); }
        @Override public Socket createSocket(String s, int i, InetAddress inetAddress, int i1) throws IOException, UnknownHostException { return tweak(sslContext.getSocketFactory().createSocket(s,i,inetAddress,i1)); }
        @Override public Socket createSocket(InetAddress inetAddress, int i) throws IOException { return tweak(sslContext.getSocketFactory().createSocket(inetAddress,i)); }
        @Override public Socket createSocket(InetAddress inetAddress, int i, InetAddress inetAddress1, int i1) throws IOException { return tweak(sslContext.getSocketFactory().createSocket(inetAddress,i,inetAddress1,i1)); }
        // and these we'll just pass these through too
        @Override public String[] getDefaultCipherSuites() { return sslContext.getSocketFactory().getDefaultCipherSuites(); }
        @Override public String[] getSupportedCipherSuites() { return sslContext.getSocketFactory().getSupportedCipherSuites(); }
    }

    private class InternalClient extends WebSocketClient {

        public InternalClient(URI uri, String subprotocol) {
            super(uri, new Draft_6455(new ArrayList<>(), Collections.singletonList(new Protocol(subprotocol))));
            super.setConnectionLostTimeout(0);
            if (sslContext != null) super.setSocketFactory(new CustomSSLWebSocketClientFactory(sslContext,enabledProtocols,enabledCipherSuites,clientValidation));
        }

        @Override public void onOpen(ServerHandshake handshake) {
            closed = false;
            closeReason = "";
            closeRemote = false;
            closeCode = 0;
            connected = true;
            EventLoop.emit(this,listener,EVENT_SOCKET_OPEN);
        }

        @Override public void onClose(int code, String reason, boolean remote) {
            closeCode = code;
            closeReason = reason;
            closeRemote = remote;
            connected = false;
            EventLoop.emit(this,listener,EVENT_SOCKET_CLOSE);
        }

        @Override public void onMessage(String message) {
            log.error("WebSocket "+this+" received non-binary data - closing");
            close(BSWebSocket.CLOSE_CODE_DATA_NOT_ACCEPTED, "Can't accept non-binary frames"); // Clause YY.7.5.3
            closeCode   = BSWebSocket.CLOSE_CODE_DATA_NOT_ACCEPTED;
            closeReason = "Non-binary frame received";
            closeRemote = false;
            connected   = false;
            close(); // TODO test that this properly calls onClose her
        }

        @Override public void onMessage(ByteBuffer message) {
            EventLoop.emit(this,listener,EVENT_SOCKET_MESSAGE,message);
        }

        @Override public void onError(Exception ex) {
            EventLoop.emit(this,listener,EVENT_SOCKET_ERROR,ex);
        }

    }

}
