package com.ai.aigent.entity.VO;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ChatMessageVO {

    private Long id;

    private String conversationId;

    private String messageType;
    private String payload;
    List<AttachmentVO>aiAttachments;
    private Integer cost;


    private LocalDateTime createTime;
    //    private Integer isDeleted;
    //    private Boolean hasAttachment;
//    private byte[]attachment;
}
