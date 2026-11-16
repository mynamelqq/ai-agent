package com.ai.aigent.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
@Component
public class TtsWebSocketHandler implements WebSocketHandler {

    @Autowired
    private TtsSessionManager sessionManager;
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // 从 URL 获取 sessionId，例如 ws://ws/tts?sessionId=chat-123
        String sessionId = getSessionId(session);
        // ⭐ 添加 session 到 manager
        sessionManager.addSession(sessionId, session);
    }
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionId = getSessionId(session);
        // ⭐ 删除 session 并停止 TTS
        sessionManager.removeSession(sessionId);
    }

    private String getSessionId(WebSocketSession session) {
        return session.getUri().getQuery().replace("sessionId=", "");
    }
    @Override public void handleMessage(WebSocketSession s, WebSocketMessage<?> m) {}
    @Override public void handleTransportError(WebSocketSession s, Throwable e) {}
    @Override public boolean supportsPartialMessages() { return false; }
}