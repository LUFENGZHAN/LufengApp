package com.study.chat.module.friend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ApplyFriendReq {

    @NotNull(message = "对方用户ID不能为空")
    private Long toUserId;

    @Size(max = 100, message = "申请留言过长")
    private String applyMsg;
}
