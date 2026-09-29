package com.study.chat.module.friend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class HandleFriendReq {

    /**
     * accept / reject
     */
    @NotBlank(message = "action 不能为空")
    private String action;
}
