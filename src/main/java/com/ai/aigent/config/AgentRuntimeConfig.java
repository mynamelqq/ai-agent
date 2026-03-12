package com.ai.aigent.config;

import com.ai.aigent.advisor.MySimpleLoggerAdvisor;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AgentRuntimeConfig {

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
        return DashScopeChatOptions.builder()
                .enableThinking(true)
                .temperature(0.6)
                .parallelToolCalls(true)
                .model("qwen3-coder-plus")
                .internalToolExecutionEnabled(false)
                .build();
    }

    @Bean("manusChatClient")
    public ChatClient manusChatClient(@Qualifier("dashScopeChatModel") ChatModel dashscopeChatModel) {
        return ChatClient.builder(dashscopeChatModel)
                .defaultAdvisors(new MySimpleLoggerAdvisor())
                .build();
    }
}
