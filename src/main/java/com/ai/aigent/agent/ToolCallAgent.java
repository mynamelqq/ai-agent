package com.ai.aigent.agent;

import com.ai.aigent.agent.model.*;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Data
@Slf4j
@EqualsAndHashCode(callSuper = true)
public class ToolCallAgent extends ReActAgent {
    private final List<ToolCallback> availableTools;
    private List<AssistantMessage.ToolCall> toolCalls;
    private ChatResponse toolCallChatResponse;
    private final ToolCallingManager toolCallingManager;
    private final ChatOptions chatOptions;
    private final ToolCallbackProvider toolCallbackProvider;
    private Prompt currentToolPrompt;

    public ToolCallAgent(List<ToolCallback> availableTools, ToolCallbackProvider toolCallbackProvider,
                         ToolCallingManager toolCallingManager, ChatOptions chatOptions) {
        super();
        this.availableTools = List.copyOf(availableTools);
        this.toolCallbackProvider = toolCallbackProvider;
        this.toolCallingManager = toolCallingManager;
        this.chatOptions = chatOptions;
    }

    @Override
    public boolean think() {

        StringBuilder reasoningBuf = new StringBuilder();
        StringBuilder textBuf = new StringBuilder();
        toolCalls = null;
        toolCallChatResponse = null;

        try {
            currentToolPrompt = buildToolPrompt();

            AtomicReference<Usage> finalUsage = new AtomicReference<>();
            AtomicReference<Throwable> streamError = new AtomicReference<>();
            AtomicReference<Boolean> isThinking = new AtomicReference<>(false);
            AtomicReference<Long> thinkingStartTime = new AtomicReference<>(System.currentTimeMillis());
            AtomicReference<Long> thinkingEndTime = new AtomicReference<>(System.currentTimeMillis());
            AtomicReference<Long> endTime = new AtomicReference<>(System.currentTimeMillis());
            AtomicReference<Long> startTime = new AtomicReference<>(System.currentTimeMillis());
            Flux<ChatResponse> responseFlux = getChatClient()
                    .prompt(currentToolPrompt)
                    .advisors(new SimpleLoggerAdvisor())
                    .stream()
                    .chatResponse();

            // Aggregate provider-normalized tool calls across the entire stream, including
            // calls emitted in different chunks. Do not execute an intermediate chunk.
            responseFlux = new MessageAggregator().aggregate(responseFlux, response -> {
                toolCallChatResponse = response;
                toolCalls = response.getResult().getOutput().getToolCalls();
            });
            CountDownLatch latch = new CountDownLatch(1);
            setCurrentDisposable(responseFlux
                    .doOnNext(chatResponse -> {
                        if (chatResponse == null) {
                            return;
                        }

                        Usage usage = chatResponse.getMetadata().getUsage();
                        if (usage != null && usage.getTotalTokens() != null) {
                            finalUsage.set(usage);
                        }

                        if (Boolean.TRUE.equals(getCancelled())) {
                            if (getCurrentDisposable() != null) {
                                getCurrentDisposable().dispose();
                            }
                            return;
                        }
                        if(chatResponse.getResult()==null)return;
                        if(chatResponse.getResult().getOutput()==null)return;
                        var output = chatResponse.getResult().getOutput();
                        Optional.ofNullable(output.getMetadata())
                                .map(meta -> meta.get("reasoningContent"))
                                .map(Object::toString)
                                .filter(StringUtils::isNotBlank)
                                .ifPresent(reasoning -> {
                                    reasoningBuf.append(reasoning);
                                    emit(AgentEvent.reasoning(reasoning));
                                    if (!isThinking.get()) {
                                        isThinking.set(true);
                                        thinkingStartTime.set(System.currentTimeMillis());
                                    }
                                });

                        String text = output.getText();
                        if (StringUtils.isNotBlank(text)) {
                            if (isThinking.get()) {
                                isThinking.set(false);
                                thinkingEndTime.set(System.currentTimeMillis());
                            }
                            textBuf.append(text);
                            emit(AgentEvent.text(text));
                        }
                    })
                    .doOnComplete(() -> {
                        endTime.set(System.currentTimeMillis());
                        if (isThinking.get()) {
                            thinkingEndTime.set(System.currentTimeMillis());
                        }
                    })
                    .doOnError(error -> {
                        endTime.set(System.currentTimeMillis());
                        if (isThinking.get()) {
                            thinkingEndTime.set(System.currentTimeMillis());
                        }
                        streamError.set(error);
                    })
                    .doFinally(signal -> {
                        try {
                            persistAssistantTurn(signal, textBuf, reasoningBuf, finalUsage.get(),
                                    thinkingStartTime.get(), thinkingEndTime.get(), startTime.get(), endTime.get());
                        } catch (Exception e) {
                            streamError.compareAndSet(null, e);
                        } finally {
                            latch.countDown();
                        }
                    })
                    .subscribe());

            try {
                if (!latch.await(5, TimeUnit.MINUTES)) {
                    if (getCurrentDisposable() != null) {
                        getCurrentDisposable().dispose();
                    }
                    throw new IllegalStateException("模型响应等待超时");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                if (getCurrentDisposable() != null) {
                    getCurrentDisposable().dispose();
                }
                throw new IllegalStateException("Agent 等待被中断", e);
            }

            if (Boolean.TRUE.equals(getCancelled())) {
                return false;
            }

            Throwable error = streamError.get();
            if (error != null && !Boolean.TRUE.equals(getCancelled())) {
                throw new RuntimeException(error);
            }

            if (toolCalls == null || toolCalls.isEmpty()) {
                if (textBuf.toString().isBlank()) {
                    throw new IllegalStateException("模型返回了空响应，请检查模型配置或稍后重试");
                }
                return false;
            }

            toolCalls.forEach(tc -> {
                AgentMessagePayload callPayload = new AgentMessagePayload();
                if (isMcpTool(tc.name())) {
                    emit(AgentEvent.mcpToolCall(tc.name(), tc.arguments()));
                } else {
                    emit(AgentEvent.toolCall(tc.name(), tc.arguments()));
                }
                callPayload.setToolCallId(tc.id());
                callPayload.setToolName(tc.name());
                callPayload.setToolArgs(tc.arguments());
                getAgentMessageService().saveAgentMessage(
                        getSessionId(), getRunId(), AgentMessageEnum.TOOL.getValue(), callPayload, 0, 0L, 0L, 0, 0, 0);
            });
            log.info("{} 选择了 {} 个工具", getName(), toolCalls.size());
            return true;
        } catch (Exception e) {
            log.error("{} think 异常：{}", getName(), e.getMessage(), e);
            throw new IllegalStateException("模型推理失败：" + e.getMessage(), e);
        }
    }

    @Override
    public String act() {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return "不需要调用工具";
        }

        long startTime = System.currentTimeMillis();
        if (currentToolPrompt == null || toolCallChatResponse == null) {
            throw new IllegalStateException("缺少当前工具请求上下文");
        }
        ToolExecutionResult result = toolCallingManager.executeToolCalls(currentToolPrompt, toolCallChatResponse);
        List<Message> history = result.conversationHistory();
        if (history.isEmpty() || !(history.getLast() instanceof ToolResponseMessage)) {
            throw new IllegalStateException("工具执行未返回标准工具响应");
        }
        ToolResponseMessage toolResponseMessage = (ToolResponseMessage) history.getLast();
        // The manager supplies the assistant tool_calls and matching tool_call_id responses.
        // Replace the history; appending it would duplicate the preceding conversation.
        setMessageList(new ArrayList<>(history));

        toolResponseMessage.getResponses().forEach(r -> {
            String toolArguments = toolCalls.stream()
                    .filter(tc -> Objects.equals(tc.id(), r.id()))
                    .findFirst().map(AssistantMessage.ToolCall::arguments).orElse(null);
            ToolResultPresentation presentation = ToolResultPresentation.from(
                    r.name(), r.responseData(), toolArguments);
            Map<String, Object> resultMeta = new HashMap<>();
            resultMeta.put("displayResult", presentation.displayResult());
            resultMeta.put("toolSuccess", presentation.toolSuccess());
            resultMeta.put("displayFormat", presentation.displayFormat());
            if (presentation.language() != null) {
                resultMeta.put("language", presentation.language());
            }
            if (isMcpTool(r.name())) {
                emit(AgentEvent.mcpToolResult(r.name(), r.responseData(), resultMeta));
            } else {
                emit(AgentEvent.toolResult(r.name(), r.responseData(), resultMeta));
            }

            AgentMessagePayload resultPayload = new AgentMessagePayload();
            resultPayload.setToolCallId(r.id());
            resultPayload.setToolName(r.name());
            resultPayload.setToolResult(r.responseData());
            resultPayload.setDisplayResult(presentation.displayResult());
            resultPayload.setToolSuccess(presentation.toolSuccess());
            resultPayload.setDisplayFormat(presentation.displayFormat());
            resultPayload.setLanguage(presentation.language());
            getAgentMessageService().saveAgentMessage(
                    getSessionId(), getRunId(), AgentMessageEnum.TOOL_RESULT.getValue(),
                    resultPayload, 0, 0L, System.currentTimeMillis() - startTime, 0, 0, 0);
        });

        boolean isTerminated = toolResponseMessage.getResponses().stream()
                .anyMatch(t -> t.name().equals("doTerminateTool"));
        if (isTerminated) {
            setAgentState(AgentState.FINISHED);
        }

        String collect = toolResponseMessage.getResponses().stream()
                .map(r -> "工具 " + r.name() + " 返回：" + r.responseData())
                .collect(Collectors.joining("\n"));
        log.info(collect);
        return collect;
    }

