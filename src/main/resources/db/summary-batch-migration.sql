-- 在启动新版摘要消费者前执行；项目现有 chat_message 已有 id、payload、is_deleted 等列。
ALTER TABLE chat_message
    ADD COLUMN archive_status TINYINT NOT NULL DEFAULT 0,
    ADD COLUMN summary_batch_id VARCHAR(64) NULL,
    ADD INDEX idx_chat_archive (conversation_id, archive_status, id),
    ADD INDEX idx_summary_batch (summary_batch_id);

-- 批次表已存在。lease_token 用于阻止租约过期的旧 Worker 提交；published_at 用于待发布扫描。
ALTER TABLE chat_summary_batch
    ADD COLUMN lease_token VARCHAR(64) NULL,
    ADD COLUMN lease_until DATETIME NULL,
    ADD COLUMN published_at DATETIME NULL,
    ADD INDEX idx_summary_outbox (status, published_at, created_at);

-- 旧数据中 is_deleted=1 的消息按已有归档状态初始化。
UPDATE chat_message SET archive_status = 2 WHERE is_deleted = 1;
