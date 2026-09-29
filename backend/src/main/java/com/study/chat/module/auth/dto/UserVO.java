package com.study.chat.module.auth.dto;

import lombok.Data;

/**
 * 用户视图：Web 与 App 共用。
 */
@Data
public class UserVO {

    private Long userId;

    private String userNo;

    private String account;

    private String nickname;

    private String avatar;

    private Integer gender;

    private String signature;

    /**
     * 是否在线（会话列表、好友列表展示小绿点）
     */
    private Boolean online;
}
