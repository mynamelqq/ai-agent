package com.ai.aigent.agent;


import com.ai.aigent.advisor.MySimpleLoggerAdvisor;
import com.ai.aigent.agent.model.AgentSessionRegistry;
import com.ai.aigent.service.AgentMessageService;
import com.ai.aigent.service.AgentSessionService;
import com.ai.aigent.service.AiAttachmentService;
import com.ai.aigent.service.UserUsageDailyService;
import com.ai.aigent.utils.AttachmentReaderUtil;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Scope("prototype")  // 每次注入/获取都是新实例
public class Manus extends ToolCallAgent{

    public Manus(List<ToolCallback>availableTools, @Qualifier("dashScopeChatModel") ChatModel dashscopeChatModel
            , AgentSessionService agentSessionService, AgentMessageService agentMessageService, AgentSessionRegistry
                 agentSessionRegistry, UserUsageDailyService dailyService, ToolCallbackProvider toolCallbackProvider, AiAttachmentService attachmentService,
                 AttachmentReaderUtil attachmentReaderUtil) {
        super(availableTools,dailyService,toolCallbackProvider);
        this.setAttachmentReaderUtil(attachmentReaderUtil);
        this.setAttachmentService(attachmentService);
        this.setName("Manus");
        String SYSTEM_PROMPT = """
        You are Manus, an all-capable AI assistant, aimed at solving any task presented by the user.
        You have various tools at your disposal that you can call upon to efficiently complete complex requests.
        """;
        this.setSystemPrompt(SYSTEM_PROMPT);
        String NEXT_STEP_PROMPT = """
        Based on user needs, proactively select the most appropriate tool or combination of tools.
        For complex tasks, you can break down the problem and use different tools step by step to solve it.
        After using each tool, clearly explain the execution results and suggest the next steps.
        If you want to stop the interaction at any point, use the `terminate` tool/function call.
        """;
        this.setNextStepPrompt(NEXT_STEP_PROMPT);
        this.setMaxStep(20);this.setCancelled(false);
        this.setAgentSessionRegistry(agentSessionRegistry);
        this.setAgentMessageService(agentMessageService);
        this.setAgentSessionService(agentSessionService);
        ChatClient client=ChatClient.builder(dashscopeChatModel)
                .defaultAdvisors(new MySimpleLoggerAdvisor())
                .build();
        this.setChatClient(client);

    }
}
