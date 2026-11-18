-- 手动迁移：仅在 agent_message 尚未添加 run_id 时执行第一条语句。
-- 旧消息保留 NULL，无法仅凭现有消息可靠还原完整轮次。
ALTER TABLE agent_message ADD COLUMN run_id BIGINT NULL COMMENT '前端生成的对话轮次 ID';

-- 同一轮有多条消息，因此该索引不能设为唯一索引。
CREATE INDEX idx_agent_message_session_run ON agent_message (session_id, run_id);

-- Agent 附件沿用 ai_attachment.chat_id 保存 runId，conservation_id 保存 sessionId。
