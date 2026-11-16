package com.ai.aigent.service.impl;

import com.ai.aigent.constant.AiCientConstans;
import com.ai.aigent.consumer.SummaryChatEvent;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** 摘要批次的数据库状态机；每个事务只处理分配、领取或提交中的一个步骤。 */
@Service
public class SummaryBatchService {
    private static final int BATCH_SIZE = AiCientConstans.SHORT_MEMORY_LENGTH;
    private static final int MAX_ATTEMPTS = 3;
    private final JdbcTemplate db;

    public SummaryBatchService(@Qualifier("mysqlJdbcTemplate") JdbcTemplate db) {
        this.db = db;
    }

    // 消费者根据这个结果决定：执行模型、确认消息、延迟重试或转入失败队列。
    public enum Decision {CLAIMED, DONE, RETRY_LATER, FINAL_FAILURE}

    public record Claim(Decision decision, String batchId, String conversationId, String leaseToken) {
        static Claim of(Decision decision, String batchId, String conversationId) {
            return new Claim(decision, batchId, conversationId, null);
        }
    }

    public record BatchMessage(long id, byte[] payload) { }

    private record Candidate(long id, String type, boolean completed) { }

    @Transactional
    public List<String> createBatches(String conversationId) {
        // 同一会话可能同时写入消息；锁会话行后再分配，避免两个入口预留同一批消息。
        if (db.queryForList("SELECT id FROM conversation WHERE id = ? FOR UPDATE", String.class,
                conversationId).isEmpty()) return List.of();

        List<String> created = new ArrayList<>();
        while (true) {
            List<Candidate> candidates = db.query("""
                    SELECT id, message_type, completed FROM chat_message
                    WHERE conversation_id = ? AND archive_status = 0 AND is_deleted = 0
                    ORDER BY id LIMIT ?
                    """, (rs, row) -> new Candidate(rs.getLong("id"), rs.getString("message_type"),
                    rs.getBoolean("completed")), conversationId, BATCH_SIZE);
            // 不把仍在流式生成的助手占位消息放进摘要，否则会总结到空内容。
            if (candidates.size() < BATCH_SIZE || candidates.stream().anyMatch(
                    item -> "assistant".equalsIgnoreCase(item.type()) && !item.completed())) break;

            String batchId = UUID.randomUUID().toString();
            List<Long> ids = candidates.stream().map(Candidate::id).toList();
            // 批次记录与 10 条消息的预留在同一事务中提交；失败会一起回滚。
            db.update("""
                    INSERT INTO chat_summary_batch
                    (batch_id, conversation_id, status, retry_count, start_message_id, end_message_id)
                    VALUES (?, ?, 'PENDING', 0, ?, ?)
                    """, batchId, Long.parseLong(conversationId), ids.get(0), ids.get(ids.size() - 1));
            String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
            List<Object> args = new ArrayList<>();
            args.add(batchId);
            args.add(conversationId);
            args.addAll(ids);
            int reserved = db.update("UPDATE chat_message SET archive_status = 1, summary_batch_id = ? "
                    + "WHERE conversation_id = ? AND archive_status = 0 AND is_deleted = 0 "
                    + "AND id IN (" + placeholders + ")", args.toArray());
            if (reserved != BATCH_SIZE) throw new IllegalStateException("Summary batch reservation failed");
            created.add(batchId);
        }
        return created;
    }

    @Transactional
    public Claim claim(SummaryChatEvent event) {
        // 锁的是chat_summary_batch批次行。后续模型调用在本方法返回后进行，不占用数据库事务。
        List<ClaimRow> rows = db.query("""
                SELECT conversation_id, status, retry_count, start_message_id, lease_until
                FROM chat_summary_batch WHERE batch_id = ? FOR UPDATE
                """, (rs, row) -> new ClaimRow(rs.getString("conversation_id"), rs.getString("status"),
                rs.getInt("retry_count"), rs.getLong("start_message_id"),
                rs.getTimestamp("lease_until")), event.batchId());
        if (rows.isEmpty()) return Claim.of(Decision.FINAL_FAILURE, event.batchId(), event.conversationId());
        ClaimRow batch = rows.get(0);
        if (!batch.conversationId().equals(event.conversationId()))
            return Claim.of(Decision.FINAL_FAILURE, event.batchId(), event.conversationId());
        if ("SUCCESS".equals(batch.status())) return Claim.of(Decision.DONE, event.batchId(), event.conversationId());
        if ("FAILED".equals(batch.status())) return Claim.of(Decision.FINAL_FAILURE, event.batchId(), event.conversationId());

        // 同会话按批次顺序推进：B 必须等 A 成功，才能基于 A 的摘要继续生成。
        Integer previousFailed = db.queryForObject("""
                SELECT COUNT(*) FROM chat_summary_batch WHERE conversation_id = ?
                AND start_message_id < ? AND status = 'FAILED'
                """, Integer.class, Long.parseLong(event.conversationId()), batch.startMessageId());
        if (previousFailed != null && previousFailed > 0) {
            db.update("UPDATE chat_summary_batch SET status = 'FAILED', error_message = ? WHERE batch_id = ?",
                    "Previous summary batch failed", event.batchId());
            return Claim.of(Decision.FINAL_FAILURE, event.batchId(), event.conversationId());
        }
        Integer previousPending = db.queryForObject("""
                SELECT COUNT(*) FROM chat_summary_batch WHERE conversation_id = ?
                AND start_message_id < ? AND status <> 'SUCCESS'
                """, Integer.class, Long.parseLong(event.conversationId()), batch.startMessageId());
        if (previousPending != null && previousPending > 0) return Claim.of(Decision.RETRY_LATER, event.batchId(), event.conversationId());
        if ("PROCESSING".equals(batch.status()) && batch.leaseUntil() != null
                && batch.leaseUntil().after(Timestamp.valueOf(LocalDateTime.now())))
            return Claim.of(Decision.RETRY_LATER, event.batchId(), event.conversationId());
        if (batch.retryCount() >= MAX_ATTEMPTS) {
            db.update("UPDATE chat_summary_batch SET status = 'FAILED', error_message = ? WHERE batch_id = ?",
                    "Retry limit reached", event.batchId());
            return Claim.of(Decision.FINAL_FAILURE, event.batchId(), event.conversationId());
        }

        String token = UUID.randomUUID().toString();
        // retry_count 记录实际领取次数；lease_token 防止过期 Worker 迟到后覆盖新结果。
        db.update("""
                UPDATE chat_summary_batch SET status = 'PROCESSING', retry_count = retry_count + 1,
                lease_token = ?, lease_until = DATE_ADD(NOW(), INTERVAL 5 MINUTE), error_message = NULL
                WHERE batch_id = ?
                """, token, event.batchId());
        return new Claim(Decision.CLAIMED, event.batchId(), event.conversationId(), token);
    }

