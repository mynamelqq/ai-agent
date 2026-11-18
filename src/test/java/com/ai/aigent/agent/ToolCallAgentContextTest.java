package com.ai.aigent.agent;

import com.ai.aigent.service.AgentMessageService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.*;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ToolCallAgentContextTest {
    public static class ProbeTool {
        int calls;
        String session;
        String user;
        @Tool(description = "Capture context and return a result")
        public String probe(ToolContext context) {
            calls++;
            session = context.getContext().get("chatId").toString();
            user = context.getContext().get("userId").toString();
            return "5050";
        }
    }

    private ToolCallAgent agent(ProbeTool probe, ChatModel model) {
        var callbacks = ToolCallbacks.from(probe);
        var options = OpenAiChatOptions.builder().internalToolExecutionEnabled(false).build();
        when(model.getDefaultOptions()).thenReturn(options);
        ToolCallAgent agent = new ToolCallAgent(List.of(callbacks), () -> callbacks,
                ToolCallingManager.builder().build(), options);
        agent.setChatClient(ChatClient.builder(model).build());
        agent.setSessionId("session-1");
        agent.setUserId("user-1");
        agent.setRunId(1L);
        agent.setEmitter(mock(SseEmitter.class));
        agent.setAgentMessageService(mock(AgentMessageService.class));
        agent.setMessageList(new ArrayList<>(List.of(new SystemMessage("rules"), new UserMessage("calculate"))));
        return agent;
    }

    private ChatResponse response(String text, AssistantMessage.ToolCall... calls) {
        return new ChatResponse(List.of(new Generation(AssistantMessage.builder()
                .content(text).toolCalls(List.of(calls)).build())));
    }

    @Test
    void realToolResultRetainsRolesIdsAndContextOnNextModelRequest() {
        ChatModel model = mock(ChatModel.class);
        ProbeTool probe = new ProbeTool();
        ToolCallAgent agent = agent(probe, model);
        AtomicReference<Prompt> secondRequest = new AtomicReference<>();
        when(model.stream(any(Prompt.class))).thenReturn(
                Flux.just(response("Executing", new AssistantMessage.ToolCall("call-1", "function", "probe", "{}"))))
                .thenAnswer(invocation -> {
                    secondRequest.set(invocation.getArgument(0));
                    return Flux.just(response("Result is 5050"));
                });

        assertTrue(agent.think());
        agent.act();
        assertEquals(1, probe.calls);
        assertEquals("session-1", probe.session);
        assertEquals("user-1", probe.user);
        assertFalse(agent.think());
        List<Message> messages = secondRequest.get().getInstructions();
        assertEquals(4, messages.size());
        assertInstanceOf(SystemMessage.class, messages.get(0));
        assertInstanceOf(UserMessage.class, messages.get(1));
        AssistantMessage assistant = assertInstanceOf(AssistantMessage.class, messages.get(2));
        assertEquals("call-1", assistant.getToolCalls().getFirst().id());
        ToolResponseMessage result = assertInstanceOf(ToolResponseMessage.class, messages.get(3));
        assertEquals("call-1", result.getResponses().getFirst().id());
        assertEquals("5050", result.getResponses().getFirst().responseData());
        assertEquals(5, agent.getMessageList().size()); // final assistant, no duplicated tool-turn text
    }

    @Test
    void callsFromSeparateStreamChunksAreAllExecutedOnce() {
        ChatModel model = mock(ChatModel.class);
        ProbeTool probe = new ProbeTool();
        ToolCallAgent agent = agent(probe, model);
        when(model.stream(any(Prompt.class))).thenReturn(Flux.just(
                response("", new AssistantMessage.ToolCall("call-1", "function", "probe", "{}")),
                response("", new AssistantMessage.ToolCall("call-2", "function", "probe", "{}"))));
        assertTrue(agent.think());
        assertEquals(2, agent.getToolCalls().size());
        agent.act();
        assertEquals(2, probe.calls);
        ToolResponseMessage results = (ToolResponseMessage) agent.getMessageList().getLast();
        assertEquals(List.of("call-1", "call-2"), results.getResponses().stream().map(ToolResponseMessage.ToolResponse::id).toList());
    }
}
