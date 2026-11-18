package com.ai.aigent.tools.sandbox;
import com.ai.aigent.sandbox.SandboxManager;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
@Component
public class PythonExecutorTool {
    private final SandboxManager sandbox;
    public PythonExecutorTool(SandboxManager sandbox) { this.sandbox = sandbox; }
    @Tool(description = "在当前会话容器运行 Python；附件在 /attachments，产物写入 /workspace。每次执行独立进程，最长60秒，禁止联网")
    public String runPythonInDocker(String pythonCode, ToolContext context) throws Exception {
        if (pythonCode == null || pythonCode.length() > 100000) throw new IllegalArgumentException("代码为空或过长");
        return new ObjectMapper()
                .writeValueAsString(sandbox.execute(
            context.getContext().get("chatId").toString(),
                        context.getContext().get("userId").toString(),
            new String[]{"python", "-I", "-B", "-c", pythonCode}));
    }
}
