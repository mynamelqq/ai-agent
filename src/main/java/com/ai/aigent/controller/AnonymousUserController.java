package com.ai.aigent.controller;

import com.ai.aigent.entity.domin.AnonymousUser;
import com.ai.aigent.service.AnonymousUserService;
import com.ai.aigent.service.UserSettingService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/anon")
public class AnonymousUserController {

    private final AnonymousUserService service;

    public AnonymousUserController(AnonymousUserService service) {
        this.service = service;
    }

    @GetMapping("/identify")
    public AnonymousUser identify(HttpServletRequest req,
                                  @RequestHeader(value = "Fingerprint-Hash", required = false) String fingerprintHash) {
        // 1. 先从 Header 取
        String anonId = req.getHeader("Anno-Id");

        // 2. 如果 Header 里没有，再从 query/body param 里取
        if (anonId == null || anonId.isEmpty()) {
            anonId = req.getParameter("annoId");
        }

        if (anonId == null || anonId.isEmpty()) {
            throw new IllegalArgumentException("参数 annoId 不能为空");
        }
        String ip = req.getRemoteAddr();

        // 3. 获取或创建匿名用户
        AnonymousUser user = service.getOrCreate(anonId, fingerprintHash, ip);
        return user;
    }
}