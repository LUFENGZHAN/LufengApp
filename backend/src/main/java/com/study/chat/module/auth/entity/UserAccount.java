package com.study.chat.module.auth.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户账户：仅承载登录凭证，资料字段放在 user_profile。
 */
@Data
public class UserAccount {

    private Long id;

    private String userNo;

    private String account;

    private String phone;

    private String email;

    /**
     * BCrypt 摘要，永不明文落库
     */
    private String passwordHash;

    private String passwordAlgo;

    /**
     * 0禁用 1正常 2锁定
     */
    private Integer status;

    /**
     * 令牌版本：递增后该用户所有已签发 JWT 立即失效
     */
    private Integer tokenVersion;

    private LocalDateTime lastLoginAt;

    private String lastLoginIp;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;
}
