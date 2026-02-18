package com.ai.aigent.utils;

import com.alibaba.dashscope.audio.qwen_asr.*;
import com.alibaba.dashscope.utils.Constants;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.springframework.beans.factory.annotation.Value;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class AudioReader {

    private String apiKey;
    @Value("${spring.ai.dashscope.api-key}")
    void setApiKey(String apiKey)
    {
        this.apiKey = apiKey;
    }
    public String readAudio(String url)
    {
        Constants.baseHttpApiUrl = "https://dashscope.aliyuncs.com/api/v1";
        QwenTranscriptionParam param =
                QwenTranscriptionParam.builder()
                        .apiKey(apiKey)
                        .model("qwen3-asr-flash-filetrans")
                        .fileUrl(url)
                        .parameter("enable_itn", false)
                        .parameter("enable_words", true)
                        .build();
         try {
            QwenTranscription transcription = new QwenTranscription();
            // 提交任务
            QwenTranscriptionResult result = transcription.asyncCall(param);
            // 查询任务状态
            result = transcription.fetch(QwenTranscriptionQueryParam.FromTranscriptionParam(param, result.getTaskId()));
            // 等待任务完成
            result =
                    transcription.wait(
                            QwenTranscriptionQueryParam.FromTranscriptionParam(param, result.getTaskId()));
            // 获取语音识别结果
            QwenTranscriptionTaskResult taskResult = result.getResult();
            if (taskResult != null) {
                // 获取识别结果的url
                String transcriptionUrl = taskResult.getTranscriptionUrl();
                // 获取url内对应的结果
                HttpURLConnection connection =
                        (HttpURLConnection) new URL(transcriptionUrl).openConnection();
                connection.setRequestMethod("GET");
                connection.connect();
                BufferedReader reader =
                        new BufferedReader(new InputStreamReader(connection.getInputStream()));
                // 格式化输出json结果
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                return gson.toJson(gson.fromJson(reader,JsonObject.class));
            }
        } catch (Exception e) {
            return "error: " + "读取音频文件失败";
        }
         return "error: " + "任务执行失败";
    }

}
