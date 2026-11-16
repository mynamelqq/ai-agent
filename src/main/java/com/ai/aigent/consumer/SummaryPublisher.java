package com.ai.aigent.consumer;

import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpMessageReturnedException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/** 批次表兼作待发布记录：先提交 MySQL，再由定时任务发布到 RabbitMQ。 */
@Component
@Slf4j
public class SummaryPublisher {
    private final JdbcTemplate db;
    private final RabbitTemplate rabbit;

    public SummaryPublisher(@Qualifier("mysqlJdbcTemplate") JdbcTemplate db, RabbitTemplate rabbit) {
        this.db = db;
        this.rabbit = rabbit;
    }

    @Scheduled(fixedDelayString = "${summary.outbox.poll-ms:5000}")
    public void publishPending() {
        // Worker 崩溃后，租约到期的任务重新进入待发布状态；旧令牌随之失效。
        db.update("""
                UPDATE chat_summary_batch SET status = 'PENDING', lease_token = NULL,
                lease_until = NULL, published_at = NULL
                WHERE status = 'PROCESSING' AND lease_until < NOW()
                """);
        // 只取已提交且从未确认发布的 PENDING 批次；这里不直接调用模型。
        List<SummaryChatEvent> pending = db.query("""
                SELECT batch_id, conversation_id FROM chat_summary_batch
                WHERE status = 'PENDING' AND published_at IS NULL
                ORDER BY created_at LIMIT 50
                """, (rs, row) -> new SummaryChatEvent(rs.getString("batch_id"),
                rs.getString("conversation_id")));
        for (SummaryChatEvent event : pending) {
            try {
                // Broker 不可用时不设置 published_at，下轮扫描会继续发布。
                publishConfirmed(RabbitMQConfig.MAIN_EXCHANGE, RabbitMQConfig.MAIN_KEY, event);
                // 确认后才记 published_at；若此处崩溃会重复发布，由批次状态去重。
                db.update("UPDATE chat_summary_batch SET published_at = NOW() "
                        + "WHERE batch_id = ? AND published_at IS NULL", event.batchId());
            } catch (Exception e) {
                log.warn("Summary batch publish failed: {}", event.batchId(), e);
                break;
            }
        }
    }

    public void publishConfirmed(String exchange, String routingKey, SummaryChatEvent event) {
        // 正常、重试和最终失败都发送同一个批次身份，消费端据此做幂等判断。
        publishRawConfirmed(exchange, routingKey, JSONUtil.toJsonStr(event), event.batchId());
    }

    public void publishRawConfirmed(String exchange, String routingKey, String payload, String correlationId) {
        // confirm 表示 Broker 收到发布；mandatory return 用来发现交换机没有路由到队列。
        CorrelationData correlation = new CorrelationData(correlationId);//CorrelationData 是 Spring AMQP 中用来关联“已发送消息”与“Broker 返回的确认结果” 的载体。
        rabbit.convertAndSend(exchange, routingKey, payload, correlation);
        try {
            CorrelationData.Confirm confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);//最多阻塞5秒等待
            if (correlation.getReturned() != null) {//是否被退回
                throw new AmqpMessageReturnedException("Summary message was not routed",
                        correlation.getReturned());
            }
            if (!confirm.isAck()) {
                throw new IllegalStateException("RabbitMQ rejected summary publish: " + confirm.getReason());
            }
            // 返回前完成确认；调用方此时才可 ACK 原消息或标记 published_at。
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Summary publish interrupted", e);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
            throw new IllegalStateException("Summary publish confirm unavailable", e);
        }
    }
}
