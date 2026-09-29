package com.study.chat.module.auth.dto;

import lombok.Data;

/**
 * 登录 / 刷新凭证。
 */
@Data
public class TokenVO {

    private String accessToken;

    private String refreshToken;

    /**
     * access token 剩余秒数
     */
    private Long expiresIn;

    /**
     * refresh token 剩余秒数
     */
    private Long refreshExpiresIn;

    private UserVO user;
}
