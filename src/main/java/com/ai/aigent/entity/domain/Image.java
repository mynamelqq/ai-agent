package com.ai.aigent.entity.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 
 * @TableName image
 */
@TableName(value ="image")
@Data
public class Image {
    @TableId
    private Integer id;
    private String userId;
    private String link;
    private Date createdAt;
    private String conservationId;
}