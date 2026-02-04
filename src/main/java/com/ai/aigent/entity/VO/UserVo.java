package com.ai.aigent.entity.VO;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserVo {
    private String username;
    private Long id;
    private String avatar_url;
    private String nickname;
    private LocalDateTime last_login_at;
    private LocalDateTime created_at;
    private String token;
}
