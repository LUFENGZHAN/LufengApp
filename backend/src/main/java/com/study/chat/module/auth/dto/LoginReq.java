package com.study.chat.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginReq {

    @NotBlank(message = "账号不能为空")
    private String account;

    @NotBlank(message = "密码不能为空")
    private String password;

    @NotBlank(message = "设备ID不能为空")
    private String deviceId;

    /**
     * web / android / ios / desktop
     */
    private String deviceType;
}
