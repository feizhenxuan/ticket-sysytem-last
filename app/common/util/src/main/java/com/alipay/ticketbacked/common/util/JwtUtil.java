package com.alipay.ticketbacked.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * JWT 工具 — 对应 Python core/security.py
 */
@Component
public class JwtUtil {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expire-hours:24}")
    private long expireHours;

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** 签发 JWT */
    public String createToken(Map<String, Object> claims) {
        long now = System.currentTimeMillis();
        long exp = now + expireHours * 3600_000L;
        return Jwts.builder()
                .claims(claims)
                .issuedAt(new Date(now))
                .expiration(new Date(exp))
                .signWith(getKey())
                .compact();
    }

    /** 解析并校验 JWT，返回 claims；失败返回 null */
    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            return null;
        }
    }

    /** 从 token 中提取 user_id */
    public Long extractUserId(String token) {
        Claims claims = parseToken(token);
        if (claims == null) return null;
        Object uid = claims.get("user_id");
        if (uid instanceof Number) return ((Number) uid).longValue();
        if (uid instanceof String) {
            try { return Long.parseLong((String) uid); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }
}