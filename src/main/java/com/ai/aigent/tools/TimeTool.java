package com.ai.aigent.tools;


import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
@Component
public class TimeTool {
    @Tool(name = "getCurrentTime", description = "获取当前时间，可指定时区，如 Asia/Shanghai、UTC、Asia/Singapore")
    public String getCurrentTime(String zoneId) {
        ZoneId zone;
        if (zoneId == null || zoneId.isBlank()) {
            zone = ZoneId.systemDefault();
        } else {
            zone = ZoneId.of(zoneId);
        }
        LocalDateTime now = LocalDateTime.now(zone);
        return now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}