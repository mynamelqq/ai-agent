package com.ai.aigent.tools;


import com.ai.aigent.entity.dto.TimeResponseDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
@Component
public class TimeTool {
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Tool(name = "getCurrentTime", description = "获取当前时间，可指定时区，如 Asia/Shanghai、UTC、Asia/Singapore")
    public String getCurrentTime(String zoneId) {
        ZoneId zone;
        if (zoneId == null || zoneId.isBlank()) {
            zone = ZoneId.systemDefault();
        } else {
            zone = ZoneId.of(zoneId);
        }
        LocalDateTime now = LocalDateTime.now(zone);
        return toJson(TimeResponseDTO.ok(now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
    }
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{\"success\":false,\"error\":\"JSON序列化失败\"}";
        }
    }
}