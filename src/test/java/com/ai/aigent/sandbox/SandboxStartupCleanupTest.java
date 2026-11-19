package com.ai.aigent.sandbox;

import com.ai.aigent.service.AgentSessionService;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ListContainersCmd;
import com.github.dockerjava.api.command.RemoveContainerCmd;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Container;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class SandboxStartupCleanupTest {
    @Test
    void removesAllLabeledContainersIncludingStoppedOnes() {
        DockerClient docker = mock(DockerClient.class);
        SandboxManager manager = manager(docker, true);
        ListContainersCmd list = listing(docker);
        Container first = mock(Container.class);
        Container second = mock(Container.class);
        when(first.getId()).thenReturn("running");
        when(second.getId()).thenReturn("stopped");
        when(list.exec()).thenReturn(List.of(first, second));
        RemoveContainerCmd removeFirst = removal(docker, "running");
        RemoveContainerCmd removeSecond = removal(docker, "stopped");
        doThrow(new NotFoundException("already removed")).when(removeFirst).exec();

        try {
            manager.initialize();
            verify(removeFirst).exec();
            verify(removeSecond).exec();
        } finally { manager.close(); }
    }

    @Test
    void disabledSandboxDoesNotContactDocker() {
        DockerClient docker = mock(DockerClient.class);
        SandboxManager manager = manager(docker, false);
        try {
            manager.initialize();
            verifyNoInteractions(docker);
        } finally { manager.close(); }
    }

    @Test
    void cleanupFailurePreventsStartup() {
        DockerClient docker = mock(DockerClient.class);
        SandboxManager manager = manager(docker, true);
        ListContainersCmd list = listing(docker);
        Container container = mock(Container.class);
        when(container.getId()).thenReturn("failed");
        when(list.exec()).thenReturn(List.of(container));
        doThrow(new IllegalStateException("daemon unavailable")).when(removal(docker, "failed")).exec();
        try {
            assertThrows(IllegalStateException.class, manager::initialize);
        } finally { manager.close(); }
    }

    private SandboxManager manager(DockerClient docker, boolean enabled) {
        SandboxManager manager = new SandboxManager(mock(AgentSessionService.class));
        ReflectionTestUtils.setField(manager, "enabled", enabled);
        ReflectionTestUtils.setField(manager, "docker", docker);
        return manager;
    }

    private ListContainersCmd listing(DockerClient docker) {
        ListContainersCmd list = mock(ListContainersCmd.class);
        when(docker.listContainersCmd()).thenReturn(list);
        when(list.withShowAll(true)).thenReturn(list);
        when(list.withLabelFilter(Map.of("app", "aigent-sandbox"))).thenReturn(list);
        return list;
    }

    private RemoveContainerCmd removal(DockerClient docker, String id) {
        RemoveContainerCmd remove = mock(RemoveContainerCmd.class);
        when(docker.removeContainerCmd(id)).thenReturn(remove);
        when(remove.withForce(true)).thenReturn(remove);
        return remove;
    }
}