    Prompt buildToolPrompt() {
        if (!(chatOptions.copy() instanceof ToolCallingChatOptions options)) {
            throw new IllegalStateException("Agent 模型配置必须支持工具调用");
        }
        Map<String, ToolCallback> callbacks = new LinkedHashMap<>();
        for (ToolCallback callback : availableTools) callbacks.put(callback.getToolDefinition().name(), callback);
        if (toolCallbackProvider != null) {
            for (ToolCallback callback : toolCallbackProvider.getToolCallbacks()) {
                callbacks.putIfAbsent(callback.getToolDefinition().name(), callback);
            }
        }
        options.setToolCallbacks(new ArrayList<>(callbacks.values()));
        options.setInternalToolExecutionEnabled(false);
        Map<String, Object> context = new HashMap<>();
        context.put("chatId", getSessionId());
        context.put("userId", getUserId());
        options.setToolContext(context);
        return new Prompt(new ArrayList<>(getMessageList()), options);
    }

    static String buildAssistantMemory(String text, String reasoning) {
        if (StringUtils.isNotBlank(text)) {
            return text;
        }
        return "";
    }

    private void persistAssistantTurn(SignalType signal, StringBuilder textBuf, StringBuilder reasoningBuf,
                                      Usage usage, long thinkingStart, long thinkingEnd, long startTime, long endTime) {
        boolean completed = signal == SignalType.ON_COMPLETE;
        boolean cancelledWithOutput = signal == SignalType.CANCEL
                && Boolean.TRUE.equals(getCancelled())
                && (textBuf.length() > 0 || reasoningBuf.length() > 0);
        if (!completed && !cancelledWithOutput) {
            return;
        }

        UsageMetrics usageMetrics = UsageMetrics.from(usage);
        AgentMessagePayload agentMessagePayload = new AgentMessagePayload();
        agentMessagePayload.setContent(textBuf.toString());
        agentMessagePayload.setReasoning(reasoningBuf.toString());

        String memoryText = buildAssistantMemory(textBuf.toString(), reasoningBuf.toString());
        if ((toolCalls == null || toolCalls.isEmpty()) && StringUtils.isNotBlank(memoryText)) {
            getMessageList().add(new AssistantMessage(memoryText));
        }

        getAgentMessageService().saveAgentMessage(
                getSessionId(), getRunId(),
                AgentMessageEnum.ASSISTANT.getValue(),
                agentMessagePayload,
                usageMetrics.totalTokens(),
                safeDurationMillis(thinkingStart, thinkingEnd, endTime),
                safeDurationMillis(startTime, endTime, endTime),
                usageMetrics.promptTokens(),
                usageMetrics.completionTokens(),
                usageMetrics.cachedTokens());
    }

