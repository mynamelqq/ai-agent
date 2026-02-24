package com.ai.aigent.entity.dto;

import lombok.Data;

@Data
public class FileGenerateDTO {
    private boolean success;
    private String error;
    private String url;
    public static FileGenerateDTO ok(String content,Integer size) {
        FileGenerateDTO dto = new FileGenerateDTO();
        dto.success = true;
        dto.url =content+"?size="+size;
        dto.error="";
        return dto;
    }
    public static FileGenerateDTO error(String errorContent) {
        FileGenerateDTO dto = new FileGenerateDTO();
        dto.success = false;
        dto.url ="";
        dto.error=errorContent;
        return dto;
    }
}
