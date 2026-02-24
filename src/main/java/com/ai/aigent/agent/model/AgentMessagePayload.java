package com.ai.aigent.agent.model;

import com.ai.aigent.entity.domin.AgentMessage;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Date;
@Data
public class AgentMessagePayload {
    private String role;          // user | assistant | tool
    private String content;       // 正文
    private String reasoning;     // 推理内容
    private String toolCallId;     // 工具调用唯一ID
    private String toolName;       // 工具名
    private String toolArgs;       // 入参 JSON
    private String toolResult;     // 结果
    @Data
    @AllArgsConstructor
    public static class ToolCallRecord {
        private String toolName;
        private String args;
        private String result;
    }
    private void saveAgentMessage(String sessionId, AgentMessage payload, int tokens) {
        AgentMessage msg = new AgentMessage();
        msg.setSessionId(sessionId);
        msg.setType(payload.getType());
        msg.setTokens(tokens);
        msg.setCreatedAt(new Date());
//        msg.setPayload(JSON(payload)); // byte[]
//        agentMessageMapper.insert(msg);
    }
}
