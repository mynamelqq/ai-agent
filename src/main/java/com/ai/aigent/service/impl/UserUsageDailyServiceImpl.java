package com.ai.aigent.service.impl;

import com.ai.aigent.constant.ResultCodeEnum;
import com.ai.aigent.entity.domain.AnonymousUser;
import com.ai.aigent.entity.domain.UserUsageDaily;
import com.ai.aigent.entity.domain.Users;
import com.ai.aigent.exception.TokenLimitExceed;
import com.ai.aigent.mapper.UserUsageDailyMapper;
import com.ai.aigent.service.AnonymousUserService;
import com.ai.aigent.service.UserUsageDailyService;
import com.ai.aigent.service.UsersService;
import com.ai.aigent.utils.JWTUtils;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
* @author MECHREVO
* @description 针对表【user_usage_daily】的数据库操作Service实现
* @createDate 2026-02-19 00:31:07
*/
@Service
public class UserUsageDailyServiceImpl extends ServiceImpl<UserUsageDailyMapper, UserUsageDaily>
    implements UserUsageDailyService{
    @Autowired
    public  UserUsageDailyMapper usageMapper;
    @Autowired
    public UsersService usersService;
    @Autowired
    public  AnonymousUserService anonymousUserService;
    @Transactional
    public void checkAndConsume(String userId, String type) {//检查

        // 1️⃣ 获取用户额度
        long dailyLimit = getDailyLimit(userId, type);

        // 2️⃣ 获取今日已用
        long todayUsed =todayTokens(userId);
        // 3️⃣ 判断是否超限
        if (todayUsed  > dailyLimit) {
            throw new TokenLimitExceed(ResultCodeEnum.TOKEN_LIMIT_EXCEEDED.getCode(),"TOKEN_LIMIT_EXCEEDED");
        }
    }

    @Override
    public Long getUsage(HttpServletRequest request) {
        String token=request.getHeader("Authorization");
        if(token!=null&&!token.isEmpty())
        {
            token= JWTUtils.extractToken(token);
            String userId = JWTUtils.getUserId(token);
            Users user = usersService.getById(userId);
            return  user.getTotalTokensUsed();
        }
        else {
            AnonymousUser user=anonymousUserService.getById(request);
            if(user!=null){
                return  user.getTotalTokensUsed();
            }
        }
        throw new IllegalArgumentException("未登录");
    }
    public long getDailyLimit(String userId, String type) {//查询额度
        if ("user".equals(type)) {
            return usersService.getById(userId).getDailyTokenLimit();
        } else {
            return anonymousUserService.getById(userId).getDailyTokenLimit();
        }
    }
    public Long todayTokens(String userId) {
        // selectOne 返回实体对象，需要有一个对应的字段接收
        UserUsageDaily result = baseMapper.selectOne(
            new QueryWrapper<UserUsageDaily>()
                .select("SUM(token) as token") // 假设实体中有 token 字段
                .eq("user_id", userId)
                .ge("time", LocalDate.now().atStartOfDay())
        );
        return result == null || result.getToken() == null ?
               0L : result.getToken().longValue();
    }
    public List<Map<String, Object>> statByHour(String userId, LocalDate date) {
        return usageMapper.selectMaps(
            new QueryWrapper<UserUsageDaily>()
                .select("DATE_FORMAT(time, '%Y-%m-%d %H:00:00') AS hour",
                        "COUNT(*) AS totalCount",      // 次数统计
                        "SUM(token) AS totalTokens")   // token总量（可选）
                .eq("user_id", userId)
                .ge("time", date.atStartOfDay())
                .lt("time", date.plusDays(1).atStartOfDay())
                .groupBy("DATE_FORMAT(time, '%Y-%m-%d %H:00:00')")
                .orderByAsc("hour")
        );
    }
        public List<Map<String, Object>> statByYear(String userId,
                                               LocalDate start,
                                                   LocalDate end) {
            // 使用 ge 和 lt 替代 between，避免函数导致索引失效
            return usageMapper.selectMaps(
                new QueryWrapper<UserUsageDaily>()
                    .select("DATE_FORMAT(time, '%Y') AS year","COUNT(*) AS totalCount","SUM(token) AS totalTokens")
                    .eq("user_id", userId)
                    .ge("time", start.atStartOfDay())
                    .lt("time", end.plusDays(1).atStartOfDay())
                    .groupBy("DATE_FORMAT(time, '%Y')")  // 不依赖别名
                    .orderByAsc("DATE_FORMAT(time, '%Y')")
            );
        }

    public List<Map<String, Object>> statByMonth(String userId,
                                                LocalDate start,
                                                LocalDate end) {
            return usageMapper.selectMaps(
                new QueryWrapper<UserUsageDaily>()
                    .select("DATE_FORMAT(time, '%Y-%m') AS month","COUNT(*) AS totalCount",
                            "SUM(token) AS totalTokens")
                    .eq("user_id", userId)
                    .ge("time", start.atStartOfDay())
                    .lt("time", end.plusDays(1).atStartOfDay())
                    .groupBy("DATE_FORMAT(time, '%Y-%m')")  // 不依赖别名
                    .orderByAsc("DATE_FORMAT(time, '%Y-%m')")
            );
        }
    public List<Map<String,Object>> statByModel(String userId){
        return usageMapper.selectMaps(
                new QueryWrapper<UserUsageDaily>()
                        .select("chat_model AS model","SUM(token) AS totalTokens")
                        .eq("user_id",userId)
                        .groupBy("chat_model")
                        .orderByDesc("totalTokens")
        );
    }
    public Map<String,Object> statPromptCompletion(String userId){

        Map<String,Object> map =
                usageMapper.selectMaps(
                        new QueryWrapper<UserUsageDaily>()
                                .select(
                                        "SUM(prompt_tokens) AS promptTokens",
                                        "SUM(completion_tokens) AS completionTokens"
                                )
                                .eq("user_id",userId)
                ).get(0);

        return map;
    }
    public void addUsage(String userId,
                         String type,
                         long tokens,
                         int promptTokens,
                         int completionTokens,
                         int cachedTokens,String from,String model) {

        UserUsageDaily usage = new UserUsageDaily();
        usage.setUserId(userId);
        usage.setFromWhere(from);
        usage.setType(type);
        usage.setToken(tokens);
        usage.setChatModel(model);
        usage.setPromptTokens(promptTokens);
        usage.setCompletionTokens(completionTokens);
        usage.setCachedTokens(cachedTokens);
        usage.setTime(LocalDateTime.now());
        usageMapper.insert(usage);
    }

    public void addTotalUsage(String userId, String type, long tokens) {//增加总用量
        if ("user".equals(type)) {
            Users u = usersService.getById(userId);
            u.setTotalTokensUsed(u.getTotalTokensUsed() + tokens);
            usersService.updateById(u);
        } else {
            AnonymousUser a = anonymousUserService.getById(userId);
            a.setTotalTokensUsed(a.getTotalTokensUsed() + tokens);
            anonymousUserService.updateById(a);
        }
    }
}




