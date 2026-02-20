package com.ai.aigent.service.impl;

import com.ai.aigent.entity.domin.ChatSummary;
import com.ai.aigent.mapper.ChatSummaryMapper;
import com.ai.aigent.service.ChatSummaryService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
* @author MECHREVO
* @description 针对表【chat_summary】的数据库操作Service实现
* @createDate 2025-12-22 14:35:24
*/
@Service
public class ChatSummaryServiceImpl extends ServiceImpl<ChatSummaryMapper, ChatSummary>
    implements ChatSummaryService{
    @Override
    public String getSummary(String conversationId) {
        ChatSummary entity = this.getOne(
                Wrappers.<ChatSummary>lambdaQuery()
                        .eq(ChatSummary::getConversationId, conversationId)
        );
        return entity == null ? null : entity.getSummary();
    }

    @Override
    public void updateSummary(String conversationId, String newSummary) {
         ChatSummary entity = this.getOne(
                Wrappers.<ChatSummary>lambdaQuery()
                        .eq(ChatSummary::getConversationId, conversationId)
        );
        if (entity == null) {
            entity = new ChatSummary();
            entity.setConversationId(conversationId);
            entity.setSummary(newSummary);
            this.save(entity);
        } else {
            entity.setSummary(entity.getSummary()+newSummary);
            entity.setUpdateTime(LocalDateTime.now());
            this.updateById(entity);
        }
    }
}




