package com.ai.aigent.entity.dto;

import lombok.Data;

import java.util.List;

@Data
public class ChatSendReq {
    private String chatId;
    private String message;
    private List<Long> attachments;  // 关键：附件数组
    private String messageId;
    private String token;
    private Boolean isThinking;
    private Boolean isImageGen;
    private Boolean isSearch;
    private Boolean isGenerateFile;
    private Boolean isEcharts;
    private String style;
    private String model;
    private Double temperature;
    private Double topP;
}