package com.ai.aigent.interceptor;

import com.ai.aigent.mapper.UsersMapper;
import com.ai.aigent.service.AnonymousUserService;
import com.ai.aigent.utils.JWTUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
@Component
@Slf4j
public class jwtHandler extends OncePerRequestFilter {
    @Autowired
    private AnonymousUserService anonymousUserService;
    private UsersMapper usersMapper;
    private final AntPathMatcher antPathMatcher = new AntPathMatcher();
    jwtHandler(UsersMapper usersMapper)
    {
        this.usersMapper=usersMapper;
    }
    private boolean isWhiteListPath(String requestUri) {
        for (String pattern : White_LIST) {
            if (antPathMatcher.match(pattern, requestUri)) {
                return true;
            }
        }
        return false;
    }
  private static final Set<String> White_LIST = new HashSet<>(Arrays.asList(
        "/api/user/register","/api/user/login","/api/user/refresh/**","/api/user/password/forgot/update","/api/export/**","/api/ws/asr","/api/ai/ocr/**",
          "/api/ai/read/**","/api/ai/stop/**","/api/ws/tts",
          "/api/ws/notification/**","/api/notifications/broadcast",
          "/api/doc.html","/api/webjars/**","/api/favicon.ico","/api/v3/**"
    ));
   @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestUri = request.getRequestURI();
        // 1. 白名单直接放行
        if (isWhiteListPath(requestUri)) {
            log.debug("白名单路径，跳过认证: {}", requestUri);
            filterChain.doFilter(request, response);
            return;
        }
       if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
           log.debug("options");
           filterChain.doFilter(request, response);
           return;
       }
       if(anonymousUserService.hasAnnoID(request)){
            filterChain.doFilter(request,response);
            return;
        }
        try {

            // 2. 获取 Authorization 头
            final String authorizationHeader = request.getHeader("Authorization");
            if (authorizationHeader == null || authorizationHeader.isEmpty()) {
                log.warn("缺少 Authorization 头"+requestUri);
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("缺少Authorization头");
                return;
            }

            // 3. 去掉 Bearer 前缀
            String token = JWTUtils.extractToken(authorizationHeader);
            if (token == null) {
                log.warn("Authorization 头格式错误: {}", authorizationHeader);
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("非法的Authorization格式");
                return;
            }

            // 4. 解析 token（这里直接用 parseToken，异常交给下面各个 catch）
            Claims claims = JWTUtils.parseToken(token);

            // 5. 验证 Token 类型必须是 access
            String tokenType = claims.get("type", String.class);
            if (!"access".equals(tokenType)) {
                log.warn("使用了非Access Token: {}", tokenType);
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("Token类型错误");
                return;
            }

            // 6. 从 claims 中取出用户信息
            String userId = claims.get("userId", String.class);
            String username = claims.get("username", String.class);

            log.debug("用户认证通过 - userId: {}, username: {}", userId, username);

            request.setAttribute("userId", userId);
            request.setAttribute("username", username);
            request.setAttribute("token", token);
            // 如果需要角色：
            // request.setAttribute("roles", JWTUtils.getRoles(token));

            // 不在过滤器里自动刷新 token，让前端收到 401 后调用 /auth/refresh
            filterChain.doFilter(request, response);

        } catch (ExpiredJwtException e) {
            log.warn("Token已过期: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Token已过期");

        } catch (UnsupportedJwtException e) {
            log.warn("不支持的Token格式: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("不支持的Token格式");

        } catch (MalformedJwtException e) {
            log.warn("Token格式错误: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Token格式错误");

        } catch (SignatureException e) {
            log.warn("Token签名验证失败: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Token签名验证失败");

        } catch (IllegalArgumentException e) {
            log.warn("Token参数错误: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Token参数错误");

        } catch (Exception e) {
            log.error("认证过程中发生错误: {}", e.getMessage(), e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("认证过程发生错误");
        }

    }
}
