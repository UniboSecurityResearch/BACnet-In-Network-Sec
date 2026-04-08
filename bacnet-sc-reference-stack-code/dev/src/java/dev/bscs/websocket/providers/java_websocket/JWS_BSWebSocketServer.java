// Copyright, distribution, and usage are defined by the top level LICENSE file.
package dev.bscs.websocket.providers.java_websocket;

import dev.bscs.common.Log;
import dev.bscs.events.EventListener;
import dev.bscs.events.EventLoop;
import dev.bscs.websocket.BSWebSocket;
import dev.bscs.websocket.BSWebSocketServer;
import org.java_websocket.WebSocket;
import org.java_websocket.drafts.Draft;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.extensions.IExtension;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.protocols.IProtocol;
import org.java_websocket.protocols.Protocol;
import org.java_websocket.server.CustomSSLWebSocketServerFactory;
import org.java_websocket.server.WebSocketServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;

/**
 * A wrapper for the java_websocket library to provide the BWebSocketServer interface.
 * @author drobin
 */
public class JWS_BSWebSocketServer extends BSWebSocketServer {

    private static Log log = new Log(JWS_BSWebSocketServer.class);

    private InternalServer  internalServer;

    public JWS_BSWebSocketServer(String name, EventListener listener) {
        this.name = name;
        this.listener = listener;
    }

    @Override public void start(InetSocketAddress localAddr, String subprotocol) {
        stop();
        stopped = false; // neither stared nor stopped at this point
        internalServer = new InternalServer(localAddr, subprotocol);
        internalServer.start();
        // 'start' will be set by internal server's onOpen confirmation
    }

    @Override public void stop() {
        if (internalServer != null) try {internalServer.stop(); } catch (InterruptedException|IOException ignore) {}
        started = false;
        stopped = true;
        internalServer = null;
    }
    
    /////////////////////
    // Internal Server //
    /////////////////////

    private class InternalServer extends WebSocketServer { // JWS WebSocketServer

        InternalServer(InetSocketAddress serverSocketAddress, String protocol) {
            // oh, the ugly ugly things you have to do to avoid the dreaded "super() must be first statement in constructor body"!
            super(serverSocketAddress,
                    new ArrayList<Draft>(
                            Collections.singletonList(
                                    new Draft_6455( new ArrayList<IExtension>(), Collections.singletonList((IProtocol)new Protocol(protocol)))
                            )
                    )
            );
            super.setConnectionLostTimeout(0);
            // these come from superclass
            if (sslContext != null) super.setWebSocketFactory(new CustomSSLWebSocketServerFactory(sslContext,enabledProtocols,enabledCipherSuites,clientValidation));
        }

        @Override public void onStart() {  // the JWS server is bound and listening
            started = true;
            EventLoop.emit(this,listener,EVENT_SERVER_STARTED);
        }

        private int conectionNumber = 1; // for name

        @Override public void onOpen(WebSocket jwsSocket, ClientHandshake handshake) {  // received incoming connection
            if (jwsSocket == null) return; // ignore occasional JWS glitch
            BSWebSocket bwsSocket = new JWS_BSWebSocket(name+"_"+conectionNumber++,jwsSocket);
            log.debug("onOpen() with socket "+bwsSocket.hashCode());
            bwsSocket.connected = true;         // new socket starts out connected
            jwsSocket.setAttachment(bwsSocket); // attach the BWebSocket to JWS socket, so it can be found later
            EventLoop.emit(this,listener,EVENT_SERVER_SOCKET_OPEN,bwsSocket); // send newly created socket to this server's listener
        }

        @Override public void onClose(WebSocket jwsSocket, int code, String reason, boolean remote) {
            if (jwsSocket == null) return; // ignore occasional JWS glitch
            BSWebSocket bwsSocket = jwsSocket.getAttachment();
            if (bwsSocket != null) {
                bwsSocket.closeCode   = code;
                bwsSocket.closeReason = reason;
                bwsSocket.closeRemote = remote;
                bwsSocket.connected   = false;
                EventLoop.emit(this,listener,EVENT_SERVER_SOCKET_CLOSE,bwsSocket);
            }
        }

        @Override public void onMessage(WebSocket jwsSocket, String message) {
            if (jwsSocket == null) return; // ignore occasional JWS glitch
            log.error("WebSocket "+this+" received non-binary data - closing");
            jwsSocket.close(BSWebSocket.CLOSE_CODE_DATA_NOT_ACCEPTED, "Can't accept non-binary frames"); // Clause YY.7.5.3
            BSWebSocket bwsSocket = jwsSocket.getAttachment();
            if (bwsSocket != null) {
                bwsSocket.closeCode   = BSWebSocket.CLOSE_CODE_DATA_NOT_ACCEPTED;
                bwsSocket.closeReason = "Non-binary frame received";
                bwsSocket.closeRemote = false;
                bwsSocket.connected   = false;
                EventLoop.emit(this,listener,EVENT_SERVER_SOCKET_CLOSE,bwsSocket);
            }
            jwsSocket.close();
        }

        @Override public void onMessage(WebSocket jwsSocket, ByteBuffer message) {
            if (jwsSocket == null) return; // ignore occasional JWS glitch
            BSWebSocket bwsSocket = jwsSocket.getAttachment();
            if (bwsSocket != null) EventLoop.emit(this,listener,EVENT_SERVER_SOCKET_MESSAGE,bwsSocket,message);
        }

        @Override public void onError(WebSocket jwsSocket, Exception ex) {
            if (jwsSocket == null) {
                EventLoop.emit(this,listener,EVENT_SERVER_STOPPED,ex); // usually because binding failed
                JWS_BSWebSocketServer.this.stop(); // container's stop, not the internal server
            }
            else {
                BSWebSocket bwsSocket = jwsSocket.getAttachment();
                if (bwsSocket != null) EventLoop.emit(this,listener,EVENT_SERVER_SOCKET_ERROR,bwsSocket,ex);
            }
        }
    }

}
