package com.study.chat.module.message.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息主表：seq 为会话内单调递增序号，是排序与分页的唯一基准。
 */
@Data
public class Message {

    private Long id;

    private String msgNo;

    private Long conversationId;

    private Long seq;

    private Long senderId;

    /**
     * 单聊接收者，冗余便于统计
     */
    private Long receiverId;

    /**
     * 1文本 2图片 3语音 4视频 5文件 6位置 10系统 11撤回
     */
    private Integer msgType;

    private String content;

    /**
     * JSON 扩展：图片宽高、文件 URL、语音时长、@列表
     */
    private String extra;

    /**
     * 客户端幂等 ID
     */
    private String clientMsgId;

    /**
     * 1正常 2已撤回 3已删除
     */
    private Integer status;

    private LocalDateTime sendTime;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
