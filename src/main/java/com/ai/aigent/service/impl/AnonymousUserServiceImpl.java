package com.ai.aigent.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.ai.aigent.entity.domain.AnonymousUser;
import com.ai.aigent.mapper.AnonymousUserMapper;
import com.ai.aigent.service.AnonymousUserService;
import com.ai.aigent.service.UserSettingService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
* @author MECHREVO
* @description 针对表【anonymous_user(匿名用户表)】的数据库操作Service实现
* @createDate 2026-02-19 00:38:05
*/
@Service
public class AnonymousUserServiceImpl extends ServiceImpl<AnonymousUserMapper, AnonymousUser>
    implements AnonymousUserService{
    @Autowired
    private UserSettingService userSettingService;
    @Autowired
    private AnonymousUserMapper repository;
    @Override
    @Transactional
    public AnonymousUser getOrCreate(String anonId, String fingerprintHash, String ip) {

        if (anonId != null) {
            Optional<AnonymousUser> existing = getOptById(anonId);
            if (existing.isPresent()) {
                AnonymousUser user = existing.get();
                user.setLastActiveAt(new Date());
                user.setIp(ip);
                if (fingerprintHash != null) {
                    user.setFingerprintHash(fingerprintHash);
                }
                this.save( user);
                return user;
            }
        }
        // 创建新匿名用户
        String newAnonId = UUID.randomUUID().toString();
        AnonymousUser user = new AnonymousUser();
        user.setAnonId(newAnonId);
        if(fingerprintHash==null||fingerprintHash.isEmpty()){
            fingerprintHash = RandomUtil.randomString(10);
        }
        user.setFingerprintHash(fingerprintHash);
        user.setIp(ip);
        user.setTotalTokensUsed(0L);
        user.setCreatedAt(new Date());
        user.setDailyTokenLimit(20000);
        user.setLastActiveAt(new Date());
        repository.insert(user);
        userSettingService.createDefaultSetting(newAnonId,"anno");
        return user;
    }
    public Boolean hasAnnoID(HttpServletRequest req)
    {
       String anonId = req.getHeader("Anno-Id");
        // 2. 如果 Header 里没有，再从 query/body param 里取
        if (anonId == null || anonId.isEmpty()) {
            anonId = req.getParameter("annoId");
        }
        if (anonId == null || anonId.isEmpty()) {
            return false;
        }
        return true;
    }

    public AnonymousUser getById(HttpServletRequest req) {
        String anonId = req.getHeader("Anno-Id");
        // 2. 如果 Header 里没有，再从 query/body param 里取
        if (anonId == null || anonId.isEmpty()) {
            anonId = req.getParameter("annoId");
        }
        if (anonId == null || anonId.isEmpty()) {
            throw new IllegalArgumentException("参数 annoId 不能为空");
        }
        AnonymousUser user=repository.selectById(anonId);
        if(user==null){
            user = new AnonymousUser();
            String ip = req.getRemoteAddr();
            user.setAnonId(anonId);
            user.setIp(ip);
            user.setTotalTokensUsed(0L);
            user.setCreatedAt(new Date());
            user.setFingerprintHash("req");
            user.setDailyTokenLimit(100000);
            user.setLastActiveAt(new Date());
            repository.insert(user);
            userSettingService.createDefaultSetting(anonId,"anno");
        }
        return user;
    }
}




