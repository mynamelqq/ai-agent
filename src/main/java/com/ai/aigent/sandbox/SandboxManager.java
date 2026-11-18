package com.ai.aigent.sandbox;

import com.ai.aigent.constant.FileConstant;
import com.ai.aigent.service.AgentSessionService;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

@Service
public class SandboxManager {
    private final AgentSessionService sessions;
    private DockerClient docker;
    private final Map<String, Slot> slots = new ConcurrentHashMap<>();
    private final ScheduledExecutorService reaper =
            Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "sandbox-reaper");
        t.setDaemon(true);
        return t;
    });
    @Value("${sandbox.enabled:false}") private boolean enabled;
    @Value("${sandbox.image:aigent-python:1}") private String image;
    @Value("${sandbox.idle-minutes:15}") private long idleMinutes;
    private static class Slot {
        volatile String id;
        volatile long last = System.currentTimeMillis();
    }
    public record Execution(Long exitCode, String stdout, String stderr, boolean timedOut) {

    }
    public SandboxManager(AgentSessionService sessions) {
        this.sessions = sessions;
        reaper.scheduleWithFixedDelay(this::reap, 60, 60, TimeUnit.SECONDS);
    }
    private synchronized DockerClient client() {
        if (docker == null) {
            var config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
            var httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofSeconds(45))
                .build();
            docker = DockerClientImpl.getInstance(config, httpClient);
        }
        return docker;
    }
    public static Path directory(String base, String session) {
        if (session == null || !session.matches("[A-Za-z0-9_-]{1,128}"))
            throw new IllegalArgumentException("非法会话 ID");
        Path path = Path.of(base).toAbsolutePath().normalize().resolve(session);
        if (Files.isSymbolicLink(path)) throw new IllegalArgumentException("会话目录不能是符号链接");
        return path;
    }
    public void authorize(String session, String user) {
        var value = sessions.getById(session);
        if (user == null || value == null || !Objects.equals(value.getUserId(), user))
            throw new SecurityException("无权访问会话");
    }
    public Execution execute(String session, String user, String[] command) throws Exception {
        authorize(session, user);
        if (!enabled) throw new IllegalStateException("沙箱尚未启用，请配置 sandbox.enabled");
        Slot slot = slots.computeIfAbsent(session, k -> new Slot());//懒启动 只有不存在的时候才启动
        synchronized (slot) {
            slot.last = System.currentTimeMillis();
            DockerClient c = client();
            if (slot.id == null) {
                Path work = directory(FileConstant.AGENT_RUN_SANDBOX_TEMP_DIR, session);
                Path attachments = directory(FileConstant.ATTACHMENTS_SAVE_DIR, session);
                Files.createDirectories(work);
                Files.createDirectories(attachments);//确保目录
                // Directory ownership must be provisioned for container UID 10001 by the operator.
                HostConfig host = HostConfig.newHostConfig().withNetworkMode("none")
                    .withReadonlyRootfs(true).withCapDrop(Capability.ALL)
                    .withSecurityOpts(List.of("no-new-privileges:true"))
                    .withMemory(512L * 1024 * 1024).withMemorySwap(512L * 1024 * 1024)
                    .withNanoCPUs(1_000_000_000L).withPidsLimit(64L)
                    .withTmpFs(Map.of("/tmp", "rw,noexec,nosuid,size=64m"))
                    .withBinds(new Bind(work.toString(), new Volume("/workspace")),
                        new Bind(attachments.toString(), new Volume("/attachments"), AccessMode.ro));
                slot.id = c.createContainerCmd(image).withUser("10001:10001")
                    .withWorkingDir("/workspace").withHostConfig(host)
                    .withLabels(Map.of("app", "aigent-sandbox", "session", session))
                    .withCmd("sleep", "infinity").exec().getId();
                try {
                    c.startContainerCmd(slot.id).exec();
                }
                catch (Exception e)
                {
                    remove(slot);
                    throw e;
                }
            }
            String id = slot.id;
            try {
                var exec = c.execCreateCmd(id).withAttachStdout(true).withAttachStderr(true)
                    .withTty(false).withCmd(command).exec();
                Capture capture = new Capture();
                try (capture) {
                    c.execStartCmd(exec.getId()).exec(capture);
                    if (!capture.awaitCompletion(100, TimeUnit.SECONDS)) {
                        remove(slot);
                        return new Execution(null, capture.out(), capture.err(), true);
                    }
                    return new Execution(c.inspectExecCmd(exec.getId()).exec().getExitCodeLong(),
                        capture.out(), capture.err(), false);
                }
            } catch (Exception e) {
                remove(slot);
                throw e;
            }
            finally {
                slot.last = System.currentTimeMillis();
            }
        }
    }
    // Cancellation intentionally bypasses the execution lock to kill a running exec.
    public void cancel(String session) {
        Slot slot = slots.get(session);
        if (slot != null) remove(slot);
    }
    public void delete(String session) throws IOException {
        Slot slot = slots.computeIfAbsent(session, k -> new Slot());
        cancel(session);
        synchronized (slot) {
            remove(slot);
            for (String base : List.of(FileConstant.ATTACHMENTS_SAVE_DIR, FileConstant.AGENT_RUN_SANDBOX_TEMP_DIR)) {
                Path dir = directory(base, session);
                if (Files.exists(dir, LinkOption.NOFOLLOW_LINKS)) {
                    try (var paths = Files.walk(dir)) {
                        for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
                    }
                }
            }
        }
    }
    private void remove(Slot slot) {
        String id = slot.id;
        if (id != null) {
            try {
                client().removeContainerCmd(id).withForce(true).exec(); slot.id = null;
            }
            catch (NotFoundException e) {
                slot.id = null;
            }
        }
    }
    private void reap() {
        for (Slot slot : slots.values()) {
            if (System.currentTimeMillis() - slot.last > TimeUnit.MINUTES.toMillis(idleMinutes)) {
                // Execution refreshes last on exit; only reap after acquiring its lock.
                synchronized (slot) {
                    if (System.currentTimeMillis() - slot.last > TimeUnit.MINUTES.toMillis(idleMinutes))
                        try {
                            remove(slot);

                        } catch (Exception ignored)
                        {
                            /* retry next sweep */
                        }
                }
            }
        }
    }
    @PreDestroy public void close() {
        reaper.shutdownNow();
        slots.values().forEach(s -> { try { remove(s); } catch (Exception ignored) {} });
        if (docker != null) try
        {
            docker.close();
        }
        catch (IOException ignored) {

        }
    }
    private static class Capture extends ResultCallback.Adapter<Frame> {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();
        private final ByteArrayOutputStream err = new ByteArrayOutputStream();
        @Override public synchronized void onNext(Frame frame) {
            var target = frame.getStreamType() == StreamType.STDERR ? err : out;
            byte[] bytes = frame.getPayload();
            target.write(bytes, 0, Math.min(bytes.length, Math.max(0, 65536 - target.size())));
        }
        synchronized String out()
        {
            return out.toString(StandardCharsets.UTF_8);
        }
        synchronized String err() {
            return err.toString(StandardCharsets.UTF_8);
        }
    }
}
