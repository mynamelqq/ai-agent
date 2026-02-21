package com.ai.aigent.entity.VO;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ChatMessageVO {

    private String id;

    private String conversationId;
    private Integer token;
    private String messageType;
    private String payload;
    List<AttachmentVO>aiAttachments;
    private Integer cost;
    private LocalDateTime createTime;
    private Boolean isThinking;
    private Boolean isImage;
    private Boolean isGenerate;
    private Boolean isEcharts;
    private Boolean isSearch;
}
