package com.study.chat.module.friend.dto;

import lombok.Data;

@Data
public class FriendVO {

    private Long userId;

    private String userNo;

    private String nickname;

    private String avatar;

    private String signature;

    private String remark;

    private Boolean online;
}
