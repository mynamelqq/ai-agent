package com.ai.aigent.consumer;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class RabbitMQConfig {
    @Bean
    public Queue summaryQueue() {
        return new Queue("summaryChat",true,false,false);
    }
    @Bean
    public TopicExchange summaryExchange() {
        return new TopicExchange("summaryChatExchange", true, false);
        // 参数说明：name, durable, autoDelete
    }
    @Bean
    public Binding summaryBinding() {
        return BindingBuilder.bind(summaryQueue())
                .to(summaryExchange())
                .with("summary.#");  // 使用通配符，更灵活
    }

}
