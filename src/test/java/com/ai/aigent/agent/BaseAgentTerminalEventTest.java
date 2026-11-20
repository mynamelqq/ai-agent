package com.ai.aigent.agent;

import com.ai.aigent.agent.model.AgentEvent;
import com.ai.aigent.agent.model.AgentSessionRegistry;
import com.ai.aigent.service.AgentContextService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class BaseAgentTerminalEventTest {
    private static class RecordingAgent extends BaseAgent {
        private final List<String> eventTypes = new ArrayList<>();
        private final List<Long> observedRunIds = new ArrayList<>();
        private List<Message> observedMessages;

        @Override
        protected void emit(AgentEvent event) {
            eventTypes.add(event.getType());
        }

        @Override
        public String step() {
            observedRunIds.add(getRunId());
            observedMessages = new ArrayList<>(getMessageList());
            return "继续执行";
        }
    }

    @Test
    void maxStepsStillEmitsWarningAndDone() {
        AgentSessionRegistry registry = new AgentSessionRegistry();
        RecordingAgent agent = new RecordingAgent();
        agent.setAgentSessionRegistry(registry);
        agent.setAgentExecutor(Runnable::run);
        agent.setMaxStep(1);
        assertTrue(registry.register("session", agent));

        agent.agentRun("question", "session", 101L, List.of(), "user");

        assertEquals(List.of("step_start", "warn", "done"), agent.eventTypes);
        assertFalse(registry.isRunning("session"));
    }

    @Test
    void successiveRunsKeepTheirOwnRunIdAndClearItAfterCompletion() {
        RecordingAgent agent = new RecordingAgent();
        AgentSessionRegistry registry = new AgentSessionRegistry();
        agent.setAgentSessionRegistry(registry);
        agent.setAgentExecutor(Runnable::run);
        agent.setMaxStep(1);

        for (long runId : List.of(101L, 102L)) {
            assertTrue(registry.register("session", agent));
            agent.agentRun("question", "session", runId, List.of(), "user");
            org.junit.jupiter.api.Assertions.assertNull(agent.getRunId());
        }

        assertEquals(List.of(101L, 102L), agent.observedRunIds);
    }

    @Test
    void cancellationBeforeWorkerStartsIsPreserved() {
        AgentSessionRegistry registry = new AgentSessionRegistry();
        RecordingAgent agent = new RecordingAgent();
        agent.setAgentSessionRegistry(registry);
        agent.setAgentExecutor(Runnable::run);
        assertTrue(registry.register("session", agent));
        registry.cancel("session");

        agent.agentRun("question", "session", 101L, List.of(), "user");

        assertEquals(List.of("cancelled"), agent.eventTypes);
        assertFalse(registry.isRunning("session"));
    }

    @Test
    void loadsPersistedMessagesBeforeRunningStep() {
        RecordingAgent agent = new RecordingAgent();
        AgentContextService context = mock(AgentContextService.class);
        when(context.loadView("session", "user")).thenReturn(List.of(
                new UserMessage("earlier"), new UserMessage("current")));
        agent.setAgentContextService(context);
        agent.setAgentSessionRegistry(new AgentSessionRegistry());
        agent.setAgentExecutor(Runnable::run);
        agent.setMaxStep(1);

        agent.agentRun("current", "session", 101L, List.of(), "user");

        verify(context).appendUser("session", "user", 101L, "current", List.of());
        assertEquals(3, agent.observedMessages.size());
        assertEquals("earlier", ((UserMessage) agent.observedMessages.get(1)).getText());
        assertEquals("current", ((UserMessage) agent.observedMessages.get(2)).getText());
    }
}
