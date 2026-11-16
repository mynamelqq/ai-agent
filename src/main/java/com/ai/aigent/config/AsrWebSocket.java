package com.ai.aigent.config;

import com.ai.aigent.utils.BailianAsrSession;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionResult;
import com.alibaba.dashscope.common.ResultCallback;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@ServerEndpoint("/ws/asr")
public class AsrWebSocket {

    private BailianAsrSession session;
    private static String apiKey;
    @Value("${spring.ai.dashscope.api-key}")
    public void setApiKey(String key) {
        apiKey = key;
    }
    @OnOpen
    public void onOpen(Session wsSession) {
        try {
        session = new BailianAsrSession(apiKey,new ResultCallback<>() {
            @Override
            public void onEvent(RecognitionResult result) {
                try {
                    String text = result.getSentence().getText();
                    boolean isFinal = result.isSentenceEnd();

                    wsSession.getBasicRemote().sendText(
                        "{\"text\":\"" + text + "\",\"final\":" + isFinal + "}"
                    );
                } catch (Exception ignored) {}
            }

            @Override public void onComplete() {}
            @Override public void onError(Exception e) {
                e.printStackTrace();
            }
        });

    } catch (Exception e) {
        e.printStackTrace();
        try {
            wsSession.close();
        } catch (Exception ignored) {}
    }
    }
     @OnError
        public void onError(Session session, Throwable error) {

        }

    @OnMessage
    public void onAudio(byte[] audio) {
        session.send(audio);
    }

    @OnClose
    public void onClose() {
        if (session != null) {
            session.stop();
        }
    }
}
