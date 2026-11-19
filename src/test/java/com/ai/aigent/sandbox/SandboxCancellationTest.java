package com.ai.aigent.sandbox;

import com.ai.aigent.entity.domain.AgentSession;
import com.ai.aigent.service.AgentSessionService;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.Path;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SandboxCancellationTest {
    @TempDir Path directory;
    private SandboxManager manager(DockerClient docker) {
        AgentSessionService sessions = mock(AgentSessionService.class);
        AgentSession session = new AgentSession(); session.setUserId("user");
        when(sessions.getById("session")).thenReturn(session);
        SandboxManager manager = spy(new SandboxManager(sessions));
        doReturn(directory).when(manager).sessionDirectory(anyString(), eq("session"));
        ReflectionTestUtils.setField(manager, "enabled", true);
        ReflectionTestUtils.setField(manager, "image", "image");
        ReflectionTestUtils.setField(manager, "docker", docker);
        return manager;
    }
    @Test void cancelBeforeFirstExecutionNeverCreatesContainer() {
        DockerClient docker = mock(DockerClient.class);
        SandboxManager manager = manager(docker);
        try {
            String token = manager.beginRun("session", "user", 1L);
            manager.cancel("session", token);
            assertThrows(CancellationException.class,
                () -> manager.execute("session", "user", token, new String[]{"python"}));
            verifyNoInteractions(docker);
        } finally { manager.close(); }
    }
    @Test void cancellationWhileCreateIsInFlightRemovesReturnedContainerWithoutStartingIt() throws Exception {
        DockerClient docker = mock(DockerClient.class);
        SandboxManager manager = manager(docker);
        CreateContainerCmd create = mock(CreateContainerCmd.class, RETURNS_SELF);
        when(docker.createContainerCmd("image")).thenReturn(create);
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        when(create.exec()).thenAnswer(i -> {
            entered.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test timeout");
            CreateContainerResponse response = new CreateContainerResponse(); response.setId("created"); return response;
        });
        RemoveContainerCmd remove = mock(RemoveContainerCmd.class, RETURNS_SELF);
        when(docker.removeContainerCmd("created")).thenReturn(remove);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            String token = manager.beginRun("session", "user", 1L);
            Future<?> execution = executor.submit(() -> {
                assertThrows(CancellationException.class,
                    () -> manager.execute("session", "user", token, new String[]{"python"}));
            });
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            manager.cancel("session", token);
            release.countDown(); execution.get(5, TimeUnit.SECONDS);
            verify(docker, never()).startContainerCmd(anyString());
            verify(remove).withForce(true); verify(remove).exec();
        } finally { release.countDown(); executor.shutdownNow(); manager.close(); }
    }
    @Test void staleTokenCannotCancelOrExecuteInNewRunEvenWhenRunIdIsReused() throws Exception {
        SandboxManager manager = manager(mock(DockerClient.class));
        try {
            String old = manager.beginRun("session", "user", 1L);
            manager.cancel("session", old);
            String current = manager.beginRun("session", "user", 1L);
            assertNotEquals(old, current);
            manager.cancel("session", old);
            manager.finishRun("session", old);
            assertThrows(CancellationException.class,
                () -> manager.execute("session", "user", old, new String[]{"python"}));
            java.nio.file.Files.writeString(directory.resolve("result"), "ok");
            try (var snapshot = manager.snapshot("session", "user", current, "result")) {
                assertEquals("ok", java.nio.file.Files.readString(snapshot.file()));
            }
        } finally { manager.close(); }
    }
    @Test void snapshotFreezesBackgroundWritersAndResumesBeforeUpload() throws Exception {
        DockerClient docker = mock(DockerClient.class);
        SandboxManager manager = manager(docker);
        try {
            String token = manager.beginRun("session", "user", 1L);
            var slots = (java.util.Map<?, ?>) ReflectionTestUtils.getField(manager, "slots");
            ReflectionTestUtils.setField(slots.get("session"), "id", "live");
            var pause = mock(PauseContainerCmd.class);
            var resume = mock(UnpauseContainerCmd.class);
            when(docker.pauseContainerCmd("live")).thenReturn(pause);
            when(docker.unpauseContainerCmd("live")).thenReturn(resume);
            var remove = mock(RemoveContainerCmd.class, RETURNS_SELF);
            when(docker.removeContainerCmd("live")).thenReturn(remove);
            var source = java.nio.file.Files.writeString(directory.resolve("result"), "before freeze");
            doAnswer(i -> { java.nio.file.Files.writeString(source, "frozen bytes"); return null; }).when(pause).exec();
            doAnswer(i -> { java.nio.file.Files.writeString(source, "changed after resume"); return null; }).when(resume).exec();
            try (var snapshot = manager.snapshot("session", "user", token, "result")) {
                assertEquals("frozen bytes", java.nio.file.Files.readString(snapshot.file()));
                assertEquals("changed after resume", java.nio.file.Files.readString(source));
                var order = inOrder(pause, resume);
                order.verify(pause).exec(); order.verify(resume).exec();
            }
        } finally { manager.close(); }
    }

}
