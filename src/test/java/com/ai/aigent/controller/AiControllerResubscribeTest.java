package com.ai.aigent.controller;

import com.ai.aigent.entity.VO.ChatResult;
import com.ai.aigent.service.impl.CachedService;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiControllerResubscribeTest {
    @Test
    void replaysMissedEventsAndStopsAtDoneWithoutLiveSink() {
        CachedService cache = mock(CachedService.class);
        AiController controller = new AiController();
        ReflectionTestUtils.setField(controller, "streamingCacheService", cache);

        List<ChatResult> events = List.of(event(4, "chunk"), event(5, "chunk"), event(6, "done"));
        when(cache.getEventsAfter(eq("chat"), eq("123"), anyInt()))
                .thenAnswer(invocation -> events.stream()
                        .filter(event -> event.getSeq() > invocation.getArgument(2, Integer.class))
                        .toList());

        List<ServerSentEvent<ChatResult>> received = controller.resubscribe("chat", 123L, 3)
                .collectList().block(Duration.ofSeconds(2));

        assertEquals(List.of("4", "5", "6"), received.stream().map(ServerSentEvent::id).toList());
        assertEquals(List.of("chunk", "chunk", "done"),
                received.stream().map(item -> item.data().getEventType()).toList());
    }

    private static ChatResult event(int seq, String type) {
        ChatResult result = new ChatResult();
        result.setSeq(seq);
        result.setEventType(type);
        return result;
    }
}
