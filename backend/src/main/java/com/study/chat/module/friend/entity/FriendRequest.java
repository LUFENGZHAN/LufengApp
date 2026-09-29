package com.study.chat.module.friend.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 好友申请：0待处理 1已同意 2已拒绝 3已忽略
 */
@Data
public class FriendRequest {

    private Long id;

    private String requestNo;

    private Long fromUserId;

    private Long toUserId;

    private String applyMsg;

    private Integer status;

    private LocalDateTime handledAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
