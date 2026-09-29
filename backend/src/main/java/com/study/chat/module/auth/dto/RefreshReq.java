package com.study.chat.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RefreshReq {

    @NotBlank(message = "refreshToken 不能为空")
    private String refreshToken;

    @NotBlank(message = "设备ID不能为空")
    private String deviceId;
}
