package com.ai.aigent.agent;

import cn.hutool.core.collection.CollectionUtil;
import com.ai.aigent.agent.model.AgentEvent;
import com.ai.aigent.agent.model.AgentState;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Data
@Slf4j
@EqualsAndHashCode(callSuper = true)
public  class ToolCallAgent extends ReActAgent{
    private final ToolCallback[] availableTools;
    private ChatResponse toolCallChatResponse;
    private final ToolCallingManager toolCallingManager;
    private final ChatOptions chatOptions;
    public ToolCallAgent(ToolCallback[] availableTools)
    {
        super();
        this.availableTools=availableTools;
        this.toolCallingManager=ToolCallingManager.builder().build();
        this.chatOptions= ToolCallingChatOptions.builder().internalToolExecutionEnabled(false).build();
    }

    @Override
    public boolean think() {
        if (StringUtils.isNotBlank(this.getNextStepPrompt())) {
            getMessageList().add(new UserMessage(getNextStepPrompt()));
        }
        StringBuilder reasoningBuf = new StringBuilder();
        StringBuilder textBuf = new StringBuilder();
        try {
            Flux<ChatResponse> responseFlux = getChatClient()
                    .prompt(new Prompt(getMessageList(), chatOptions))
                    .system(getSystemPrompt())
                    .tools(availableTools)
                    .stream()
                    .chatResponse();
            // 同步等待流结束，同时实时 emit 给前端
            this.toolCallChatResponse = responseFlux
                    .doOnNext(chatResponse -> {
                        var output = chatResponse.getResult().getOutput();
                        // 推理内容（DeepSeek/Claude extended thinking 等）
                        String reasoning = Optional.ofNullable(output.getMetadata())
                                .map(m -> m.get("reasoningContent"))
                                .map(Object::toString)
                                .orElse("");
                        if (!reasoning.isEmpty()) {
                            reasoningBuf.append(reasoning);
                            emit(AgentEvent.reasoning(reasoning));
                        }
                        // 正文
                        String text = output.getText();
                        if (StringUtils.isNotBlank(text)) {
                            textBuf.append(text);
                            emit(AgentEvent.text(text));
                        }
                    })
                    // 聚合最后一个完整 ChatResponse（含 toolCalls 信息）
                    .last()
                    .block();
            if (toolCallChatResponse == null) return false;
            AssistantMessage output = toolCallChatResponse.getResult().getOutput();
            List<AssistantMessage.ToolCall> toolCalls = output.getToolCalls();
            // 把助手消息加入历史
            getMessageList().add(output);
            if (toolCalls.isEmpty()) return false;

            // 推送工具调用信息给前端
            toolCalls.forEach(tc ->
                emit(AgentEvent.toolCall(tc.name(), tc.arguments()))
            );
            log.info("{} 选择了 {} 个工具", getName(), toolCalls.size());
            return true;

        } catch (Exception e) {
            log.error("{} think 异常：{}", getName(), e.getMessage());
            getMessageList().add(new AssistantMessage("处理时遇到问题：" + e.getMessage()));
            return false;
        }
    }
    @Override
    public String act() {//工具调用没有ai
        if (!toolCallChatResponse.hasToolCalls()) return "不需要调用工具";

        Prompt prompt = new Prompt(getMessageList(), chatOptions);
        ToolExecutionResult result = toolCallingManager.executeToolCalls(prompt, toolCallChatResponse);
        setMessageList(result.conversationHistory());

        ToolResponseMessage toolResponseMessage = (ToolResponseMessage)
                CollectionUtil.getLast(result.conversationHistory());

        // 推送工具结果给前端
        toolResponseMessage.getResponses().forEach(r -> {
            emit(AgentEvent.toolResult(r.name(), r.responseData()));
        });
        // 检查终止工具
        boolean isTerminated = toolResponseMessage.getResponses().stream()
                .anyMatch(t -> t.name().equals("doTerminate"));
        if (isTerminated) setAgentState(AgentState.FINISHED);

        String collect = toolResponseMessage.getResponses().stream()
                .map(r -> "工具 " + r.name() + " 返回：" + r.responseData())
                .collect(Collectors.joining("\n"));
        log.info(collect);
        return collect;
    }
}
