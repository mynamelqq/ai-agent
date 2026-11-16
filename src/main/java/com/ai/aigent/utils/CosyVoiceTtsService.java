package com.ai.aigent.utils;

import com.ai.aigent.config.TtsSessionManager;
import com.alibaba.dashscope.audio.tts.SpeechSynthesisResult;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisParam;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesizer;
import com.alibaba.dashscope.common.ResultCallback;
import com.alibaba.dashscope.utils.Constants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.concurrent.CountDownLatch;
@Service
public class CosyVoiceTtsService {
    @Value("${spring.ai.dashscope.api-key}")
    private String apiKey;
    private static String model = "cosyvoice-v3-plus";
    // 音色
    private static String voice = "cosyvoice-v3-plus-efrewew-47ac93a1e5c24b5680b16b3154ff1ef1";
     @Autowired
    private TtsSessionManager sessionManager;

    public void readText(String text, String sessionId) {
        Constants.baseWebsocketApiUrl = "wss://dashscope.aliyuncs.com/api-ws/v1/inference";
        WebSocketSession session = sessionManager.getSession(sessionId);
        if (session == null) return;
        sessionManager.stopTts(sessionId);
        CountDownLatch latch = new CountDownLatch(1);
        ResultCallback<SpeechSynthesisResult> callback =
                new ResultCallback<>() {

                    @Override
                    public void onEvent(SpeechSynthesisResult result) {
                        try {
                            if (result.getAudioFrame() != null) {
                                session.sendMessage(
                                        new BinaryMessage(result.getAudioFrame()));
                            }
                        } catch (Exception ignored) {}
                    }

                    @Override
                    public void onComplete() {
                        try {
                            session.sendMessage(new TextMessage("TTS_END"));
                        } catch (Exception ignored) {}

                        sessionManager.stopTts(sessionId);
                        latch.countDown();
                    }

                    @Override
                    public void onError(Exception e) {
                        sessionManager.stopTts(sessionId);
                        latch.countDown();
                    }
                };

        SpeechSynthesisParam param =
                SpeechSynthesisParam.builder()
                        .apiKey(apiKey)
                        .model(model)
                        .voice(voice)
                        .build();

        SpeechSynthesizer synthesizer = new SpeechSynthesizer(param, callback);

        // ⭐ 绑定到session
        sessionManager.bindTts(sessionId, synthesizer);

        try {
            synthesizer.call(text);
            latch.await();
        } catch (Exception ignored) {}
    }
    // ⭐ 外部停止
    public void stop(String sessionId) {
        sessionManager.stopTts(sessionId);
    }
}
