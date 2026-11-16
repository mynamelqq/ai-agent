package com.ai.aigent.agent;

import com.ai.aigent.agent.model.AgentSessionRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class AgentSessionRegistryTest {
    @Test
    void olderAgentCannotReplaceOrRemoveActiveAgent() {
        AgentSessionRegistry registry = new AgentSessionRegistry();
        BaseAgent first = mock(BaseAgent.class);
        BaseAgent second = mock(BaseAgent.class);

        assertTrue(registry.register("session", first));
        assertFalse(registry.register("session", second));
        registry.remove("session", second);
        assertTrue(registry.isRunning("session"));
        registry.remove("session", first);
        assertFalse(registry.isRunning("session"));
        assertTrue(registry.register("session", second));
    }
}
