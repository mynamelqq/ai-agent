package com.ai.aigent.advisor;


import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
@Slf4j
@Component
public class MySimpleLoggerAdvisor implements CallAdvisor, StreamAdvisor {


    public String getName() {
        return this.getClass().getSimpleName();
    }

    public int getOrder() {
        return 10000;
    }

    	@Override
	public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
		logRequest(chatClientRequest);
		ChatClientResponse chatClientResponse = callAdvisorChain.nextCall(chatClientRequest);
		logResponse(chatClientResponse);
		return chatClientResponse;
	}
	@Override
	public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest,
			StreamAdvisorChain streamAdvisorChain) {
		logRequest(chatClientRequest);//前操作

		Flux<ChatClientResponse> chatClientResponses = streamAdvisorChain.nextStream(chatClientRequest);

		return new ChatClientMessageAggregator().aggregateChatClientResponse(chatClientResponses, this::logResponse); //后操作
	}
	private void logRequest(ChatClientRequest request) {
        log.info("AI Request :{}",request.prompt().getContents());
	}
	private void logResponse(ChatClientResponse chatClientResponse) {
		 log.info("AI Response :{}",chatClientResponse.chatResponse().getResult().getOutput().getText());
	}
}
