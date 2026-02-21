package com.ai.aigent.entity.VO;

import com.ai.aigent.agent.model.AgentMessagePayload;
import lombok.Data;
import org.springframework.ai.chat.messages.AssistantMessage;

import java.util.Date;
import java.util.List;

@Data
public class AgentMessageVO {
    private Long id;
    private String type;
    private String payload;
    private Date createdAt;
}