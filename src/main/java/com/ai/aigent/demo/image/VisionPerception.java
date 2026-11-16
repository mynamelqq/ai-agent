package com.ai.aigent.demo.image;

//import org.checkerframework.checker.units.qual.C;

import ai.z.openapi.ZhipuAiClient;
import ai.z.openapi.service.model.*;
import org.springframework.stereotype.Component;

import java.util.Arrays;
@Component
public class VisionPerception  {
    String apiKey = "da08e9aee27041bc8c9ba26b6b9acbd1.bf2hfTor3TVneYtk";
    ZhipuAiClient client;
    VisionPerception()
    {
        client = ZhipuAiClient.builder().ofZHIPU()
            .apiKey(apiKey)
            .build();
    }
    public String getVisionDescription(String url)
    {
        ChatCompletionCreateParams request = ChatCompletionCreateParams.builder()
        .model("glm-4.6v")
        .messages(Arrays.asList(
                ChatMessage.builder()
                    .role(ChatMessageRole.USER.value())
                    .content(Arrays.asList(
                        MessageContent.builder()
                            .type("text")
                            .text("描述下这张图片")
                            .build(),
                        MessageContent.builder()
                            .type("image_url")
                            .imageUrl(ImageUrl.builder()
                                .url(url)
                                .build())
                            .build()))
                    .build()))
            .build();
        ChatCompletionResponse response = client.chat().createChatCompletion(request);

        if (response.isSuccess()) {
            Object reply = response.getData().getChoices().get(0).getMessage();
            return reply.toString();
        } else {
            return "错误: " + response.getMsg();
        }
    }


}
