package com.study.chat.module.conversation.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReadReq {

    /**
     * 已读位点：服务端用 GREATEST 保证不会回退
     */
    @NotNull(message = "lastReadSeq 不能为空")
    private Long lastReadSeq;
}
