package com.ai.aigent.config;

import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesizer;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class TtsSessionManager {

    private final ConcurrentHashMap<String, WebSocketSession> wsMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SpeechSynthesizer> ttsMap = new ConcurrentHashMap<>();

    public void addSession(String sessionId, WebSocketSession session) {
        wsMap.put(sessionId, session);
    }

    public WebSocketSession getSession(String sessionId) {
        return wsMap.get(sessionId);
    }

    public void removeSession(String sessionId) {
        wsMap.remove(sessionId);
        stopTts(sessionId);
    }

    public void bindTts(String sessionId, SpeechSynthesizer tts) {
        ttsMap.put(sessionId, tts);
    }

    public void stopTts(String sessionId) {
        SpeechSynthesizer tts = ttsMap.remove(sessionId);
        if (tts != null) {
            try {
                tts.getDuplexApi().close(1000, "stop");
            } catch (Exception ignored) {}
        }
    }
}
