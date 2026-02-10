package com.ai.aigent.demo.invoke;
import com.theokanning.openai.completion.chat.ChatCompletionChoice;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.service.OpenAiService;

import java.time.Duration;
import java.util.Arrays;

public class JieKouAI {
     public static void main(String[] args) {
           // 创建 OpenAiService 实例
            OpenAiService service = new OpenAiService(
                "sk_77UhKFgx6f92sRcQ6_DmQGk4aXkikul3i83L6Gpfxw0",
                Duration.ofSeconds(200)  // 超时时间

            );

            // 对于 DeepSeek，如果需要自定义 baseUrl
            // 如果默认的 OpenAI 格式不适用，可以这样设置：
            // service.setBaseUrl("https://api.deepseek.com");

            // 构建请求
         ChatCompletionRequest request = ChatCompletionRequest.builder()
                 .model("deepseek-chat")  // DeepSeek 的模型名称
                 .stream(true)
                 .messages(Arrays.asList(
                         new ChatMessage("user", "Hello, how are you?")
                 ))
                 .maxTokens(1000)

                 .temperature(0.7).build();

         // 发送请求并获取响应
            ChatCompletionChoice choice = service.createChatCompletion(request)
                .getChoices()
                .get(0);

            System.out.println(choice.getMessage().getContent());

            // 关闭服务
            service.shutdownExecutor();
     }

}



