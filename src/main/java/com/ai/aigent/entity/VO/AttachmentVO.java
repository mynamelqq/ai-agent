package com.ai.aigent.entity.VO;


import lombok.Data;

import java.util.Date;
@Data
public class AttachmentVO {

    private Long id;
    private String fileName;
    private String fileUrl;
    private Long fileSize;
    private String fileType;
}
