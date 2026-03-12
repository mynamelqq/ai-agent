package com.ai.aigent.agent;

import com.ai.aigent.agent.model.AgentSessionRegistry;
import com.ai.aigent.service.AgentMessageService;
import com.ai.aigent.service.AgentSessionService;
import com.ai.aigent.service.AiAttachmentService;
import com.ai.aigent.utils.AttachmentReaderUtil;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.Executor;

@Component
@Scope("prototype")
public class Manus extends ToolCallAgent {

    public Manus(List<ToolCallback> availableTools,
                 @Qualifier("manusChatClient") ChatClient manusChatClient,
                 AgentSessionService agentSessionService,
                 AgentMessageService agentMessageService,
                 AgentSessionRegistry agentSessionRegistry,
                 ToolCallbackProvider toolCallbackProvider,
                 AiAttachmentService attachmentService,
                 AttachmentReaderUtil attachmentReaderUtil,
                 @Qualifier("agentToolCallingManager") ToolCallingManager agentToolCallingManager,
                 @Qualifier("manusChatOptions") ChatOptions manusChatOptions,
                 @Qualifier("agentTaskExecutor") Executor agentTaskExecutor) {
        super(availableTools, toolCallbackProvider, agentToolCallingManager, manusChatOptions);

        this.setAttachmentReaderUtil(attachmentReaderUtil);
        this.setAttachmentService(attachmentService);
        this.setAgentExecutor(agentTaskExecutor);
        this.setName("Manus");
        this.setSystemPrompt("""
        You are Manus, an all-capable AI assistant, aimed at solving any task presented by the user.
        You have various tools at your disposal that you can call upon to efficiently complete complex requests.
        During execution, you may see blocks of data starting with # # # SYSTEM. These are your senses, not your language style. Don't try to imitate this piecemeal format in your output.
        After completing the task, processing the information, or reaching a final conclusion, you must provide the user with a clear, complete,
        and easy-to-understand final response.
        """);
        this.setNextStepPrompt("""
        Based on user needs, proactively select the most appropriate tool or combination of tools.
        For complex tasks, you can break down the problem and use different tools step by step to solve it.
        After using each tool, clearly explain the execution results and suggest the next steps.
        If you want to stop the interaction at any point, use the `terminate` tool/function call.
        """);
        this.setMaxStep(20);
        this.setCancelled(false);
        this.setAgentSessionRegistry(agentSessionRegistry);
        this.setAgentMessageService(agentMessageService);
        this.setAgentSessionService(agentSessionService);
        this.setChatClient(manusChatClient);
    }
}
