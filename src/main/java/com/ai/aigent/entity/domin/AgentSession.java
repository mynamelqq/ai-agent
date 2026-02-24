package com.ai.aigent.entity.domin;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 
 * @TableName agent_session
 */
@TableName(value ="agent_session")
@Data
public class AgentSession {
    @TableId(type = IdType.AUTO)
    private String id;
    private Long userId;
    private String title;
    private String lastMessage;
    private Date createTime;
    private Date updateTime;
}