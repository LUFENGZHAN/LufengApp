package com.study.chat.module.message.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 断线重连后的离线补拉请求：每个会话带上本地已确认的最大 seq。
 */
@Data
public class SyncReq {

    @Valid
    @Size(max = 200, message = "一次最多同步 200 个会话")
    private List<SyncItem> items;

    @Data
    public static class SyncItem {

        @NotNull(message = "conversationId 不能为空")
        private Long conversationId;

        /**
         * 本地已确认接收的最大 seq，服务端返回大于该值的消息
         */
        private Long afterSeq;
    }
}
