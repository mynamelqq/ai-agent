package com.ai.aigent.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * JWT 工具类
 * 支持 HS256/HS384/HS512 算法
 */
@Component
public class JWTUtils {
// 不要在这里直接初始化静态密钥，使用 @PostConstruct
    private static SecretKey SECRET_KEY;
    private static final long DEFAULT_EXPIRATION = 24 * 60 * 60 * 1000L; // 24小时
    private static final String ISSUER = "ai-agent-system";
    public static final String TOKEN_PREFIX = "Bearer ";

    private String secretString="-256-bit-secret-key-must-be-at-lea";

    /**
     * 在 Spring 容器初始化后执行
     */
    @PostConstruct
    public void init() {
        try {
            // 确保密钥长度足够
            if (secretString.length() < 32) {
                // 如果密钥太短，自动补全
                secretString = String.format("%-32s", secretString).substring(0, 32);
            }

            byte[] keyBytes = secretString.getBytes(StandardCharsets.UTF_8);
            SECRET_KEY = Keys.hmacShaKeyFor(keyBytes);
        } catch (Exception e) {
            throw new RuntimeException("JWT密钥初始化失败", e);
        }
    }
    /**
     * 生成JWT（使用默认签发人和过期时间）
     */
    public static String createToken(Map<String, Object> claims) {
        return createToken(ISSUER, DEFAULT_EXPIRATION, claims);
    }

    /**
     * 生成JWT（指定签发人）
     */
    public static String createToken(String issuer, Map<String, Object> claims) {
        return createToken(issuer, DEFAULT_EXPIRATION, claims);
    }

    /**
     * 生成JWT（完整参数）
     */
    public static String createToken(String issuer, long ttlMillis, Map<String, Object> claims) {
        long nowMillis = System.currentTimeMillis();
        Date now = new Date(nowMillis);
        Date expiration = ttlMillis > 0 ? new Date(nowMillis + ttlMillis) : null;

        JwtBuilder builder = Jwts.builder()
                .setId(UUID.randomUUID().toString())
                .setIssuer(issuer)
                .setIssuedAt(now)
                .addClaims(claims)
                .signWith(SECRET_KEY, SignatureAlgorithm.HS256);

        if (expiration != null) {
            builder.setExpiration(expiration);
        }

        return builder.compact();
    }

    /**
     * 生成用户登录Token
     */
    public static String createUserToken(String userId, String username) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        claims.put("type", "access");
        return createToken(claims);
    }

    /**
     * 生成刷新Token
     */
    public static String createRefreshToken(String userId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("type", "refresh");

        // 刷新token有效期更长（7天）
        return createToken(ISSUER, 7 * 24 * 60 * 60 * 1000L, claims);
    }
    /**
     * 解析JWT
     */
    public static Claims parseToken(String token) {
            return Jwts.parser()
                    .setSigningKey(SECRET_KEY)
                    .parseClaimsJws(token)
                    .getBody();
    }
    /**
     * 验证JWT是否有效
     */
    public static boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    /**
     * 从Token中获取指定claim
     */
    public static <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver) {
        Claims claims = parseToken(token);
        return claimsResolver.apply(claims);
    }
    /**
     * 获取用户ID
     */
    public static String getUserId(String token) {
        return getClaimFromToken(token, claims -> claims.get("userId", String.class));
    }
    /**
     * 获取用户名
     */
    public static String getUsername(String token) {
        return getClaimFromToken(token, claims -> claims.get("username", String.class));
    }
    /**
     * 获取用户角色
     */
    public static String[] getRoles(String token) {
        return getClaimFromToken(token, claims -> {
            Object roles = claims.get("roles");
            if (roles instanceof String[]) {
                return (String[]) roles;
            }
            return new String[0];
        });
    }
    /**
     * 获取Token类型
     */
    public static String getTokenType(String token) {
        return getClaimFromToken(token, claims -> claims.get("type", String.class));
    }
    /**
     * 获取签发时间
     */
    public static Date getIssuedAt(String token) {
        return getClaimFromToken(token, Claims::getIssuedAt);
    }
    /**
     * 获取过期时间
     */
    public static Date getExpiration(String token) {
        return getClaimFromToken(token, Claims::getExpiration);
    }
    /**
     * 检查Token是否即将过期（在指定时间内）
     */
    public static boolean isTokenExpiringSoon(String token, long thresholdMillis) {
        Date expiration = getExpiration(token);
        long timeLeft = expiration.getTime() - System.currentTimeMillis();
        return timeLeft <= thresholdMillis && timeLeft > 0;
    }
    /**
     * 检查Token是否已过期
     */
    public static boolean isTokenExpired(String token) {
        Date expiration = getExpiration(token);
        return expiration.before(new Date());
    }
    /**
     * 从Authorization头中提取Token
     */
    public static String extractToken(String authorizationHeader) {
        if (authorizationHeader != null && authorizationHeader.startsWith(TOKEN_PREFIX)) {
            return authorizationHeader.substring(TOKEN_PREFIX.length());
        }
        return null;
    }

    /**
     * 刷新Token（创建新的Token，保留原有claims）
     */
    public static String refreshToken(String refreshToken) {
        Claims claims = parseToken(refreshToken);

        String userId = claims.get("userId", String.class);

        return createUserToken(userId, null);
    }
    /**
     * 验证Token类型
     */
    public static boolean validateTokenType(String token, String expectedType) {
        try {
            String tokenType = getTokenType(token);
            return expectedType.equals(tokenType);
        } catch (Exception e) {
            return false;
        }
    }
}