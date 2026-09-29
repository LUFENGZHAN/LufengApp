package com.study.chat.common.auth;

import java.time.Instant;

/**
 * access_token 载荷。
 *
 * @param uid          用户 ID
 * @param userNo       用户编号
 * @param deviceId     设备唯一 ID
 * @param tokenVersion 令牌版本，与 user_account.token_version 比对，用于全端下线
 * @param jti          JWT ID，登出后加入黑名单
 * @param expiresAt    过期时间
 */
public record JwtClaims(long uid,
                        String userNo,
                        String deviceId,
                        int tokenVersion,
                        String jti,
                        Instant expiresAt) {
}
