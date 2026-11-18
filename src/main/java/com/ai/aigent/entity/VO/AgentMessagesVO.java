package com.ai.aigent.entity.VO;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class AgentMessagesVO {
    private List<AgentMessageVO> messages;
    /** runId -> attachments; IDs are strings to preserve JavaScript precision. */
    private Map<String, List<AgentAttachmentVO>> attachmentsByRunId;
}
