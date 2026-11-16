package com.ai.aigent.entity.dto;

import lombok.Data;

@Data
public class UploadFileDTO {
    private String chat_id;
    private String conservation_id;
    private String file_name;
    private String file_url;
    private Long file_size;

    private String file_type;


}