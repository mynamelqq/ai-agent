package com.ai.aigent.agent.model;

import lombok.Data;

import java.util.Date;
@Data
public class AgentAttachment {
    private Long id;
    private String fileName;
    private Long fileSize;
    private String fileType;
}
