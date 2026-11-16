package com.ai.aigent.agent.model;

import com.ai.aigent.agent.BaseAgent;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AgentSessionRegistry {
     private final Map<String, BaseAgent> sessions = new ConcurrentHashMap<>();
        public boolean register(String sessionId, BaseAgent agent) {
            return sessions.putIfAbsent(sessionId, agent) == null;
        }

        public void cancel(String sessionId) {
            BaseAgent agent = sessions.get(sessionId);
            if (agent != null) {
                agent.cancel();
            }
        }
        public boolean isRunning(String sessionId) {
            return sessions.containsKey(sessionId);
        }
        public void remove(String sessionId, BaseAgent agent) {
            sessions.remove(sessionId, agent);
        }
}
