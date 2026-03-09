package com.ai.aigent.entity.domin;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

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