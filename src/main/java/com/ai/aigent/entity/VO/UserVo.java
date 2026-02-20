package com.ai.aigent.entity.VO;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserVo {
    private String username;
    private String id;
    private String avatarUrl;
    private String nickname;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private String token;
}
