package com.ai.aigent.entity.VO;

import com.ai.aigent.agent.model.AgentMessagePayload;
import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
public class AgentMessageVO {
    private Long id;
    private String type;         // user | assistant | tool
    private String content;      // 正文
    private String reasoning;    // 推理内容
    private List<AgentMessagePayload.ToolCallRecord> toolCalls;
    private Date createdAt;
}