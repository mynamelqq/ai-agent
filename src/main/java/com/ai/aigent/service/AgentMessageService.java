package com.ai.aigent.service;

import com.ai.aigent.agent.model.AgentMessagePayload;
import com.ai.aigent.entity.domin.AgentMessage;
import com.baomidou.mybatisplus.extension.service.IService;

/**
* @author MECHREVO
* @description 针对表【agent_message】的数据库操作Service
* @createDate 2026-02-20 14:41:20
*/
public interface AgentMessageService extends IService<AgentMessage> {
AgentMessage saveAgentMessage(String sessionId, String type, AgentMessagePayload payload, Integer token,
                              Long thinkingTime,Long cost, Integer promptTokens,  Integer completionTokens,Integer cachedTokens);

}
