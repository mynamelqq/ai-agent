package com.ai.aigent.tools.sandbox;
import com.ai.aigent.sandbox.SandboxManager;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
@Component
public class TerminalOpTool {
    private final SandboxManager sandbox;
    public TerminalOpTool(SandboxManager sandbox) { this.sandbox = sandbox; }
    @Tool(description = "在当前会话容器执行 shell 命令，支持管道重定向；目录 /workspace，附件 /attachments，只读附件，最长60秒，禁止联网")
    public String execTerminal(String command, ToolContext context) throws Exception {
        if (command == null || command.isBlank() || command.length() > 100000) throw new IllegalArgumentException("命令为空或过长");
        return new ObjectMapper().
                writeValueAsString(sandbox.execute(
            context.getContext().get("chatId").toString(), context.getContext().get("userId").toString(),
            new String[]{"sh", "-lc", command}));
    }
}
