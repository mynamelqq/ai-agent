package com.ai.aigent.agent;

import com.ai.aigent.agent.model.AgentEvent;
import com.ai.aigent.agent.model.AgentSessionRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseAgentTerminalEventTest {
    private static class RecordingAgent extends BaseAgent {
        private final List<String> eventTypes = new ArrayList<>();

        @Override
        protected void emit(AgentEvent event) {
            eventTypes.add(event.getType());
        }

        @Override
        public String step() {
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

        agent.agentRun("question", "session", List.of(), "user");

        assertEquals(List.of("step_start", "warn", "done"), agent.eventTypes);
        assertFalse(registry.isRunning("session"));
    }

    @Test
    void cancellationBeforeWorkerStartsIsPreserved() {
        AgentSessionRegistry registry = new AgentSessionRegistry();
        RecordingAgent agent = new RecordingAgent();
        agent.setAgentSessionRegistry(registry);
        agent.setAgentExecutor(Runnable::run);
        assertTrue(registry.register("session", agent));
        registry.cancel("session");

        agent.agentRun("question", "session", List.of(), "user");

        assertEquals(List.of("cancelled"), agent.eventTypes);
        assertFalse(registry.isRunning("session"));
    }
}
