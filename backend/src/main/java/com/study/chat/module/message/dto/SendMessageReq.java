package com.study.chat.module.message.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SendMessageReq {

    /**
     * 客户端生成的幂等 ID（UUID），重试时必须复用同一个值
     */
    @NotBlank(message = "clientMsgId 不能为空")
    @Size(max = 64, message = "clientMsgId 过长")
    private String clientMsgId;

    /**
     * 已有会话时必填；与 peerId 二选一。
     */
    private Long conversationId;

    /**
     * 单聊对象的 userId。尚未建立会话时用它代替 conversationId，
     * 服务端校验好友关系后懒创建会话（添加好友不再预建空会话）。
     */
    private Long peerId;

    /**
     * 1文本 2图片 3语音 4视频 5文件 6位置
     */
    @NotNull(message = "msgType 不能为空")
    private Integer msgType;

    @Size(max = 4096, message = "消息内容超出长度限制")
    private String content;

    /**
     * JSON 字符串扩展字段
     */
    @Size(max = 4096, message = "扩展字段超出长度限制")
    private String extra;
}
