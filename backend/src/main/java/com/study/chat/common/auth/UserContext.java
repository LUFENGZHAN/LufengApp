package com.study.chat.common.auth;

/**
 * 当前登录用户上下文，存放在 {@link AuthContext} 的线程局部变量中。
 */
public record UserContext(long userId,
                          String userNo,
                          String deviceId,
                          String clientType) {

    public static UserContext of(JwtClaims claims, String clientType) {
        return new UserContext(claims.uid(), claims.userNo(), claims.deviceId(), clientType);
    }
}
