package com.ai.aigent.consumer;

/**
 * 队列只传批次定位信息；固定的 10 条成员保存在 MySQL 的 summary_batch_id 字段中。
 * 重试时沿用同一个 batchId，避免重新查询“最新的 10 条”而串批。
 */
public record SummaryChatEvent(String batchId, String conversationId) {
}
