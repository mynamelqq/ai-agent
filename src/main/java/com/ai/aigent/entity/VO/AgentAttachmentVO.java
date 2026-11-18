package com.ai.aigent.entity.VO;

import lombok.Data;

@Data
public class AgentAttachmentVO {
    private String id;
    private String fileName;
    private String fileUrl;
    private Long fileSize;
    private String fileType;
}
