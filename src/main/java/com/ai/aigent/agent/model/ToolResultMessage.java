package com.ai.aigent.agent.model;
import cn.hutool.core.collection.CollectionUtil;
import lombok.Getter;
import lombok.Setter;
import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;

import java.util.Collections;
import java.util.Map;

@Setter
@Getter
public class ToolResultMessage extends ToolResponseMessage {
    private String toolName;
    private String toolResult;
    public ToolResultMessage(String textContent, Map<String, Object> metadata,String toolName) {
        super(Collections.emptyList(), metadata);
        this.toolName=toolName;
        this.toolResult=textContent;

    }

}
