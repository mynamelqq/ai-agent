package com.ai.aigent.agent.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolResultPresentationTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void pythonSuccessUsesStdout() {
        ToolResultPresentation result = ToolResultPresentation.from("runPythonInDocker",
                "{\"exitCode\":0,\"stdout\":\"answer\\n\",\"stderr\":\"\",\"timedOut\":false}");

        assertEquals("answer\n", result.displayResult());
        assertTrue(result.toolSuccess());
    }

    @Test
    void pythonFailureShowsStderrAndStatus() {
        ToolResultPresentation result = ToolResultPresentation.from("runPythonInDocker",
                "{\"exitCode\":1,\"stdout\":\"partial\",\"stderr\":\"Traceback\",\"timedOut\":false}");

        assertEquals("退出码 1\nTraceback\npartial", result.displayResult());
        assertFalse(result.toolSuccess());
    }

    @Test
    void pythonCodeReplacesStdoutForDisplay() {
        ToolResultPresentation result = ToolResultPresentation.from("runPythonInDocker",
                "{\"exitCode\":0,\"stdout\":\"generated markdown\",\"stderr\":\"\",\"timedOut\":false}",
                "{\"pythonCode\":\"print('hello')\\n\"}");

        assertEquals("print('hello')\n", result.displayResult());
        assertEquals("code", result.displayFormat());
        assertEquals("python", result.language());
        assertTrue(result.toolSuccess());
    }

    @Test
    void failedPythonExecutionStillDisplaysCodeAndFailureStatus() {
        ToolResultPresentation result = ToolResultPresentation.from("runPythonInDocker",
                "{\"exitCode\":1,\"stdout\":\"\",\"stderr\":\"Traceback\",\"timedOut\":false}",
                "{\"pythonCode\":\"raise ValueError()\"}");

        assertEquals("raise ValueError()", result.displayResult());
        assertFalse(result.toolSuccess());
    }

    @Test
    void terminalCommandReplacesStdoutForDisplay() {
        ToolResultPresentation result = ToolResultPresentation.from("execTerminal",
                "{\"exitCode\":0,\"stdout\":\"file contents\",\"stderr\":\"\",\"timedOut\":false}",
                "{\"command\":\"cat README.md\"}");

        assertEquals("cat README.md", result.displayResult());
        assertEquals("code", result.displayFormat());
        assertEquals("bash", result.language());
        assertTrue(result.toolSuccess());
    }

    @Test
    void timeoutAndGenericToolErrorsAreReadable() {
        ToolResultPresentation timeout = ToolResultPresentation.from("execTerminal",
                "{\"exitCode\":null,\"stdout\":\"\",\"stderr\":\"killed\",\"timedOut\":true}");
        ToolResultPresentation error = ToolResultPresentation.from("web_search",
                "{\"success\":false,\"results\":null,\"error\":\"搜索失败\"}");

        assertEquals("执行超时\nkilled", timeout.displayResult());
        assertFalse(timeout.toolSuccess());
        assertEquals("搜索失败", error.displayResult());
        assertFalse(error.toolSuccess());
    }

    @Test
    void unknownResultPreservesRawContent() {
        ToolResultPresentation result = ToolResultPresentation.from("mcp_unknown", "{\"items\":[1,2]}");
        assertEquals("{\"items\":[1,2]}", result.displayResult());
        assertNull(result.toolSuccess());
    }

    @Test
    void downloadResultUsesGeneratedUrl() {
        ToolResultPresentation result = ToolResultPresentation.from("downloadResource",
                "{\"success\":true,\"url\":\"https://source.example/file\","
                        + "\"cosUrl\":\"https://cdn.example/file\"}");
        assertEquals("https://cdn.example/file", result.displayResult());
        assertTrue(result.toolSuccess());
    }

    @Test
    void oldHistoryGetsDisplayFieldsWithoutLosingRawResult() throws Exception {
        String raw = "{\"exitCode\":0,\"stdout\":\"hello\",\"stderr\":\"\",\"timedOut\":false}";
        String payload = mapper.writeValueAsString(Map.of(
                "toolName", "runPythonInDocker", "toolResult", raw));

        JsonNode enriched = mapper.readTree(ToolResultPresentation.enrichPayload(payload));
        assertEquals(raw, enriched.path("toolResult").asText());
        assertEquals("hello", enriched.path("displayResult").asText());
        assertTrue(enriched.path("toolSuccess").asBoolean());
        assertEquals("invalid", ToolResultPresentation.enrichPayload("invalid"));
    }

    @Test
    void oldPythonHistoryUsesMatchedToolArguments() throws Exception {
        String raw = "{\"exitCode\":0,\"stdout\":\"old output\",\"stderr\":\"\",\"timedOut\":false}";
        String payload = mapper.writeValueAsString(Map.of(
                "toolName", "runPythonInDocker", "toolResult", raw,
                "displayResult", "old output"));

        JsonNode enriched = mapper.readTree(ToolResultPresentation.enrichPayload(
                payload, "{\"pythonCode\":\"print(1)\"}"));
        assertEquals("print(1)", enriched.path("displayResult").asText());
        assertEquals("code", enriched.path("displayFormat").asText());
        assertEquals("python", enriched.path("language").asText());
        assertEquals(raw, enriched.path("toolResult").asText());
    }

    @Test
    void oldTerminalHistoryUsesMatchedToolArguments() throws Exception {
        String raw = "{\"exitCode\":1,\"stdout\":\"\",\"stderr\":\"not found\",\"timedOut\":false}";
        String payload = mapper.writeValueAsString(Map.of(
                "toolName", "execTerminal", "toolResult", raw,
                "displayResult", "退出码 1\nnot found"));

        JsonNode enriched = mapper.readTree(ToolResultPresentation.enrichPayload(
                payload, "{\"command\":\"ls missing\"}"));
        assertEquals("ls missing", enriched.path("displayResult").asText());
        assertEquals("code", enriched.path("displayFormat").asText());
        assertEquals("bash", enriched.path("language").asText());
        assertFalse(enriched.path("toolSuccess").asBoolean());
        assertEquals(raw, enriched.path("toolResult").asText());
    }
}