    private static boolean isMcpTool(String toolName) {
        return toolName != null && toolName.startsWith("maps");
    }

    static long safeDurationMillis(long startTime, long endTime, long fallbackEndTime) {
        long normalizedEndTime = endTime >= startTime ? endTime : Math.max(startTime, fallbackEndTime);
        return Math.max(0L, normalizedEndTime - startTime);
    }
    private record UsageMetrics(int totalTokens, int promptTokens, int completionTokens, int cachedTokens) {
        private static UsageMetrics from(Usage usage) {
            if (usage == null) {
                return new UsageMetrics(0, 0, 0, 0);
            }

            int totalTokens = usage.getTotalTokens() == null ? 0 : usage.getTotalTokens();
            int promptTokens = usage.getPromptTokens() == null ? 0 : usage.getPromptTokens();
            int completionTokens = usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens();
            int cachedTokens = 0;

            if (usage.getNativeUsage() instanceof Map<?, ?> map) {
                Object promptTokensDetails = map.get("prompt_tokens_details");
                if (promptTokensDetails instanceof Map<?, ?> detailsMap) {
                    Object cachedTokensValue = detailsMap.get("cached_tokens");
                    if (cachedTokensValue != null) {
                        cachedTokens = Integer.parseInt(cachedTokensValue.toString());
                    }
                }
            }
            return new UsageMetrics(totalTokens, promptTokens, completionTokens, cachedTokens);
        }
    }
}
