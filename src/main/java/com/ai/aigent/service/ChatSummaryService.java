package com.ai.aigent.service;

import com.ai.aigent.entity.domain.ChatSummary;
import com.baomidou.mybatisplus.extension.service.IService;

/**
* @author MECHREVO
* @description 针对表【chat_summary】的数据库操作Service
* @createDate 2025-12-22 14:35:24
*/
public interface ChatSummaryService extends IService<ChatSummary> {
   String getSummary(String conversationId);

    void updateSummary(String conversationId, String newSummary);
}
