package com.study.chat.module.auth.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备会话：支撑多端在线、refresh token 轮换与单端登出。
 */
@Data
public class UserDeviceSession {

    private Long id;

    private Long userId;

    private String deviceId;

    /**
     * web / android / ios / desktop
     */
    private String deviceType;

    private String deviceName;

    /**
     * sha256(refreshToken)，服务端不保存明文
     */
    private String refreshTokenHash;

    private LocalDateTime refreshExpiresAt;

    /**
     * 当前 access token 的 jti，登出时拉黑
     */
    private String accessJti;

    private String clientIp;

    /**
     * 1有效 0失效
     */
    private Integer status;

    private LocalDateTime loginAt;

    private LocalDateTime lastActiveAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
