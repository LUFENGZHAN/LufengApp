package com.study.chat.module.user.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateUserReq {

    @Size(max = 32, message = "昵称不能超过 32 个字符")
    private String nickname;

    @Size(max = 512, message = "头像地址过长")
    private String avatar;

    private Integer gender;

    @Size(max = 255, message = "个性签名过长")
    private String signature;
}
