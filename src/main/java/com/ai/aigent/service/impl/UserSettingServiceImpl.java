package com.ai.aigent.service.impl;

import com.ai.aigent.constant.AiCientConstans;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ai.aigent.entity.domin.UserSetting;
import com.ai.aigent.service.UserSettingService;
import com.ai.aigent.mapper.UserSettingMapper;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

/**
* @author MECHREVO
* @description 针对表【user_setting】的数据库操作Service实现
* @createDate 2026-02-24 15:15:57
*/
@Service
public class UserSettingServiceImpl extends ServiceImpl<UserSettingMapper, UserSetting>
    implements UserSettingService{
    @Override
    public void updateUserSetting(UserSetting setting) {
        if (setting == null || setting.getUserId() == null) {
            throw new IllegalArgumentException("userId不能为空");
        }
        UserSetting dbSetting = getById(setting.getUserId());
        // 若不存在 → 新建
        if (dbSetting == null) {
            save(setting);
            return;
        }
        // 只更新非空字段（推荐）
        UserSetting update = new UserSetting();
        update.setUserId(setting.getUserId());
        if (setting.getChatModel() != null) update.setChatModel(setting.getChatModel());
        if(setting.getColorPoint()!=null)update.setColorPoint(setting.getColorPoint());
        if (setting.getContextWindow() != null) update.setContextWindow(setting.getContextWindow());
        if (setting.getReasoningEffort() != null) update.setReasoningEffort(setting.getReasoningEffort());
        if(setting.getFrequencyPenalty()!=null)update.setFrequencyPenalty(setting.getFrequencyPenalty());
        if (setting.getMaxTokens() != null) update.setMaxTokens(setting.getMaxTokens());
        if (setting.getUseMcp() != null) update.setUseMcp(setting.getUseMcp());
        if (setting.getTopk() != null) update.setTopk(setting.getTopk());
        if(setting.getChatMemory()!=null)update.setChatMemory(setting.getChatMemory());
        if (setting.getTemperature() != null) update.setTemperature(setting.getTemperature());
        if (setting.getToolMode() != null) update.setToolMode(setting.getToolMode());
        if (setting.getFoldingThinking() != null) update.setFoldingThinking(setting.getFoldingThinking());
        if (setting.getIsSummary() != null) update.setIsSummary(setting.getIsSummary());
        if (setting.getTone() != null) update.setTone(setting.getTone());
        if (setting.getIsExtend() != null) update.setIsExtend(setting.getIsExtend());
        updateById(update);
    }

    @Override
    public UserSetting getByUserId(String userId) {
        return createDefaultSetting(userId);
    }

    @Override
    public UserSetting createDefaultSetting(String userId) {
       UserSetting userSetting = new UserSetting();
        UserSetting byId = getById(userId);
        if(byId!=null){
            return byId;
        }
        userSetting.setUserId(userId);
        userSetting.setTone("concise");
        userSetting.setReasoningEffort("low");
        userSetting.setChatModel("gpt-4o-mini");
        userSetting.setTemperature(0.7);
        userSetting.setTopk(50);
        userSetting.setColorPoint("blue");
        userSetting.setChatMemory(true);
        userSetting.setMaxTokens(10000);//1万tokens限制
        userSetting.setFrequencyPenalty(0.0);
        userSetting.setToolMode("auto");
        userSetting.setUseMcp(false);
        userSetting.setFoldingThinking(true);
        userSetting.setContextWindow(AiCientConstans.MESSAGE_PAGE_SIZE);
        userSetting.setIsSummary(false);
        userSetting.setIsExtend(false);
        save(userSetting);
        return userSetting;
    }
}




