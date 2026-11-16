package com.ai.aigent.consumer;

import lombok.Data;

import java.util.List;

/** 旧版消息结构；新链路只发送 SummaryChatEvent(batchId, conversationId)。 */
@Data
public class  SummaryTask {
    private String conversationId;
    private List<Long> messageIds;
}
