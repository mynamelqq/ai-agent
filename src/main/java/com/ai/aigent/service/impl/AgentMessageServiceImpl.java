package com.ai.aigent.service.impl;

import cn.hutool.json.JSONUtil;
import com.ai.aigent.agent.model.AgentMessagePayload;
import com.ai.aigent.entity.domain.AgentMessage;
import com.ai.aigent.mapper.AgentMessageMapper;
import com.ai.aigent.service.AgentMessageService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
* @author MECHREVO
* @description 针对表【agent_message】的数据库操作Service实现
* @createDate 2026-02-20 14:41:20
*/
@Service
public class AgentMessageServiceImpl extends ServiceImpl<AgentMessageMapper, AgentMessage>
    implements AgentMessageService{
    public AgentMessage saveAgentMessage(String sessionId, String type, AgentMessagePayload payload,
                                         Integer token,Long thinkingTime,Long cost, Integer promptTokens,
                                         Integer completionTokens,Integer cachedTokens)
    {
        AgentMessage msg = new AgentMessage();
        msg.setPayload(JSONUtil.toJsonStr(payload));
        msg.setSessionId(sessionId);
        msg.setCost(safeToDbInt(cost));
        msg.setCachedTokens(cachedTokens);
        msg.setPromptTokens(promptTokens);
        msg.setCompletionTokens(completionTokens);
        msg.setType(type);
        msg.setThinkingTime(safeToDbInt(thinkingTime));
        msg.setCreatedAt(new java.util.Date());
        msg.setTokens(token);
        return save(msg)?msg:null;
    }

    private static Integer safeToDbInt(Long value) {
        if (value == null) {
            return 0;
        }
        if (value <= 0L) {
            return 0;
        }
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return value.intValue();
    }

}




