package com.ai.aigent.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig2  implements WebSocketConfigurer {
    private final TtsWebSocketHandler ttsWebSocketHandler;
    public WebSocketConfig2(TtsWebSocketHandler ttsWebSocketHandler) {
        this.ttsWebSocketHandler = ttsWebSocketHandler;
    }
   @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(ttsWebSocketHandler, "/ws/tts")
                .setAllowedOrigins("*");
    }
}
