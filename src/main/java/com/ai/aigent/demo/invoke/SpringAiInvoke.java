package com.ai.aigent.demo.invoke;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class SpringAiInvoke implements CommandLineRunner {
    @Resource
    private ChatModel ollamaChatModel;
//    @Resource ChatModel dashscopeChatModel;
    @Override
    public void run(String... args) throws Exception {

//        AssistantMessage assistantMessage = ollamaChatModel.call(new Prompt("你好我是李亚奇")).getResult().getOutput();
//        String text = assistantMessage.getText();
//        System.out.println(text);
    }

    public static void main(String[] args) {

    }
}
