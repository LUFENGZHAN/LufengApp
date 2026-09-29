package com.study.chat.module.friend.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 好友列表行：好友关系 + 对方资料。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class FriendRow extends Friend {

    private String friendNo;

    private String nickname;

    private String avatar;

    private String signature;
}
