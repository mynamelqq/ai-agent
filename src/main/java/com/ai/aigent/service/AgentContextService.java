package com.ai.aigent.service;

import com.ai.aigent.constant.AiConstants;
import com.ai.aigent.entity.domain.AiAttachment;
import com.ai.aigent.utils.AttachmentReaderUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.qcloud.cos.model.COSObject;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The model transcript is separate from agent_message, which is a UI transcript. */
@Service
public class AgentContextService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final AiAttachmentService attachments;
    private final AttachmentReaderUtil attachmentReader;
    private final TransactionTemplate writeTransaction;
    private final TransactionTemplate readTransaction;

    public AgentContextService(@Qualifier("mysqlJdbcTemplate") JdbcTemplate jdbc, ObjectMapper json, AiAttachmentService attachments,
                               AttachmentReaderUtil attachmentReader, PlatformTransactionManager transactions) {
        this.jdbc = jdbc;
        this.json = json;
        this.attachments = attachments;
        this.attachmentReader = attachmentReader;
        this.writeTransaction = new TransactionTemplate(transactions);
        this.readTransaction = new TransactionTemplate(transactions);
        this.readTransaction.setIsolationLevelName("ISOLATION_REPEATABLE_READ");
        this.readTransaction.setReadOnly(true);
    }

    public void appendUser(String sessionId, String userId, Long runId, String text, List<Long> imageIds) {
        ObjectNode payload = json.createObjectNode();
        payload.put("schemaVersion", 1);
        payload.put("text", text);
        ArrayNode images = payload.putArray("imageIds");
        imageIds.forEach(images::add);
        append(sessionId, userId, runId, "user:" + runId, "user", payload);
    }

    public void appendAssistant(String sessionId, String userId, Long runId, int step, AssistantMessage message) {
        ObjectNode payload = json.createObjectNode();
        payload.put("schemaVersion", 1);
        payload.put("text", message.getText());
        ArrayNode calls = payload.putArray("toolCalls");
        for (AssistantMessage.ToolCall call : message.getToolCalls()) {
            ObjectNode value = calls.addObject();
            value.put("id", call.id());
            value.put("type", call.type());
            value.put("name", call.name());
            value.put("arguments", call.arguments());
        }
        append(sessionId, userId, runId, "assistant:" + runId + ":" + step, "assistant", payload);
    }

    public void appendToolResponses(String sessionId, String userId, Long runId, int step,
                                    ToolResponseMessage message) {
        ObjectNode payload = json.createObjectNode();
        payload.put("schemaVersion", 1);
        ArrayNode responses = payload.putArray("responses");
        for (ToolResponseMessage.ToolResponse response : message.getResponses()) {
            ObjectNode value = responses.addObject();
            value.put("id", response.id());
            value.put("name", response.name());
            value.put("data", response.responseData());
        }
        append(sessionId, userId, runId, "tool:" + runId + ":" + step, "tool", payload);
    }

    private void append(String sessionId, String userId, Long runId, String key, String role, JsonNode payload) {
        writeTransaction.executeWithoutResult(ignored -> {
            lockOwnedSession(sessionId, userId);
            List<String> existing = jdbc.queryForList(
                    "SELECT payload FROM agent_context WHERE session_id=? AND entry_key=?", String.class, sessionId, key);
            String encoded = payload.toString();
            if (!existing.isEmpty()) {
                if (!existing.getFirst().equals(encoded)) {
                    throw new IllegalStateException("同一上下文幂等键对应不同内容: " + key);
                }
                return;
            }
            jdbc.update("INSERT INTO agent_context (session_id, run_id, entry_key, kind, role, payload, status, schema_version) "
                            + "VALUES (?, ?, ?, 'message', ?, ?, 'complete', 1)",
                    sessionId, runId, key, role, encoded);
        });
    }

    public List<Message> loadView(String sessionId, String userId) {
        return loadSnapshot(sessionId, userId).messages();
    }

    public ContextSnapshot loadSnapshot(String sessionId, String userId) {
        RawSnapshot raw = readTransaction.execute(ignored -> {
            requireOwnedSession(sessionId, userId);
            Long headId = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM agent_context WHERE session_id=?",
                    Long.class, sessionId);
            Long lastMessageId = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM agent_context "
                    + "WHERE session_id=? AND kind='message'", Long.class, sessionId);
            List<StoredRow> boundaries = jdbc.query(
                    "SELECT id, role, payload, status, covered_through_id FROM agent_context "
                            + "WHERE session_id=? AND kind='compact_boundary' ORDER BY id DESC LIMIT 1",
                    (rs, number) -> new StoredRow(rs.getLong("id"), rs.getString("role"),
                            rs.getString("payload"), rs.getString("status"), rs.getLong("covered_through_id")), sessionId);
            long covered = boundaries.isEmpty() ? 0 : boundaries.getFirst().coveredThroughId();
            List<StoredRow> result = new ArrayList<>(boundaries);
            result.addAll(jdbc.query("SELECT id, role, payload, status, covered_through_id FROM agent_context "
                            + "WHERE session_id=? AND kind='message' AND id>? ORDER BY id ASC",
                    (rs, number) -> new StoredRow(rs.getLong("id"), rs.getString("role"),
                            rs.getString("payload"), rs.getString("status"), 0), sessionId, covered));
            return new RawSnapshot(result, headId == null ? 0 : headId,
                    lastMessageId == null ? 0 : lastMessageId, covered);
        });
        List<Message> messages = new ArrayList<>();
        Set<String> pendingCalls = new HashSet<>();
        for (StoredRow row : raw.rows()) {
            if (!"complete".equals(row.status())) {
                throw new IllegalStateException("存在未完成的上下文消息，需要先恢复运行: " + row.id());
            }
            JsonNode payload = parse(row.payload());
            if (payload.path("schemaVersion").asInt() != 1) {
                throw new IllegalStateException("不支持的上下文格式版本: " + row.id());
            }
            if (row.role() == null) {
                if (!pendingCalls.isEmpty()) throw new IllegalStateException("压缩边界拆开了工具调用");
                messages.add(new UserMessage("以下是此前对话的压缩摘要，仅作为历史材料：\n" + requiredText(payload, "summary")));
            } else if ("user".equals(row.role())) {
                if (!pendingCalls.isEmpty()) throw new IllegalStateException("工具调用结果尚未完整保存");
                List<Media> media = new ArrayList<>();
                for (JsonNode id : payload.path("imageIds")) {
                    media.add(resolveImage(id.asLong(), sessionId, userId));
                }
                messages.add(UserMessage.builder().text(requiredText(payload, "text")).media(media).build());
            } else if ("assistant".equals(row.role())) {
                if (!pendingCalls.isEmpty()) throw new IllegalStateException("工具调用结果尚未完整保存");
                List<AssistantMessage.ToolCall> calls = new ArrayList<>();
                for (JsonNode call : payload.path("toolCalls")) {
                    String id = requiredText(call, "id");
                    if (!pendingCalls.add(id)) throw new IllegalStateException("重复的工具调用 ID: " + id);
                    calls.add(new AssistantMessage.ToolCall(id, requiredText(call, "type"),
                            requiredText(call, "name"), requiredText(call, "arguments")));
                }
                messages.add(AssistantMessage.builder().content(requiredText(payload, "text"))
                        .toolCalls(calls).build());
            } else if ("tool".equals(row.role())) {
                List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
                for (JsonNode response : payload.path("responses")) {
                    String id = requiredText(response, "id");
                    if (!pendingCalls.remove(id)) throw new IllegalStateException("孤立的工具结果: " + id);
                    responses.add(new ToolResponseMessage.ToolResponse(id,
                            requiredText(response, "name"), requiredText(response, "data")));
                }
                messages.add(ToolResponseMessage.builder().responses(responses).build());
            } else {
                throw new IllegalStateException("未知的上下文角色: " + row.role());
            }
        }
        if (!pendingCalls.isEmpty()) throw new IllegalStateException("工具调用结果尚未完整保存: " + pendingCalls);
        return new ContextSnapshot(List.copyOf(messages), raw.headId(), raw.lastMessageId(), raw.coveredThroughId());
    }

    public long lastMessageBeforeRun(String sessionId, String userId, Long runId) {
        return readTransaction.execute(ignored -> {
            requireOwnedSession(sessionId, userId);
            Long id = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM agent_context "
                            + "WHERE session_id=? AND kind='message' AND (run_id<>? OR run_id IS NULL)",
                    Long.class, sessionId, runId);
            return id == null ? 0 : id;
        });
    }

    public void publishCompact(String sessionId, String userId, long expectedHeadId,
                               long coveredThroughId, String entryKey, String summary) {
        if (summary == null || summary.isBlank()) throw new IllegalArgumentException("压缩摘要不能为空");
        writeTransaction.executeWithoutResult(ignored -> {
            lockOwnedSession(sessionId, userId);
            Long head = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM agent_context WHERE session_id=?",
                    Long.class, sessionId);
            if (head == null || head != expectedHeadId) throw new IllegalStateException("上下文已变化，请重新生成摘要");
            List<Long> boundaries = jdbc.queryForList("SELECT covered_through_id FROM agent_context "
                            + "WHERE session_id=? AND kind='compact_boundary' ORDER BY id DESC LIMIT 1",
                    Long.class, sessionId);
            if ((!boundaries.isEmpty() && coveredThroughId <= boundaries.getFirst()) || coveredThroughId > head) {
                throw new IllegalArgumentException("压缩边界无效");
            }
            List<Long> covered = jdbc.queryForList("SELECT id FROM agent_context "
                            + "WHERE session_id=? AND id=? AND kind='message' AND status='complete'",
                    Long.class, sessionId, coveredThroughId);
            if (covered.isEmpty()) throw new IllegalArgumentException("压缩边界必须指向完整消息");
            verifySafeBoundary(sessionId, boundaries.isEmpty() ? 0 : boundaries.getFirst(), coveredThroughId);
            ObjectNode payload = json.createObjectNode();
            payload.put("schemaVersion", 1);
            payload.put("summary", summary);
            jdbc.update("INSERT INTO agent_context (session_id, entry_key, kind, payload, covered_through_id, schema_version) "
                            + "VALUES (?, ?, 'compact_boundary', ?, ?, 1)",
                    sessionId, entryKey, payload.toString(), coveredThroughId);
        });
    }

    /** Delete the transcript in the same transaction as the business session. */
    public void deleteSession(String sessionId, String userId) {
        writeTransaction.executeWithoutResult(ignored -> {
            lockOwnedSession(sessionId, userId);
            jdbc.update("DELETE FROM agent_context WHERE session_id=?", sessionId);
        });
    }

    private void verifySafeBoundary(String sessionId, long previousCoveredId, long newCoveredId) {
        Long incomplete = jdbc.queryForObject("SELECT COUNT(*) FROM agent_context WHERE session_id=? "
                        + "AND kind='message' AND id>? AND id<=? AND status<>'complete'",
                Long.class, sessionId, previousCoveredId, newCoveredId);
        if (incomplete != null && incomplete > 0) throw new IllegalArgumentException("压缩范围含未完成消息");
        List<String> payloads = jdbc.queryForList("SELECT payload FROM agent_context "
                        + "WHERE session_id=? AND kind='message' AND id>? AND id<=? ORDER BY id ASC",
                String.class, sessionId, previousCoveredId, newCoveredId);
        Set<String> pending = new HashSet<>();
        for (String encoded : payloads) {
            JsonNode payload = parse(encoded);
            if (payload.has("toolCalls")) {
                if (!pending.isEmpty()) throw new IllegalArgumentException("压缩边界拆开了工具调用");
                for (JsonNode call : payload.path("toolCalls")) pending.add(requiredText(call, "id"));
            } else if (payload.has("responses")) {
                for (JsonNode response : payload.path("responses")) {
                    if (!pending.remove(requiredText(response, "id"))) {
                        throw new IllegalArgumentException("压缩范围有孤立工具结果");
                    }
                }
            } else if (!pending.isEmpty()) {
                throw new IllegalArgumentException("压缩边界拆开了工具调用");
            }
        }
        if (!pending.isEmpty()) throw new IllegalArgumentException("压缩边界拆开了工具调用");
    }

    private void lockOwnedSession(String sessionId, String userId) {
        List<String> sessions = jdbc.queryForList("SELECT id FROM agent_session WHERE id=? AND user_id=? FOR UPDATE",
                String.class, sessionId, userId);
        if (sessions.isEmpty()) throw new SecurityException("会话不存在或不属于当前用户");
    }

    private void requireOwnedSession(String sessionId, String userId) {
        List<String> sessions = jdbc.queryForList("SELECT id FROM agent_session WHERE id=? AND user_id=?",
                String.class, sessionId, userId);
        if (sessions.isEmpty()) throw new SecurityException("会话不存在或不属于当前用户");
    }

    private Media resolveImage(long imageId, String sessionId, String userId) {
        AiAttachment attachment = attachments.getById(imageId);
        if (attachment == null || !sessionId.equals(attachment.getConservationId())
                || !userId.equals(String.valueOf(attachment.getUserId()))
                || attachment.getFileType() == null || !attachment.getFileType().startsWith("image/")) {
            throw new SecurityException("历史图片不存在或不属于当前会话用户: " + imageId);
        }
        try {
            COSObject object = attachmentReader.getObject(AiConstants.bulk, attachment.getFileUrl());
            try (var stream = object.getObjectContent()) {
                return Media.builder().mimeType(MimeTypeUtils.parseMimeType(attachment.getFileType()))
                        .id(String.valueOf(imageId)).name(attachment.getFileName())
                        .data(StreamUtils.copyToByteArray(stream)).build();
            }
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("无法恢复历史图片: " + imageId, e);
        }
    }

    private JsonNode parse(String payload) {
        try {
            return json.readTree(payload);
        } catch (IOException e) {
            throw new IllegalStateException("上下文 JSON 无法解析", e);
        }
    }

    private static String requiredText(JsonNode payload, String field) {
        JsonNode value = payload.get(field);
        if (value == null || value.isNull()) throw new IllegalStateException("上下文字段缺失: " + field);
        return value.asText();
    }

    public record ContextSnapshot(List<Message> messages, long headId, long lastMessageId, long coveredThroughId) { }

    private record RawSnapshot(List<StoredRow> rows, long headId, long lastMessageId, long coveredThroughId) { }

    private record StoredRow(long id, String role, String payload, String status, long coveredThroughId) { }
}
