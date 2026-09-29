package com.study.chat.module.friend.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 好友申请行：附带申请人资料。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FriendRequestRow extends FriendRequest {

    private String userNo;

    private String nickname;

    private String avatar;
}
