package com.ai.aigent.app;

import org.springframework.stereotype.Component;
import reactor.core.Disposable;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CancelTokenRegistry {
    private final Map<String, Disposable> tasks = new ConcurrentHashMap<>();
    // 单独记录"主动取消"的 chatId
    private final Set<String> cancelledIds = ConcurrentHashMap.newKeySet();
    public void register(String chatId, Disposable disposable) {
        tasks.put(chatId, disposable);
    }
    public void cancel(String chatId) {
        cancelledIds.add(chatId); // 先标记
        Disposable d = tasks.remove(chatId);
        if (d != null && !d.isDisposed()) d.dispose(); // 再取消
    }
    public boolean isCancelled(String chatId) {
        return cancelledIds.contains(chatId);
    }
    public void clear(String chatId) {
        tasks.remove(chatId);
        cancelledIds.remove(chatId);
    }
}