    public List<BatchMessage> loadMessages(Claim claim) {
        // 通过 summary_batch_id 查询精确成员；起止消息 ID 只用于批次顺序判断。
        return db.query("""
                SELECT id, payload FROM chat_message
                WHERE conversation_id = ? AND summary_batch_id = ? AND archive_status = 1
                ORDER BY id
                """, (rs, row) -> new BatchMessage(rs.getLong("id"), rs.getBytes("payload")),
                claim.conversationId(), claim.batchId());
    }

    public String currentSummary(String conversationId) {
        List<String> summaries = db.queryForList("SELECT summary FROM chat_summary WHERE conversation_id = ?",
                String.class, conversationId);
        return summaries.isEmpty() ? "" : summaries.get(0);
    }

    @Transactional
    public boolean complete(Claim claim, String generatedSummary) {
        // 只允许当前租约持有者提交。摘要追加、消息归档、批次成功必须一起提交。
        if (!ownsLease(claim)) return false;
        Integer count = db.queryForObject("""
                SELECT COUNT(*) FROM chat_message WHERE conversation_id = ?
                AND summary_batch_id = ? AND archive_status = 1
                """, Integer.class, claim.conversationId(), claim.batchId());
        if (count == null || count != BATCH_SIZE) throw new IllegalStateException("Summary batch members changed");

        String appended = "\n" + generatedSummary;
        int updatedSummary = db.update("""
                UPDATE chat_summary SET summary = CONCAT(COALESCE(summary, ''), ?), update_time = NOW()
                WHERE conversation_id = ?
                """, appended, claim.conversationId());
        if (updatedSummary == 0) db.update(
                "INSERT INTO chat_summary (conversation_id, summary, update_time) VALUES (?, ?, NOW())",
                claim.conversationId(), generatedSummary);
        int archived = db.update("""
                UPDATE chat_message SET archive_status = 2, is_deleted = 1
                WHERE conversation_id = ? AND summary_batch_id = ? AND archive_status = 1
                """, claim.conversationId(), claim.batchId());
        if (archived != BATCH_SIZE) throw new IllegalStateException("Summary archive count changed");
        db.update("""
                UPDATE chat_summary_batch SET status = 'SUCCESS', lease_token = NULL, lease_until = NULL
                WHERE batch_id = ?
                """, claim.batchId());
        return true;
    }

    @Transactional
    public Decision fail(Claim claim, Throwable error) {
        // 失败也先核对令牌，避免旧 Worker 把新 Worker 正在处理的批次改回 PENDING。
        if (!ownsLease(claim)) return Decision.RETRY_LATER;
        Integer attempts = db.queryForObject("SELECT retry_count FROM chat_summary_batch WHERE batch_id = ?",
                Integer.class, claim.batchId());
        boolean finalFailure = attempts != null && attempts >= MAX_ATTEMPTS;
        String detail = String.valueOf(error.getMessage());
        if (detail.length() > 1000) detail = detail.substring(0, 1000);
        db.update("""
                UPDATE chat_summary_batch SET status = ?, error_message = ?,
                lease_token = NULL, lease_until = NULL WHERE batch_id = ?
                """, finalFailure ? "FAILED" : "PENDING", detail, claim.batchId());
        return finalFailure ? Decision.FINAL_FAILURE : Decision.RETRY_LATER;
    }

    private boolean ownsLease(Claim claim) {
        List<String> tokens = db.queryForList("""
                SELECT lease_token FROM chat_summary_batch
                WHERE batch_id = ? AND status = 'PROCESSING' FOR UPDATE
                """, String.class, claim.batchId());
        return tokens.size() == 1 && claim.leaseToken().equals(tokens.get(0));
    }

    private record ClaimRow(String conversationId, String status, int retryCount,
                            long startMessageId, Timestamp leaseUntil) { }
}
