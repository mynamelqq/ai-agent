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

@Configuration
public class AgentRuntimeConfig {

    @Value("${agent.manus.chat.provider:openai}")
    private String manusChatProvider;

    @Value("qwen4-max")
    private String manusChatModel;

    @Bean("agentTaskExecutor")
    public Executor agentTaskExecutor() {
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
