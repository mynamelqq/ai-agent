package com.ai.aigent.agent;

import cn.hutool.core.collection.CollectionUtil;
import com.ai.aigent.agent.model.*;
import com.ai.aigent.service.UserUsageDailyService;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;
import reactor.core.scheduler.Schedulers;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Data
@Slf4j
@EqualsAndHashCode(callSuper = true)
public  class ToolCallAgent extends ReActAgent{
    private final List<ToolCallback> availableTools;
    private List<AssistantMessage.ToolCall>toolCalls=null;
    private ChatResponse toolCallChatResponse;
    private final ToolCallingManager toolCallingManager;
    private final ChatOptions chatOptions;
    private UserUsageDailyService dailyService;
    private List<ToolCallback>toolCallbackList;
    public ToolCallAgent(List<ToolCallback> availableTools, UserUsageDailyService dailyService,
                         ToolCallbackProvider toolCallbackProvider)
    {
        super();
        this.availableTools=availableTools;
//        this.availableTools.addAll(Arrays.stream(toolCallbackProvider.getToolCallbacks()).toList());
        this.dailyService=dailyService;
        this.toolCallingManager=ToolCallingManager.builder().build();
        this.chatOptions= ToolCallingChatOptions.builder().temperature(0.6).model("qwq-plus").internalToolExecutionEnabled(false).build();
    }
     private void saveAndRecord(String userId, String type,
                AtomicReference<Integer> token, AtomicReference<Integer> prompt_tokens,
                AtomicReference<Integer> completion_tokens, AtomicReference<Integer> cached_tokens,String model) {
            dailyService.addTotalUsage(userId, type, token.get());
            dailyService.addUsage(userId, type, token.get(), prompt_tokens.get(),
                                  completion_tokens.get(), cached_tokens.get(),"agent","qwq-plus");//todo model
        }
    @Override
    public boolean think() {
        if (StringUtils.isNotBlank(this.getNextStepPrompt())) {
            getMessageList().add(new UserMessage(getNextStepPrompt()));//挑选工具提示词
        }
        StringBuilder reasoningBuf = new StringBuilder();
        toolCalls=null;
        toolCallChatResponse=null;
        StringBuilder textBuf = new StringBuilder();
        try {
            AtomicReference<ChatResponse> lastResponse = new AtomicReference<>();
            AtomicReference<Usage> finalUsage = new AtomicReference<>();
            AtomicReference<Integer> token = new AtomicReference<>(0);
             Map<String, Object> toolContext = Collections.singletonMap("chatId", this.getSessionId());
             AtomicReference<Boolean>isThinking = new AtomicReference<>(false);
            AtomicReference<Long>thinkingStartTime=new AtomicReference<>(System.currentTimeMillis());
            AtomicReference<Long>thinkingEndTime=new AtomicReference<>(System.currentTimeMillis());
            AtomicReference<Integer> prompt_tokens = new AtomicReference<>(0);
            AtomicReference<Integer> completion_tokens = new AtomicReference<>(0);
            AtomicReference<Integer> cached_tokens = new AtomicReference<>(0);
            AtomicReference<Long> endTime = new AtomicReference<>(System.currentTimeMillis());
            AtomicReference<Long> startTime = new AtomicReference<>(System.currentTimeMillis());
            Flux<ChatResponse> responseFlux = getChatClient()
                .prompt(new Prompt(buildPrompt(getMessageList()), chatOptions))
                .toolCallbacks(availableTools)
                    .toolContext(toolContext)
                .stream()
                .chatResponse();
            CountDownLatch latch = new CountDownLatch(1);
            setCurrentDisposable(responseFlux
                .doOnNext(chatResponse -> {
                    if(chatResponse==null)return;
                    Usage usage = chatResponse.getMetadata().getUsage();
                    if (usage != null && usage.getTotalTokens() != null) {
                        finalUsage.set(usage);
                    }
                    if (getCancelled()) {
                        getCurrentDisposable().dispose();
                        return;
                    }
                    var output = chatResponse.getResult().getOutput();
                    var result = chatResponse.getResult();
                    var msg = result.getOutput();
                    Optional.of(msg.getMetadata())
                            .map(meta -> meta.get("reasoningContent"))
                            .map(Object::toString)
                            .ifPresent((reasoning)->{
                                if(reasoning.isEmpty())return;
                                reasoningBuf.append(reasoning);
                                emit(AgentEvent.reasoning(reasoning));
                                if(!isThinking.get()){
                                    isThinking.set(true);
                                    thinkingStartTime.set(System.currentTimeMillis());
                                }
                            });
                    String text = output.getText();
                    if (text != null && !text.isEmpty()) {
                        if(isThinking.get()){
                            isThinking.set(false);
                            thinkingEndTime.set(System.currentTimeMillis());
                        }
                        textBuf.append(text);
                        emit(AgentEvent.text(text));
                    }
                    if(!output.getToolCalls().isEmpty()){
                        this.toolCalls = output.getToolCalls();
                        toolCallChatResponse=chatResponse;
                    }
                })
                .doOnComplete(() -> {
                    endTime.set(System.currentTimeMillis());
                    if(isThinking.get())thinkingEndTime.set(System.currentTimeMillis());
                    Usage usage = finalUsage.get();
                    if (usage == null) return;
                    if (usage.getPromptTokens() != null) prompt_tokens.set(usage.getPromptTokens());
                    if (usage.getCompletionTokens() != null) completion_tokens.set(usage.getCompletionTokens());
                    if (usage.getTotalTokens() != null) token.set(usage.getTotalTokens());
                    if (usage.getNativeUsage() instanceof Map) {
                        Map<String, Object> map = (Map<String, Object>) usage.getNativeUsage();
                        Object promptTokensDetails = map.get("prompt_tokens_details");
                        if (promptTokensDetails instanceof Map) {
                            Map<String, Object> detailsMap = (Map<String, Object>) promptTokensDetails;
                            cached_tokens.set(Integer.parseInt(detailsMap.get("cached_tokens").toString()));
                        }
                    }
                })
                .doFinally(signal -> {
                    if (signal == SignalType.ON_COMPLETE) {
                        // 正常完成：保存完整内容
                        AgentMessagePayload agentMessagePayload=new AgentMessagePayload();
                        agentMessagePayload.setContent(textBuf.toString());
                        agentMessagePayload.setReasoning(reasoningBuf.toString());
                        getAgentMessageService().saveAgentMessage(getSessionId(), AgentMessageEnum.ASSISTANT.getValue(),
                                agentMessagePayload,token.get(),
                                (thinkingEndTime.get()-thinkingStartTime.get()),(endTime.get()-startTime.get()),prompt_tokens.get(), completion_tokens.get(),cached_tokens.get());
                    } else if (signal == SignalType.CANCEL) {
                        if (getCancelled()) {
                            // 主动取消：保存已有内容
                            if (!textBuf.isEmpty()) {
                                endTime.set(System.currentTimeMillis());
                                Usage usage = finalUsage.get();
                                if (usage == null) return;
                                if (usage.getPromptTokens() != null) prompt_tokens.set(usage.getPromptTokens());
                                if (usage.getCompletionTokens() != null) completion_tokens.set(usage.getCompletionTokens());
                                if (usage.getTotalTokens() != null) token.set(usage.getTotalTokens());
                                if (usage.getNativeUsage() instanceof Map) {
                                    Map<String, Object> map = (Map<String, Object>) usage.getNativeUsage();
                                    Object promptTokensDetails = map.get("prompt_tokens_details");
                                    if (promptTokensDetails instanceof Map) {
                                        Map<String, Object> detailsMap = (Map<String, Object>) promptTokensDetails;
                                        cached_tokens.set(Integer.parseInt(detailsMap.get("cached_tokens").toString()));
                                    }
                                }
                                AgentMessagePayload agentMessagePayload=new AgentMessagePayload();
                                agentMessagePayload.setContent(textBuf.toString());
                                agentMessagePayload.setReasoning(reasoningBuf.toString());
                                getAgentMessageService().saveAgentMessage(getSessionId(), AgentMessageEnum.ASSISTANT.getValue(),
                                agentMessagePayload,token.get(),
                                        (thinkingEndTime.get()-thinkingStartTime.get()),(endTime.get()-startTime.get()),prompt_tokens.get(),completion_tokens.get(),cached_tokens.get());
                            }
                        }
                    }
                    latch.countDown(); // 释放等待
                })
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe());
        // 等待完成（替代 block()），设置超时防止永久阻塞
        try {
            latch.await(5, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Agent 等待被中断");
        }
        if(toolCalls==null||toolCalls.isEmpty()){
            return false;
        }
        toolCalls.forEach(tc ->
                    {
                        AgentMessagePayload callPayload = new AgentMessagePayload();
                        callPayload.setToolCallId(tc.id());       // 唯一ID
                        callPayload.setToolName(tc.name());
                        callPayload.setToolArgs(tc.arguments());  // JSON字符串
                        getAgentMessageService().saveAgentMessage(
                        getSessionId(), AgentMessageEnum.TOOL.getValue(), callPayload, 0,0L,0L,0,0,0);
                        emit(AgentEvent.toolCall(tc.name(), tc.arguments()));
                    });
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
        if (toolCalls.isEmpty()) return "不需要调用工具";
        long startTime=System.currentTimeMillis();
        Prompt prompt = new Prompt(getMessageList(), chatOptions);

        ToolExecutionResult result = toolCallingManager.executeToolCalls(prompt, toolCallChatResponse);
        ToolResponseMessage toolResponseMessage = (ToolResponseMessage)
                CollectionUtil.getLast(result.conversationHistory());
        // 推送工具结果给前端
        toolResponseMessage.getResponses().forEach(r -> {
            getMessageList().add(new ToolResultMessage(r.responseData(),new HashMap<>(), r.name()) );
            AgentMessagePayload resultPayload = new AgentMessagePayload();
            resultPayload.setToolCallId(r.id());      // 对应的调用ID
            resultPayload.setToolName(r.name());
            resultPayload.setToolResult(r.responseData());
            getAgentMessageService().saveAgentMessage(
            getSessionId(), AgentMessageEnum.TOOL_RESULT.getValue(),
                    resultPayload, 0,0L,(System.currentTimeMillis()-startTime),0,0,0);
            Map<String,Object>meta=new HashMap<>();
            meta.put("cost",(System.currentTimeMillis()-startTime));
            emit(AgentEvent.toolResult(r.name(), r.responseData(), meta));
        });
        // 检查终止工具
        boolean isTerminated = toolResponseMessage.getResponses().stream()
                .anyMatch(t -> t.name().equals("doTerminateTool"));
        if (isTerminated) setAgentState(AgentState.FINISHED);
        String collect = toolResponseMessage.getResponses().stream()
                .map(r -> "工具 " + r.name() + " 返回：" + r.responseData())
                .collect(Collectors.joining("\n"));
        log.info(collect);
        return collect;
    }
    public static String buildPrompt(List<Message> messageList) {
        StringBuilder sb = new StringBuilder();

        for (Message m : messageList) {

            // 1 普通消息
            if (!(m instanceof ToolResponseMessage)) {
                sb.append(m.getMessageType())
                  .append(": ")
                  .append(m.getText())
                  .append("\n");
                continue;
            }
            if (m instanceof ToolResultMessage toolMsg) {
                String toolName = toolMsg.getToolName();
                String content = truncate(toolMsg.getToolResult(), 60000);
                sb.append("[TOOL RESULT]")
                  .append("\nTool: ").append(toolName)
                  .append("\nOutput:\n")
                  .append(content)
                  .append("\n")
                  .append("[END TOOL RESULT]")
                  .append("\n");
            }
        }

        return sb.toString();
    }
    private static String truncate(String text, int maxLen) {
        if (text == null) {
            return "";
        }

        if (text.length() <= maxLen) {
            return text;
        }

        return text.substring(0, maxLen)
            + "\n...[TRUNCATED " + (text.length() - maxLen) + " chars]";
    }
}
