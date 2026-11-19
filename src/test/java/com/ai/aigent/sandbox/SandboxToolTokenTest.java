package com.ai.aigent.sandbox;

import com.ai.aigent.tools.sandbox.PythonExecutorTool;
import com.ai.aigent.tools.sandbox.TerminalOpTool;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SandboxToolTokenTest {
    @Test void bothToolsForwardServerRunToken() throws Exception {
        SandboxManager manager = mock(SandboxManager.class);
        var context = new ToolContext(Map.of("chatId", "session", "userId", "user", "sandboxRunToken", "token"));
        new PythonExecutorTool(manager).runPythonInDocker("print(1)", context);
        new TerminalOpTool(manager).execTerminal("echo hello", context);
        verify(manager).execute(eq("session"), eq("user"), eq("token"), aryEq(new String[]{"python", "-I", "-B", "-c", "print(1)"}));
        verify(manager).execute(eq("session"), eq("user"), eq("token"), aryEq(new String[]{"sh", "-lc", "echo hello"}));
    }
    @Test void toolsRejectContextWithoutRunToken() {
        SandboxManager manager = mock(SandboxManager.class);
        var context = new ToolContext(Map.of("chatId", "session", "userId", "user"));
        assertThrows(NullPointerException.class, () -> new PythonExecutorTool(manager).runPythonInDocker("print(1)", context));
        assertThrows(NullPointerException.class, () -> new TerminalOpTool(manager).execTerminal("echo hello", context));
        verifyNoInteractions(manager);
    }
    private static String[] aryEq(String[] value) { return org.mockito.AdditionalMatchers.aryEq(value); }
}
