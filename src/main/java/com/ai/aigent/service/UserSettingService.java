package com.ai.aigent.service;

import com.ai.aigent.entity.domin.UserSetting;
import com.baomidou.mybatisplus.extension.service.IService;

/**
* @author MECHREVO
* @description 针对表【user_setting】的数据库操作Service
* @createDate 2026-02-24 15:15:57
*/
public interface UserSettingService extends IService<UserSetting> {
    /**
     * 修改用户设置
     */
    void updateUserSetting(UserSetting setting);

    /**
     * 获取用户设置
     */
    UserSetting getByUserId(String userId,String type);
    UserSetting createDefaultSetting(String userId,String type);
}
