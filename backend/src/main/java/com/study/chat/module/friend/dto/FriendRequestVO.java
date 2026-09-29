package com.study.chat.module.friend.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FriendRequestVO {

    private Long requestId;

    private String requestNo;

    /**
     * 对方用户：收到的申请为申请人，发出的申请为被申请人
     */
    private Long userId;

    private String userNo;

    private String nickname;

    private String avatar;

    private String applyMsg;

    /**
     * 0待处理 1已同意 2已拒绝 3已忽略
     */
    private Integer status;

    private LocalDateTime createdAt;
}
