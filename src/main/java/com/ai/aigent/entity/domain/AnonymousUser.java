package com.ai.aigent.entity.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 匿名用户表
 * @TableName anonymous_user
 */
@TableName(value ="anonymous_user")
@Data
public class AnonymousUser {
    /**
     * 匿名ID（UUID）
     */
    @TableId
    private String anonId;

    /**
     * 设备指纹（哈希值）
     */
    private String fingerprintHash;

    /**
     * IP地址（支持IPv6）
     */
    private String ip;

    /**
     * 创建时间
     */
    private Date createdAt;

    /**
     * 最后活跃时间
     */
    private Date lastActiveAt;

    /**
     * 每日token限额
     */
    private Integer dailyTokenLimit;

    /**
     * 累计使用token数
     */
    private Long totalTokensUsed;
}