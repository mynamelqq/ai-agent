package com.ai.aigent.entity.VO;

import lombok.Data;

import java.time.LocalDateTime;
@Data
public class ChatMessageVO {

    private Long id;

    private String conversationId;

    private String messageType;
    private String payload;




    private LocalDateTime createTime;
    //    private Integer isDeleted;
    //    private Boolean hasAttachment;
//    private byte[]attachment;
}
