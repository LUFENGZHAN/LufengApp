package com.study.chat.module.friend.event;

/**
 * 好友相关通知：新申请、申请被同意/拒绝。由实时模块推送给目标用户。
 */
public record FriendNotifyEvent(Long targetUserId,
                                String kind,
                                Object payload) {
}
