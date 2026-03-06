package com.ai.aigent.entity.dto;

import lombok.Data;

@Data
public class FileGenerateDTO {
    private boolean success;
    private String error;
    private String url;
    private String fileName;
    private Integer size;
    public static FileGenerateDTO ok(String content,Integer size,String fileName) {
        FileGenerateDTO dto = new FileGenerateDTO();
        dto.success = true;
        dto.url =content;
        dto.fileName=fileName;
        dto.size=size;
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
