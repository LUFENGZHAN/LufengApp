package com.study.chat.app.user.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;


@Data
public class RegisterDTO {


    /**
     * 注册账号
     */
    @NotBlank(message = "账号不能为空")
    private String account;


    /**
     * 密码
     */
    @NotBlank(message = "密码不能为空")
    private String password;

}