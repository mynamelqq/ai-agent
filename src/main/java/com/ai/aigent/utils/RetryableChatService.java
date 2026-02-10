package com.ai.aigent.utils;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.retry.annotation.Retryable;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Service;

@Service
public class RetryableChatService {

    @Resource
    private OpenAiChatModel openAiChatModel;

    private int currentModelIndex = 0;
    private final String[] models = {
        "gpt-4-turbo-preview",
        "gpt-4-0613",
        "gpt-3.5-turbo-16k",
        "gpt-3.5-turbo"
    };

    @Retryable(
        value = {RateLimitException.class},
        maxAttempts = 4, // 最多尝试4个模型
        backoff = @Backoff(delay = 1000)
    )
    public String callWithRetry(String prompt) throws RateLimitException {
        String model = models[currentModelIndex % models.length];

        try {
            OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(model)
                .temperature(0.7)
                .build();

            ChatModel chatModel = openAiChatModel.mutate()
                .defaultOptions(options)
                .build();

            return chatModel.call(prompt);

        } catch (Exception e) {
            if (isRateLimitError(e)) {
                currentModelIndex++; // 切换到下一个模型
                throw new RateLimitException("Rate limit for model: " + model, e);
            }
            throw e;
        }
    }

    // 自定义异常类
    public static class RateLimitException extends RuntimeException {
        public RateLimitException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private boolean isRateLimitError(Exception e) {
        // 判断逻辑
        return true;
    }
}