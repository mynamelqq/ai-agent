package com.ai.aigent.agent;

import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.assertEquals;

class ManusTest {

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
