package com.study.chat.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterReq {

    @NotBlank(message = "账号不能为空")
    @Pattern(regexp = "^[A-Za-z0-9_.@-]{4,64}$", message = "账号格式不正确")
    private String account;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度需为 6-32 位")
    private String password;

    @Size(max = 32, message = "昵称不能超过 32 个字符")
    private String nickname;

    /**
     * 设备唯一 ID，注册后可直接登录
     */
    private String deviceId;

    private String deviceType;
}
