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
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger log = LoggerFactory.getLogger(SandboxManager.class);
    private static final Map<String, String> SANDBOX_LABEL = Map.of("app", "aigent-sandbox");
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
    private volatile boolean closing;
    private enum State { IDLE, CREATING, READY, RUNNING, CANCELLED, DELETED }
    private static class Run {
        final String token = UUID.randomUUID().toString();
        final Long runId;
        boolean cancelled;
        Run(Long runId) { this.runId = runId; }
    }
    private static class Slot {
        final Object stateLock = new Object();
        Run run;
        State state = State.IDLE;
        volatile String id;
        volatile long last = System.currentTimeMillis();
    }
    public record Execution(Long exitCode, String stdout, String stderr, boolean timedOut) {

    }
    public SandboxManager(AgentSessionService sessions) {
        this.sessions = sessions;
    }
    @PostConstruct
    public void initialize() {
        if (!enabled) return;
        // Run before this bean becomes available; never depend on the lost in-memory Slots.
        DockerClient c = client();
        int removed = 0;
        for (Container container : c.listContainersCmd().withShowAll(true)
                .withLabelFilter(SANDBOX_LABEL).exec()) {
            try {
                c.removeContainerCmd(container.getId()).withForce(true).exec();
                removed++;
            } catch (NotFoundException ignored) {
                // Another cleanup may already have removed it.
            }
        }
        log.info("Sandbox startup cleanup removed {} containers", removed);
        // Other failures propagate and prevent startup with uncleaned containers.
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
    Path sessionDirectory(String base, String session) { return directory(base, session); }
    public void authorize(String session, String user) {
        var value = sessions.getById(session);
        if (user == null || value == null || !Objects.equals(value.getUserId(), user))
            throw new SecurityException("无权访问会话");
    }
    public String beginRun(String session, String user, Long runId) {
        authorize(session, user);
        Objects.requireNonNull(runId, "runId");
        Slot slot = slots.computeIfAbsent(session, k -> new Slot());
        synchronized (slot) {
            synchronized (slot.stateLock) {
                if (closing || slot.state == State.DELETED) throw new CancellationException("沙箱已关闭");
                if (slot.run != null && !slot.run.cancelled) throw new IllegalStateException("会话已有运行任务");
                if (slot.run != null && slot.run.cancelled && slot.id != null) remove(slot);
                slot.run = new Run(runId);
                slot.state = State.IDLE;
                return slot.run.token;
            }
        }
    }
    private void check(Slot slot, String token) {
        if (closing || slot.state == State.DELETED || slot.run == null ||
            !Objects.equals(slot.run.token, token) || slot.run.cancelled)
            throw new CancellationException("任务已取消或运行标识已过期");
    }
    public void finishRun(String session, String token) {
        Slot slot = slots.get(session);
        if (slot == null) return;
        synchronized (slot.stateLock) {
            if (slot.run != null && Objects.equals(slot.run.token, token)) {
                slot.run.cancelled = true;
                if (slot.state != State.DELETED) slot.state = State.IDLE;
            }
        }
    }
    public Execution execute(String session, String user, String token, String[] command) throws Exception {
        authorize(session, user);
        if (!enabled) throw new IllegalStateException("沙箱尚未启用，请配置 sandbox.enabled");
        Slot slot = slots.computeIfAbsent(session, k -> new Slot());//懒启动 只有不存在的时候才启动
        synchronized (slot) {
            synchronized (slot.stateLock) { check(slot, token); }
            slot.last = System.currentTimeMillis();
            DockerClient c = client();
            if (slot.id == null) {
                synchronized (slot.stateLock) { check(slot, token); slot.state = State.CREATING; }
                Path work = sessionDirectory(FileConstant.AGENT_RUN_SANDBOX_TEMP_DIR, session);
                Path attachments = sessionDirectory(FileConstant.ATTACHMENTS_SAVE_DIR, session);
                Files.createDirectories(work);
                Files.createDirectories(attachments);//确保目录
                // Directory ownership must be provisioned for container UID 10001 by the operator.
                HostConfig host = HostConfig.newHostConfig().withNetworkMode("none")
                    .withReadonlyRootfs(true).withCapDrop(Capability.ALL)
                    .withSecurityOpts(List.of("no-new-privileges:true"))
                    .withMemory(128L * 1024 * 1024).withMemorySwap(128L * 1024 * 1024)
                    .withNanoCPUs(1_000_000_000L).withPidsLimit(64L)
                    .withTmpFs(Map.of("/tmp", "rw,noexec,nosuid,size=64m"))
                    .withBinds(new Bind(work.toString(), new Volume("/workspace")),
                        new Bind(attachments.toString(), new Volume("/attachments"), AccessMode.ro));
                String createdId = c.createContainerCmd(image).withUser("10001:10001")
                    .withWorkingDir("/workspace").withHostConfig(host)
                    .withLabels(Map.of("app", "aigent-sandbox", "session", session))
                    .withCmd("sleep", "infinity").exec().getId();
                try {
                    synchronized (slot.stateLock) {
                        slot.id = createdId;
                        check(slot, token);
                        c.startContainerCmd(createdId).exec();
                        slot.state = State.READY;
                    }
                }
                catch (Exception e)
                {
                    remove(slot);
                    throw e;
                }
            }
            String id = slot.id;
            try {
                synchronized (slot.stateLock) { check(slot, token); slot.state = State.RUNNING; }
                var exec = c.execCreateCmd(id).withAttachStdout(true).withAttachStderr(true)
                    .withTty(false).withCmd(command).exec();
                Capture capture = new Capture();
                try (capture) {
                    synchronized (slot.stateLock) {
                        check(slot, token);
                        c.execStartCmd(exec.getId()).exec(capture);
                    }
                    if (!capture.awaitCompletion(100, TimeUnit.SECONDS)) {
                        remove(slot);
                        return new Execution(null, capture.out(), capture.err(), true);
                    }
                    synchronized (slot.stateLock) { check(slot, token); slot.state = State.READY; }
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
    public ArtifactSnapshot snapshot(String session, String user, String token, String relativePath) throws IOException {
        authorize(session, user);
        Slot slot = slots.get(session);
        if (slot == null) throw new CancellationException("任务不存在");
        synchronized (slot) {
            String id;
            synchronized (slot.stateLock) { check(slot, token); id = slot.id; }
            boolean paused = false;
            ArtifactSnapshot captured = null;
            try {
                // Freeze sandbox writers, including background children, during the bounded copy.
                if (id != null) { client().pauseContainerCmd(id).exec(); paused = true; }
                synchronized (slot.stateLock) { check(slot, token); }
                ArtifactSnapshot snapshot = captured = ArtifactSnapshot.capture(
                        sessionDirectory(FileConstant.AGENT_RUN_SANDBOX_TEMP_DIR, session), relativePath);
                try {
                    synchronized (slot.stateLock) { check(slot, token); }
                    return snapshot;
                } catch (RuntimeException e) { snapshot.close(); throw e; }
            } finally {
                if (paused) {
                    try { client().unpauseContainerCmd(id).exec(); }
                    catch (NotFoundException ignored) { /* cancellation removed it */ }
                    catch (RuntimeException e) {
                        // A failed resume must not leave a permanently paused live container.
                        if (captured != null) {
                            try { captured.close(); } catch (IOException cleanup) { e.addSuppressed(cleanup); }
                        }
                        synchronized (slot.stateLock) { remove(slot); }
                        throw e;
                    }
                }
            }
        }
    }
    public void cancel(String session, String token) {
        Slot slot = slots.computeIfAbsent(session, k -> new Slot());
        synchronized (slot.stateLock) {
            if (token != null && (slot.run == null || !Objects.equals(slot.run.token, token))) return;
            if (slot.run != null) slot.run.cancelled = true;
            slot.state = State.CANCELLED;
            // Serialize deletion with start and with publishing a newly created container ID.
            remove(slot);
        }
    }
    public void cancel(String session) { cancel(session, null); }
    public void delete(String session) throws IOException {
        Slot slot = slots.computeIfAbsent(session, k -> new Slot());
        synchronized (slot.stateLock) {
            if (slot.run != null) slot.run.cancelled = true;
            slot.state = State.DELETED;
            remove(slot);
        }
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
        synchronized (slot.stateLock) {
            String id = slot.id;
            if (id != null) {
                try {
                    client().removeContainerCmd(id).withForce(true).exec();
                    slot.id = null;
                } catch (NotFoundException e) { slot.id = null; }
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
        closing = true;
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
