package com.ai.aigent.service.impl;

import com.ai.aigent.entity.dto.StreamingSnapshotDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class CachedService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    private static final Duration STREAMING_TTL = Duration.ofMinutes(30);
    private static final Duration DONE_TTL = Duration.ofMinutes(30);
    public void save(String chatId, String messageId, String content, String reasoning, Integer seq, String status) {
        String key = buildKey(chatId, messageId);
        Map<String, String> map = new HashMap<>();
        map.put("chatId", chatId);
        map.put("messageId", String.valueOf(messageId));
        map.put("content", content == null ? "" : content);
        map.put("reasoning", reasoning == null ? "" : reasoning);
        map.put("seq", seq == null ? "0" : String.valueOf(seq));
        map.put("status", status == null ? "streaming" : status);
        map.put("updatedAt", String.valueOf(System.currentTimeMillis()));
        stringRedisTemplate.opsForHash().putAll(key, map);
        stringRedisTemplate.expire(key, STREAMING_TTL);
    }
    public StreamingSnapshotDTO getSnapshotDTO(String chatId, String messageId) {
        Map<Object, Object> entries = getSnapshot(chatId, messageId);
        if (entries == null || entries.isEmpty()) {
            return null;
        }
        StreamingSnapshotDTO dto = new StreamingSnapshotDTO();
        dto.setChatId((String) entries.getOrDefault("chatId", chatId));
        dto.setMessageId(Long.valueOf(String.valueOf(entries.getOrDefault("messageId", messageId))));
        dto.setContent((String) entries.getOrDefault("content", ""));
        dto.setReasoning((String) entries.getOrDefault("reasoning", ""));
        dto.setSeq(Integer.valueOf(String.valueOf(entries.getOrDefault("seq", "0"))));
        dto.setStatus((String) entries.getOrDefault("status", "streaming"));
        dto.setUpdatedAt(Long.valueOf(String.valueOf(entries.getOrDefault("updatedAt", "0"))));
        return dto;
    }
    public void markDone(String chatId, String messageId, String content, String reasoning) {
        String key = buildKey(chatId, messageId);

        Map<String, String> map = new HashMap<>();
        map.put("chatId", chatId);
        map.put("messageId", String.valueOf(messageId));
        map.put("content", content == null ? "" : content);
        map.put("reasoning", reasoning == null ? "" : reasoning);
        map.put("status", "done");
        map.put("updatedAt", String.valueOf(System.currentTimeMillis()));

        Object seq = stringRedisTemplate.opsForHash().get(key, "seq");
        if (seq == null) {
            map.put("seq", "0");
        }

        stringRedisTemplate.opsForHash().putAll(key, map);
        stringRedisTemplate.expire(key, DONE_TTL);
    }

    public void markCancelled(String chatId,String messageId, String content, String reasoning) {
        String key = buildKey(chatId, messageId);

        Map<String, String> map = new HashMap<>();
        map.put("chatId", chatId);
        map.put("messageId", String.valueOf(messageId));
        map.put("content", content == null ? "" : content);
        map.put("reasoning", reasoning == null ? "" : reasoning);
        map.put("status", "cancelled");
        map.put("updatedAt", String.valueOf(System.currentTimeMillis()));

        Object seq = stringRedisTemplate.opsForHash().get(key, "seq");
        if (seq == null) {
            map.put("seq", "0");
        }

        stringRedisTemplate.opsForHash().putAll(key, map);
        stringRedisTemplate.expire(key, DONE_TTL);
    }

    public Map<Object, Object> getSnapshot(String chatId, String messageId) {
        String key = buildKey(chatId, messageId);
        return stringRedisTemplate.opsForHash().entries(key);
    }

    public void delete(String chatId, String messageId) {
        stringRedisTemplate.delete(buildKey(chatId, messageId));
    }

    public boolean exists(String chatId, String messageId) {
        Boolean hasKey = stringRedisTemplate.hasKey(buildKey(chatId, messageId));
        return Boolean.TRUE.equals(hasKey);
    }

    private String buildKey(String chatId,String messageId) {
        return String.format("chat:stream:{%s}:{%s}", chatId, messageId);
    }

}
