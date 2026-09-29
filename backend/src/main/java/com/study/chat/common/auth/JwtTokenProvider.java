package com.study.chat.common.auth;

import com.study.chat.common.result.BizException;
import com.study.chat.common.result.ResultCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 签发与校验。
 * <p>
 * access_token 为 JWT（无状态，携带 uid/deviceId/tokenVersion）；
 * refresh_token 为随机串，服务端只保存其 SHA-256 摘要（见 user_device_session）。
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private static final String CLAIM_USER_NO = "userNo";
    private static final String CLAIM_DEVICE_ID = "dev";
    private static final String CLAIM_TOKEN_VERSION = "tv";
    private static final String CLAIM_TYPE = "typ";
    private static final String TYPE_ACCESS = "access";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKey key;
    private final JwtProperties properties;

    public JwtTokenProvider(JwtProperties properties) {
        byte[] secretBytes = properties.getSecret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException("lufeng.jwt.secret 长度不足 32 字节，无法用于 HS256");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.properties = properties;
    }

    public String createAccessToken(long uid, String userNo, String deviceId, int tokenVersion) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.getAccessTtl());
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(uid))
                .claim(CLAIM_USER_NO, userNo)
                .claim(CLAIM_DEVICE_ID, deviceId)
                .claim(CLAIM_TOKEN_VERSION, tokenVersion)
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
    }

    /**
     * 校验并解析 access_token，任何不合法情况都抛出 {@link BizException}。
     */
    public JwtClaims parse(String token) {
        if (token == null || token.isBlank()) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            if (!TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class))) {
                throw new BizException(ResultCode.UNAUTHORIZED);
            }
            long uid = Long.parseLong(claims.getSubject());
            return new JwtClaims(
                    uid,
                    claims.get(CLAIM_USER_NO, String.class),
                    claims.get(CLAIM_DEVICE_ID, String.class),
                    claims.get(CLAIM_TOKEN_VERSION, Integer.class),
                    claims.getId(),
                    claims.getExpiration().toInstant()
            );
        } catch (ExpiredJwtException e) {
            throw new BizException(ResultCode.TOKEN_EXPIRED);
        } catch (JwtException | NumberFormatException e) {
            log.debug("JWT 解析失败：{}", e.getMessage());
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
    }

    /**
     * 生成 refresh_token：32 字节随机串，服务端只存摘要。
     */
    public String createRefreshToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public long accessTtlSeconds() {
        return properties.getAccessTtl().getSeconds();
    }

    public Instant refreshExpiresAt() {
        return Instant.now().plus(properties.getRefreshTtl());
    }
}
