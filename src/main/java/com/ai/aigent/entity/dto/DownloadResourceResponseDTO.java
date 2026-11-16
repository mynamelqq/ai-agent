package com.ai.aigent.entity.dto;

import lombok.Data;

@Data
public class DownloadResourceResponseDTO {
    private boolean success;
    private String url;
    private String fileName;
    private String savedPath;
    private String cosUrl;
    private long size;
    private String error;

    public static DownloadResourceResponseDTO ok(
            String url, String fileName, String savedPath, String cosUrl, long size) {
        DownloadResourceResponseDTO dto = new DownloadResourceResponseDTO();
        dto.success = true;
        dto.url = url;
        dto.fileName = fileName;
        dto.savedPath = savedPath;
        dto.cosUrl = cosUrl;
        dto.size = size;
        return dto;
    }

    public static DownloadResourceResponseDTO error(String url, String error) {
        DownloadResourceResponseDTO dto = new DownloadResourceResponseDTO();
        dto.success = false;
        dto.url = url;
        dto.error = error;
        return dto;
    }

}
