package com.ai.aigent.agent.model;

import com.ai.aigent.agent.BaseAgent;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AgentSessionRegistry {
     private final Map<Long, BaseAgent> sessions = new ConcurrentHashMap<>();

        public void register(Long sessionId, BaseAgent agent) {
            sessions.put(sessionId, agent);
        }

        public void cancel(Long sessionId) {
            BaseAgent agent = sessions.get(sessionId);
            if (agent != null) {
                agent.cancel();
            }
        }

        public void remove(Long sessionId) {
            sessions.remove(sessionId);
        }
}