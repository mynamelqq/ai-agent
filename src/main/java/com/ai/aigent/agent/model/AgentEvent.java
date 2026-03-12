package com.ai.aigent.agent.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

@Data
@AllArgsConstructor
public class AgentEvent {
    private String type;   // reasoning | text | tool_call | tool_result | step_start | done | error
    private String content;
    private Map<String, Object> meta; // 额外信息，如 toolName、stepNum

    public static AgentEvent reasoning(String content) {
        return new AgentEvent("reasoning", content, null);
    }
    public static AgentEvent text(String content) {
        return new AgentEvent("text", content, null);
    }
    public static AgentEvent toolCall(String toolName, String args) {
        return new AgentEvent("tool_call", args, Map.of("toolName", toolName));
    }
    public static AgentEvent toolResult(String toolName, String result,Map<String,Object>meta) {
        meta.put("toolName", toolName);
        return new AgentEvent("tool_result", result, meta);
    }
    public static AgentEvent mcpToolCall(String toolName, String args) {
        return new AgentEvent("mcp_call", args, Map.of("toolName", toolName));
    }
    public static AgentEvent mcpToolResult(String toolName, String result,Map<String,Object>meta) {
        meta.put("toolName", toolName);
        return new AgentEvent("mcp_result", result, meta);
    }
    public static AgentEvent stepStart(int step) {
        return new AgentEvent("step_start", "Step " + step, Map.of("step", step));
    }
    public static AgentEvent done() {
        return new AgentEvent("done", "", null);
    }
    public static AgentEvent attachmentLoading() {
        return new AgentEvent("attachment_loading", "正在读取附件...", null);
    }

    public static AgentEvent attachmentLoaded() {
        return new AgentEvent("attachment_loaded", "附件读取完成", null);
    }
}