package com.ai.aigent.service;

import com.ai.aigent.agent.model.AgentMessagePayload;
import com.ai.aigent.entity.domain.AgentMessage;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
* @author MECHREVO
* @description 针对表【agent_message】的数据库操作Service
* @createDate 2026-02-20 14:41:20
*/
public interface AgentMessageService extends IService<AgentMessage> {
AgentMessage saveAgentMessage(String sessionId, Long runId, String type, AgentMessagePayload payload, Integer token,
                              Long thinkingTime,Long cost, Integer promptTokens,  Integer completionTokens,Integer cachedTokens);
    List<AgentMessage> listAgentMessages(String sessionId);
}
