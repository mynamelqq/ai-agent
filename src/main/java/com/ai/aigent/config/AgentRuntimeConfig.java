package com.ai.aigent.config;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Configuration
public class AgentRuntimeConfig {

    @Value("${agent.manus.chat.provider:openai}")
    private String manusChatProvider;

    @Value("qwen4-max")
    private String manusChatModel;

    @Value("${agent.runtime.executor:platform}")
    private String executorMode;

    @Value("${agent.runtime.max-active:200}")
    private int maxActive;

    @Bean("agentTaskExecutor")
    public Executor agentTaskExecutor() {
        if ("virtual".equalsIgnoreCase(executorMode)) {
            if (maxActive < 1) {
                throw new IllegalArgumentException("agent.runtime.max-active must be positive");
            }
            return new BoundedVirtualAgentExecutor(maxActive);
        }
        if (!"platform".equalsIgnoreCase(executorMode)) {
            throw new IllegalArgumentException("Unknown agent.runtime.executor: " + executorMode);
        }
        int processors = Runtime.getRuntime().availableProcessors();
        int corePoolSize = Math.max(4, processors);
        int maxPoolSize = Math.max(corePoolSize, processors * 2);

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("agent-run-");
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(256);
        executor.setAllowCoreThreadTimeOut(true);
        executor.setKeepAliveSeconds(60);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    static final class BoundedVirtualAgentExecutor implements Executor, AutoCloseable {
        private final ExecutorService delegate = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("agent-run-", 0).factory());
        private final Semaphore permits;

        BoundedVirtualAgentExecutor(int maxActive) {
            this.permits = new Semaphore(maxActive);
        }

        @Override
        public void execute(Runnable command) {
            if (!permits.tryAcquire()) {
                throw new RejectedExecutionException("Agent active limit reached");
            }
            try {
                delegate.execute(() -> {
                    try {
                        command.run();
                    } finally {
                        permits.release();
                    }
                });
            } catch (RuntimeException e) {
                permits.release();
                throw e;
            }
        }

        @Override
        public void close() {
            delegate.shutdown();
            try {
                if (!delegate.awaitTermination(30, TimeUnit.SECONDS)) {
                    delegate.shutdownNow();
                }
            } catch (InterruptedException e) {
                delegate.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    @Bean("agentToolCallingManager")
    public ToolCallingManager agentToolCallingManager() {
        return ToolCallingManager.builder().build();
    }

    @Bean("manusChatOptions")
    public ChatOptions manusChatOptions() {
        if (isDashScopeProvider()) {
            return DashScopeChatOptions.builder()
                    .model(manusChatModel)
                    .parallelToolCalls(true)
                    .internalToolExecutionEnabled(false)
                    .build();
        }

        return OpenAiChatOptions.builder()
                .model(manusChatModel)
                .parallelToolCalls(true)
                .internalToolExecutionEnabled(false)
                .build();
    }

    @Bean("manusChatClient")
    public ChatClient manusChatClient(@Qualifier("openAiChatModel") ChatModel openAiChatModel,
                                      @Qualifier("dashScopeChatModel") ChatModel dashScopeChatModel) {
        return ChatClient.builder(isDashScopeProvider() ? dashScopeChatModel : openAiChatModel)
                .build();
    }

    private boolean isDashScopeProvider() {
        return "dashscope".equalsIgnoreCase(manusChatProvider);
    }
}
