package com.ai.aigent.agent.model;

import com.ai.aigent.agent.BaseAgent;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AgentSessionRegistry {
     private final Map<String, BaseAgent> sessions = new ConcurrentHashMap<>();
        public void register(String sessionId, BaseAgent agent) {
            sessions.put(sessionId, agent);
        }

        public void cancel(String sessionId) {
            BaseAgent agent = sessions.get(sessionId);
            if (agent != null) {
                agent.cancel();
            }
        }
        public void remove(String sessionId) {
            sessions.remove(sessionId);
        }
}