package com.study.chat.app.user.dto;


import jakarta.validation.constraints.NotBlank;
import lombok.Data;


@Data
public class LoginDTO {


    /**
     * 登录账号
     */
    @NotBlank(message = "账号不能为空")
    private String account;


    /**
     * 登录密码
     */
    @NotBlank(message = "密码不能为空")
    private String password;


    /**
     * 登录设备类型
     * pc/app
     */
    private String deviceType;


    /**
     * 设备id
     */
    @NotBlank(message = "设备ID不能为空")
    private String deviceId;

}