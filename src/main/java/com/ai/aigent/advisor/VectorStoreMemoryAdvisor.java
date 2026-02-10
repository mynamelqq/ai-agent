package com.ai.aigent.advisor;

import com.ai.aigent.service.ChatMessageService;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class VectorStoreMemoryAdvisor implements CallAdvisor, StreamAdvisor {
    @Resource
    @Qualifier("codeAppVectorStore")
    private VectorStore vectorStore;
    @Resource
    private ChatMessageService chatMessageService;
    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
  List<Document> docs = vectorStore.similaritySearch(
                        SearchRequest.builder()
                                .query(chatClientRequest.prompt().getInstructions().getLast().getText())
                                .similarityThreshold(0.39)
                                .topK(4)
                                .build()
                );
                String memoryContext = docs.stream()
                        .map(Document::getText)
                        .collect(Collectors.joining("\n"));

         List < Message > instructions = chatClientRequest.prompt().getInstructions();
        String id = chatClientRequest.context().get("chat_memory_conversation_id").toString();
        String mid=chatClientRequest.context().get("messageId").toString();
        chatMessageService.saveMessage(id,instructions.getLast(),mid);
        String text = instructions.get(instructions.size()-2).getText();
        Message message=new SystemMessage(text + "以下是与用户相关的历史记忆：\n"+ memoryContext + "\n");
        instructions.set(instructions.size()-2,message);
        chatClientRequest.prompt().mutate().messages(instructions);
    ChatClientResponse chatClientResponse = callAdvisorChain.nextCall(chatClientRequest);

    logResponse(chatClientResponse);

		return chatClientResponse;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain streamAdvisorChain) {
         List < Message > instructions = chatClientRequest.prompt().getInstructions();
        if(chatClientRequest.prompt().getUserMessage().getText().length()>=20)
        {
             List<Document> docs = vectorStore.similaritySearch(
                        SearchRequest.builder()
                                .query(chatClientRequest.prompt().getInstructions().getLast().getText())
                                .similarityThreshold(0.4)
                                .topK(5)
                                .build()
                );
            String memoryContext = docs.stream()
                    .map(Document::getText)
                    .collect(Collectors.joining("\n"));


            String text = instructions.get(instructions.size()-2).getText();
            Message message=new SystemMessage(text + "以下是与用户相关的历史记忆：\n"+ memoryContext + "\n");
            instructions.set(instructions.size()-2,message);
            chatClientRequest.prompt().mutate().messages(instructions);
        }

        Flux<ChatClientResponse> chatClientResponses = streamAdvisorChain.nextStream(chatClientRequest);

		return new ChatClientMessageAggregator().aggregateChatClientResponse(chatClientResponses, this::logResponse); //后操作
    }

    @Override
    public String getName() {
        return "vector";
    }

    @Override
    public int getOrder() {
        return 1000000;
    }
    private void logResponse(ChatClientResponse chatClientResponse) {
        }

}

