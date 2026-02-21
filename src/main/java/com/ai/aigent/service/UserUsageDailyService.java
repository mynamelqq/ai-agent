package com.ai.aigent.service;

import com.ai.aigent.entity.domin.UserUsageDaily;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
* @author MECHREVO
* @description 针对表【user_usage_daily】的数据库操作Service
* @createDate 2026-02-19 00:31:07
*/
public interface UserUsageDailyService extends IService<UserUsageDaily> {
    void checkAndConsume(String userId, String type);
    long getDailyLimit(String userId, String type);

    void addUsage(String userId, String type, long tokens,int promptTokens,int  completionTokens,int cachedTokens) ;

    void addTotalUsage(String userId, String type, long tokens);
    List<Map<String, Object>> statByHour(String userId, LocalDate date);
    List<Map<String, Object>> statByYear(String userId,
                                            LocalDate start,
                                            LocalDate end);
    List<Map<String, Object>> statByMonth(String userId,
                                                 LocalDate start,
                                                 LocalDate end);

     Long todayTokens(String userId);
}
