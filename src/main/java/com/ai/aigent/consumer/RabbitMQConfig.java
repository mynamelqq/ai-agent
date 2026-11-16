package com.ai.aigent.consumer;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;


/**
 * 摘要消息拓扑：主队列消费；重试队列延迟 30 秒后回主队列；最终失败进入独立队列。
 * summaryChat 是既有队列，因此这里不修改它的声明参数，避免与 Broker 上的旧队列冲突。
 */
@Configuration
@EnableScheduling
public class RabbitMQConfig {
    // 继续使用原有正常交换机和路由键，避免切换时遗漏旧入口。
    public static final String MAIN_EXCHANGE = "summaryChatExchange";
    public static final String MAIN_KEY = "summary";
    public static final String RETRY_EXCHANGE = "summaryChat.retry.exchange";
    public static final String RETRY_KEY = "summaryChat.retry";
    public static final String DEAD_EXCHANGE = "summaryChat.dlx";
    public static final String DEAD_KEY = "summaryChat.dead";
    private static final String SUMMARY_FAILED_EXCHANGE = "summaryChatFailedExchange";
    private static final String SUMMARY_FAILED_ROUTING_KEY = "summary.failed";

    @Bean
    public Queue summaryQueue() {
        // 正常队列仍由 RabbitConsumer 监听；业务失败由消费者显式转发。
        return new Queue("summaryChat",true,false,false);
    }
    @Bean
    public TopicExchange summaryExchange() {
        return new TopicExchange(MAIN_EXCHANGE, true, false);
    }
    @Bean
    public Binding summaryBinding() {
        // 发布器发送到 MAIN_EXCHANGE + MAIN_KEY，经过此绑定进入 summaryChat。
        return BindingBuilder.bind(summaryQueue())
                .to(summaryExchange())
                .with("summary.#");
    }

    @Bean
    public DirectExchange summaryRetryExchange() {
        return new DirectExchange(RETRY_EXCHANGE, true, false);
    }

    @Bean
    public Queue summaryRetryQueue() {
        // 消费者把暂不能处理的批次投到这里；TTL 到期后走重试队列的 DLX 回主队列。
        // 这里的 DLX 只作用于重试队列，不能让主队列的失败消息自动进入最终 DLQ。
        return QueueBuilder.durable("summaryChat.retry")
                .ttl(30_000)
                .deadLetterExchange(MAIN_EXCHANGE)
                .deadLetterRoutingKey(MAIN_KEY)
                .build();
    }

    @Bean
    public Binding summaryRetryBinding() {
        // 应用向 RETRY_EXCHANGE + RETRY_KEY 发布，消息才会进入 30 秒延迟队列。
        return BindingBuilder.bind(summaryRetryQueue()).to(summaryRetryExchange()).with(RETRY_KEY);
    }

    @Bean
    public DirectExchange summaryDeadExchange() {
        return new DirectExchange(DEAD_EXCHANGE, true, false);
    }

    @Bean
    public Queue summaryDeadQueue() {
        // 当前最终失败由消费者确认发布到这里；并非主队列自动拒绝产生的原生死信。
        return QueueBuilder.durable("summaryChat.dlq").build();
    }

    @Bean
    public Binding summaryDeadBinding() {
        // 失败队列没有消费者，消息保留给排查和人工重放。
        return BindingBuilder.bind(summaryDeadQueue()).to(summaryDeadExchange()).with(DEAD_KEY);
    }

    @Bean
    public Queue summaryFailedQueue() {
        // 旧版应用层失败队列；新摘要链路使用 summaryChat.dlq。
        return new Queue("summaryChat.failed", true);
    }

    @Bean
    public TopicExchange summaryFailedExchange() {
        return new TopicExchange(SUMMARY_FAILED_EXCHANGE, true, false);
    }

    @Bean
    public Binding summaryFailedBinding() {
        return BindingBuilder.bind(summaryFailedQueue())
                .to(summaryFailedExchange())
                .with(SUMMARY_FAILED_ROUTING_KEY);
    }

    @Bean("summaryRabbitListenerContainerFactory")
    public SimpleRabbitListenerContainerFactory summaryRabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        // 关闭自动 ACK：消费者先确认重试/DLQ 投递成功，再确认源队列消息。
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        return factory;
    }
    @Bean
    public Queue chatEmbeddingQueue() {
        return new Queue("ChatVectorStoreQueue",true,false,false);
    }
    @Bean
    public TopicExchange chatEmbeddingExchange() {
        return new TopicExchange("ChatVectorStoreExchange", true, false);
        // 参数说明：name, durable, autoDelete
    }
    @Bean
    public Binding chatEmbeddingBinding() {
        return BindingBuilder.bind(chatEmbeddingQueue())
                .to(chatEmbeddingExchange())
                .with("ChatVectorStore.#");  // 使用通配符，更灵活
    }
}
