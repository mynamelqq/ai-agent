package com.ai.aigent.sandbox;

import com.ai.aigent.agent.BaseAgent;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentSandboxRunLifecycleTest {
    private static class Agent extends BaseAgent {
        @Override public String step() { return ""; }
    }
    @Test void cancellationBeforeInitializationPreventsRunRegistration() {
        Agent agent = new Agent();
        SandboxManager manager = mock(SandboxManager.class);
        agent.setSandboxManager(manager);
        agent.cancel();
        assertThrows(CancellationException.class, () -> agent.agentRun("hello", "session", 1L, List.of(), "user"));
        verifyNoInteractions(manager);
    }
    @Test void cancellationUsesSpecificTokenAndDisposesModelEvenIfDockerFails() {
        Agent agent = new Agent();
        SandboxManager manager = mock(SandboxManager.class);
        agent.setSandboxManager(manager); agent.setSessionId("session"); agent.setSandboxRunToken("token");
        Disposable model = mock(Disposable.class); agent.setCurrentDisposable(model);
        doThrow(new IllegalStateException("docker unavailable")).when(manager).cancel("session", "token");
        assertThrows(IllegalStateException.class, agent::cancel);
        assertTrue(agent.getCancelled()); verify(model).dispose();
    }
    @Test void rejectedExecutorDoesNotLeaveActiveRunBehind() {
        Agent agent = new Agent();
        SandboxManager manager = mock(SandboxManager.class);
        agent.setSandboxManager(manager);
        when(manager.beginRun("session", "user", 1L)).thenReturn("token");
        agent.setAgentExecutor(task -> { throw new RejectedExecutionException(); });
        assertThrows(RejectedExecutionException.class, () -> agent.agentRun("hello", "session", 1L, List.of(), "user"));
        verify(manager).finishRun("session", "token");
    }
}
