package com.ai.aigent.controller;

import com.ai.aigent.entity.domin.AnonymousUser;
import com.ai.aigent.service.AnonymousUserService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
    public AnonymousUser identify(HttpServletRequest req, HttpServletResponse resp,
                                  @RequestHeader(value = "Fingerprint-Hash", required = false) String fingerprintHash) {
        // 1. 从 cookie 读取
        String anonId = null;
        if (req.getCookies() != null) {
            for (Cookie c : req.getCookies()) {
                if ("anon_id".equals(c.getName())) {
                    anonId = c.getValue();
                    break;
                }
            }
        }
        String ip = req.getRemoteAddr();
        // 3. 获取或创建匿名用户
        AnonymousUser user = service.getOrCreate(anonId, fingerprintHash, ip);
        // 4. 写回 cookie（最长 10 年）
        Cookie cookie = new Cookie("anon_id", user.getAnonId());
        cookie.setMaxAge(365 * 24 * 3600);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        resp.addCookie(cookie);

        return user;
    }
}