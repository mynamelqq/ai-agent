package com.ai.aigent.interceptor;

import com.ai.aigent.mapper.UsersMapper;
import com.ai.aigent.utils.JWTUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
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

    private UsersMapper usersMapper;
    private final AntPathMatcher antPathMatcher = new AntPathMatcher();
    jwtHandler(UsersMapper usersMapper)
    {
        this.usersMapper=usersMapper;
    }
    private boolean isWhiteListPath(String requestUri) {
        for (String pattern : BLACK_LIST) {
            if (antPathMatcher.match(pattern, requestUri)) {
                return true;
            }
        }
        return false;
    }
  private static final Set<String> BLACK_LIST = new HashSet<>(Arrays.asList(
        "/api/user/logout",
          "/chat/rename",
          "/user/update",
          "/sign/avatar",
          "/delete"
    ));
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestUri = request.getRequestURI();
        if (!isWhiteListPath(requestUri)) {
            log.debug("白名单路径，跳过认证: {}", requestUri);
            filterChain.doFilter(request, response);
            return;
        }

        try {
            final String authorizationHeader = request.getHeader("Authorization");
            String token = authorizationHeader;
            if (!JWTUtils.validateToken(authorizationHeader)) {
                log.warn("Token验证失败");
                return;
            }
        Claims claims = JWTUtils.parseToken(authorizationHeader);
        String username =JWTUtils.getUsername( token);
        String userId=JWTUtils.getUserId(token);

        String tokenType = JWTUtils.getTokenType(token);
       // 4. 验证Token类型（防止使用刷新Token作为访问Token）
        if (!"access".equals(tokenType)) {
            log.warn("使用了非Access Token: {}", tokenType);
            return;
        }
       log.debug("用户认证通过 - userId: {}, username: {}",
             userId, username);
        request.setAttribute("userId", userId);
        request.setAttribute("username", username);
//        request.setAttribute("roles", roles);
        request.setAttribute("token", token);
        JWTUtils.refreshToken(token);
        filterChain.doFilter(request, response);

        }catch (ExpiredJwtException e) {
            log.warn("Token已过期: {}", e.getMessage());

        } catch (UnsupportedJwtException e) {
            log.warn("不支持的Token格式: {}", e.getMessage());

        } catch (MalformedJwtException e) {
            log.warn("Token格式错误: {}", e.getMessage());

        } catch (IllegalArgumentException e) {
            log.warn("Token参数错误: {}", e.getMessage());
        } catch (Exception e) {
            log.error("认证过程中发生错误: {}", e.getMessage(), e);
        }

    }
}