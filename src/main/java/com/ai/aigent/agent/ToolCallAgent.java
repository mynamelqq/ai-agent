package com.ai.aigent.agent;

import cn.hutool.core.collection.CollectionUtil;
import com.ai.aigent.agent.model.AgentState;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
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
        if(StringUtils.isNotBlank(this.getNextStepPrompt()))
        {
//            UserMessage userMessage =new UserMessage(getNextStepPrompt());
//            getMessageList().add(userMessage);
        }
        List<Message> messageList=getMessageList();
        Prompt prompt=new Prompt(messageList,chatOptions);
        try {
            ChatResponse chatResponse = getChatClient().prompt(prompt).system(getSystemPrompt()).tools(availableTools)
                    .call().chatResponse();
            this.toolCallChatResponse=chatResponse;
            AssistantMessage output = chatResponse.getResult().getOutput();
            String text = output.getText();
            log.info(getName()+"的思考"+text);

            List<AssistantMessage.ToolCall> toolCalls = output.getToolCalls();
            log.info(getName()+"选择了"+toolCalls.size()+"个工具使用");
            String toolCallInfo = toolCalls.stream().map(toolCall ->
                    String.format("工具名称：%s,工具参数：%s", toolCall.name(), toolCall.arguments())
            ).collect(Collectors.joining("\n"));
            if(toolCalls.isEmpty())
            {
                getMessageList().add(output);
                return false;
            }
        } catch (Exception e) {
            log.info(getName()+"的思考遇到了问题：{}",e.getMessage());
            getMessageList().add(new AssistantMessage("处理时遇到了问题：{}"+e.getMessage()));
            return false;
        }

        return true;
    }

    @Override
    public String act() {
        if(!toolCallChatResponse.hasToolCalls()){
            return "不需要调用工具";
        }
        Prompt prompt=new Prompt(getMessageList(),this.chatOptions);
        ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, toolCallChatResponse);
        setMessageList(toolExecutionResult.conversationHistory());
        ToolResponseMessage toolResponseMessage=(ToolResponseMessage)CollectionUtil.
                getLast(toolExecutionResult.conversationHistory());
        boolean isTerminated=toolResponseMessage.getResponses().stream().anyMatch(t->t.name().equals("doTerminate"));
        if(isTerminated){
            setAgentState(AgentState.FINISHED);
        }
        String collect = toolResponseMessage.getResponses().stream().map(response -> "工具" + response.name() + "被调用返回的结果：" + response.responseData())
                .collect(Collectors.joining("\n"));
       log.info(collect);
        return collect;
    }
}
