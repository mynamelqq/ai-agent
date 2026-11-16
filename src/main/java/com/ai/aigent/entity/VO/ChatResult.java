package com.ai.aigent.entity.VO;

import lombok.Data;

@Data
public class ChatResult {
    private String eventType;
    private String messageId;
    private String text;
    private Integer seq;
    private String reasoning;
    private String finishReason;
    private Object toolCalls;

}
