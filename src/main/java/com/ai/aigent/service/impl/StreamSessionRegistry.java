package com.ai.aigent.service.impl;

import com.ai.aigent.entity.VO.ChatResult;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Sinks;

import java.util.concurrent.ConcurrentHashMap;

@Service
public class StreamSessionRegistry {
    private final ConcurrentHashMap<String, Sinks.Many<ChatResult>> sinkMap = new ConcurrentHashMap<>();//并发集合

    public void register(String chatId, Long messageId, Sinks.Many<ChatResult> sink) {
        sinkMap.put(buildKey(chatId, messageId), sink);
    }

    public Sinks.Many<ChatResult> get(String chatId, Long messageId) {
        return sinkMap.get(buildKey(chatId, messageId));
    }

    public void remove(String chatId, Long messageId) {
        sinkMap.remove(buildKey(chatId, messageId));
    }

    private String buildKey(String chatId, Long messageId) {
        return chatId + ":" + messageId;
    }
}
