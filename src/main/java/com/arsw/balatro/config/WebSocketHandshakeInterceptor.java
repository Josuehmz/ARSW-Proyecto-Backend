package com.arsw.balatro.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

public class WebSocketHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WebSocketHandshakeInterceptor.class);

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, 
                                  ServerHttpResponse response, 
                                  WebSocketHandler wsHandler, 
                                  Map<String, Object> attributes) throws Exception {
        
        log.info("=== WEBSOCKET HANDSHAKE ===");
        log.info("Request from: {}", request.getRemoteAddress());
        log.info("Request URI: {}", request.getURI());
        
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, 
                              ServerHttpResponse response, 
                              WebSocketHandler wsHandler, 
                              Exception exception) {
        
        if (exception != null) {
            log.error("WebSocket handshake failed: {}", exception.getMessage());
        } else {
            log.info("WebSocket handshake completed successfully");
        }
    }
}
