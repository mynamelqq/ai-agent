package com.ai.aigent.advisor;

import com.ai.aigent.service.ChatSummaryService;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

@Component
public class SummaryMemoryAdvisor implements CallAdvisor, StreamAdvisor {
    @Resource
    private ChatSummaryService chatSummaryService;
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public int getOrder() {
        return 100;
    }
      	@Override
	public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        String id = (String)chatClientRequest.context().get("id");
        String summary = chatSummaryService.getSummary(id);
        if(StringUtils.isNotBlank(summary)&& !summary.isEmpty())
        {
                List<Message> instructions = chatClientRequest.prompt().getInstructions();
                String text = instructions.get(instructions.size()-2).getText();
                Message message=new SystemMessage(text+"\n【长期记忆摘要】\n" + summary + "\n【结束】");
                instructions.set(instructions.size()-2,message);
                chatClientRequest.prompt().mutate().messages(instructions);

        }
		ChatClientResponse chatClientResponse = callAdvisorChain.nextCall(chatClientRequest);

		logResponse(chatClientResponse);

		return chatClientResponse;
	}

	@Override
	public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest,
        StreamAdvisorChain streamAdvisorChain) {
        String id = chatClientRequest.context().get(ChatMemory.CONVERSATION_ID).toString();
        String summary = chatSummaryService.getSummary(id);

        if(StringUtils.isNotBlank(summary)&& !summary.isEmpty())
        {
                List<Message> instructions = chatClientRequest.prompt().getInstructions();
                String text = instructions.get(instructions.size()-2).getText();
                Message message=new SystemMessage(text+"\n【长期记忆摘要】\n" + summary + "\n【结束】");
                instructions.set(instructions.size()-2,message);
                chatClientRequest.prompt().mutate().messages(instructions);
        }
		Flux<ChatClientResponse> chatClientResponses = streamAdvisorChain.nextStream(chatClientRequest);

		return new ChatClientMessageAggregator().aggregateChatClientResponse(chatClientResponses, this::logResponse); //后操作
	}

	private void logResponse(ChatClientResponse chatClientResponse) {
	}
}
