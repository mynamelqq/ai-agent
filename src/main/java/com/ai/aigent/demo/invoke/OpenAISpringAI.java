package com.ai.aigent.demo.invoke;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import reactor.core.publisher.Flux;

//@Component
public class OpenAISpringAI implements CommandLineRunner {
    @Resource
    @Qualifier("openAiChatModel")
    private OpenAiChatModel openAiChatModel;
//    @Resource
//    private ModelScopeClient modelScopeClient;
//    public String chatWithThinking(String message) {
//        String call = openAiChatModel.call(message);
//        return call;
//    }
    @Override
    public void run(String... args) throws Exception {
        ChatClient chatClient = ChatClient.builder(openAiChatModel)
                .defaultOptions(OpenAiChatOptions.builder().model("qwen3-max").build()).build();
        Flux<String> stream = chatClient.prompt("你是？").stream().content();
        stream.subscribe(System.out::print);
//         System.out.println(openAiChatModel.call("你是?"));
////      System.out.println(modelScopeClient.chat("你是谁？"));
//        Prompt prompt = new Prompt("你是谁？");
////        System.out.println(modelScopeClient.chat("你是谁？"));
//        String text = openAiChatModel.call(prompt).getResult().getOutput().getText();
//        System.out.println(text);
    }
}
