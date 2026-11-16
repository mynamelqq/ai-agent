package com.ai.aigent.service;

import com.ai.aigent.entity.domain.AnonymousUser;
import com.baomidou.mybatisplus.extension.service.IService;
import jakarta.servlet.http.HttpServletRequest;

/**
* @author MECHREVO
* @description 针对表【anonymous_user(匿名用户表)】的数据库操作Service
* @createDate 2026-02-19 00:38:05
*/
public interface AnonymousUserService extends IService<AnonymousUser> {
     public AnonymousUser getOrCreate(String anonId, String fingerprintHash, String ip);
     AnonymousUser getById(HttpServletRequest req);
      Boolean hasAnnoID(HttpServletRequest req);
}
