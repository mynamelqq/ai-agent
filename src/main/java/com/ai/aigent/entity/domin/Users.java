package com.ai.aigent.entity.domin;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 
 * @TableName users
 */
@TableName(value ="users")
@Data
public class Users {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    @TableField(value = "password_hash")
    private String password;

    private String avatarUrl;
    private Integer dailyTokenLimit;
    private Long totalTokensUsed;
    private String ip;
    private String nickname;
    private Integer status;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime lastLoginAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;
}