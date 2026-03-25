package com.ai.aigent.app;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpringAiCodeWrappedTextParserTest {

    @Test
    void shouldParseWrappedReasoningAcrossChunks() {
        SpringAiCode.StreamTextParseState state = new SpringAiCode.StreamTextParseState();

        SpringAiCode.ParsedTextChunk chunk1 = SpringAiCode.parseStreamTextChunk(state, "anal", true);
        SpringAiCode.ParsedTextChunk chunk2 = SpringAiCode.parseStreamTextChunk(state, "ysisstep-1 assista", true);
        SpringAiCode.ParsedTextChunk chunk3 = SpringAiCode.parseStreamTextChunk(state, "ntfinalfinal answer", true);
        SpringAiCode.ParsedTextChunk tail = SpringAiCode.flushStreamTextChunk(state);

        assertEquals("", chunk1.reasoning());
        assertEquals("", chunk1.text());
        assertEquals("step-1 ", chunk2.reasoning());
        assertEquals("", chunk2.text());
        assertEquals("", chunk3.reasoning());
        assertEquals("final answer", chunk3.text());
        assertEquals("", tail.reasoning());
        assertEquals("", tail.text());
    }

    @Test
    void shouldKeepNormalTextUntouched() {
        SpringAiCode.StreamTextParseState state = new SpringAiCode.StreamTextParseState();

        SpringAiCode.ParsedTextChunk chunk = SpringAiCode.parseStreamTextChunk(state, "analysis is just a normal word here", false);

        assertEquals("", chunk.reasoning());
        assertEquals("analysis is just a normal word here", chunk.text());
    }

    @Test
    void shouldParseWrappedReasoningWithLeadingWhitespace() {
        SpringAiCode.StreamTextParseState state = new SpringAiCode.StreamTextParseState();

        SpringAiCode.ParsedTextChunk chunk1 = SpringAiCode.parseStreamTextChunk(state, "\n  anal", true);
        SpringAiCode.ParsedTextChunk chunk2 = SpringAiCode.parseStreamTextChunk(state, "ysisstep-1\nassistantfinal结论", true);
        SpringAiCode.ParsedTextChunk tail = SpringAiCode.flushStreamTextChunk(state);

        assertEquals("", chunk1.reasoning());
        assertEquals("", chunk1.text());
        assertEquals("step-1\n", chunk2.reasoning());
        assertEquals("结论", chunk2.text());
        assertEquals("", tail.reasoning());
        assertEquals("", tail.text());
    }

    @Test
    void shouldParseAssistantWrappedTraceAndIgnoreCommentary() {
        SpringAiCode.StreamTextParseState state = new SpringAiCode.StreamTextParseState();

        SpringAiCode.ParsedTextChunk chunk1 = SpringAiCode.parseStreamTextChunk(state, "assistantanal", true);
        SpringAiCode.ParsedTextChunk chunk2 = SpringAiCode.parseStreamTextChunk(state, "ysisWe got 403.assistantcommentaryto=functions.fetchWebPage", true);
        SpringAiCode.ParsedTextChunk chunk3 = SpringAiCode.parseStreamTextChunk(state, " json{\"url\":\"https://example.com\"}assistantanalysisTry another way.", true);
        SpringAiCode.ParsedTextChunk chunk4 = SpringAiCode.parseStreamTextChunk(state, "assistantfinal这样就失效了", true);
        SpringAiCode.ParsedTextChunk tail = SpringAiCode.flushStreamTextChunk(state);

        assertEquals("", chunk1.reasoning());
        assertEquals("", chunk1.text());
        assertEquals("We got 403.", chunk2.reasoning());
        assertEquals("", chunk2.text());
        assertEquals("Try another way.", chunk3.reasoning());
        assertEquals("", chunk3.text());
        assertEquals("", chunk4.reasoning());
        assertEquals("这样就失效了", chunk4.text());
        assertEquals("", tail.reasoning());
        assertEquals("", tail.text());
    }
}
