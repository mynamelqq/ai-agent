package com.ai.aigent.entity.VO;

import lombok.Data;

import java.util.Date;

@Data
public class AgentSessionVO {
    private Long sessionId;
    private String title;
    private String lastMessage;
    private Date createTime;
    private Date updateTime;
}
