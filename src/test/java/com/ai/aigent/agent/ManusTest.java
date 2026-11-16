package com.ai.aigent.agent;

import com.ai.aigent.agent.model.ToolResultMessage;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManusTest {

    @Test
    void buildPromptShouldKeepToolOutputBounded() {
        ToolResultMessage toolResultMessage = new ToolResultMessage("result", Map.of(), "web_search");
        String prompt = ToolCallAgent.buildPrompt(List.of(
                new SystemMessage("system"),
                new UserMessage("user"),
                toolResultMessage
        ));

        assertTrue(prompt.contains("SYSTEM: system"));
        assertTrue(prompt.contains("USER: user"));
        assertTrue(prompt.contains("Tool: web_search"));
        assertTrue(prompt.contains("Output:\nresult"));
    }

    @Test
    void assistantMemoryShouldPreferVisibleText() {
        assertEquals("final answer", ToolCallAgent.buildAssistantMemory("final answer", "reasoning"));
    }

    @Test
    void assistantMemoryShouldIgnoreReasoningOnlyTurns() {
        assertEquals("", ToolCallAgent.buildAssistantMemory("", "reasoning only"));
    }

    @Test
    void safeDurationShouldFallbackWhenCancelLeavesEndBeforeStart() {
        assertEquals(500L, ToolCallAgent.safeDurationMillis(1_000L, 800L, 1_500L));
        assertEquals(0L, ToolCallAgent.safeDurationMillis(1_000L, 800L, 900L));
    }
}
