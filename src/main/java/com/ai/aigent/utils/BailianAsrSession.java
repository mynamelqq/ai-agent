package com.ai.aigent.utils;

import com.alibaba.dashscope.audio.asr.recognition.Recognition;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionParam;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionResult;
import com.alibaba.dashscope.common.ResultCallback;

import java.nio.ByteBuffer;

public class BailianAsrSession {

    private Recognition recognizer;
    private volatile boolean started = false;
    public BailianAsrSession(String apiKey,ResultCallback<RecognitionResult> callback) {
        RecognitionParam param = RecognitionParam.builder()
                .model("fun-asr-realtime")
                .apiKey(apiKey)
                .format("pcm")
                .sampleRate(16000)
                .build();

        recognizer = new Recognition();
        recognizer.call(param, callback);
    }

    public void send(byte[] pcm) {
        ByteBuffer buffer = ByteBuffer.wrap(pcm);
        recognizer.sendAudioFrame(buffer);
    }

    public void stop() {
         try {
            recognizer.stop();
            recognizer.getDuplexApi().close(1000, "bye");
        } catch (Exception ignored) {}
    }
}
