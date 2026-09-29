package com.study.chat.module.message.controller;

import com.study.chat.common.auth.AuthContext;
import com.study.chat.common.model.PageVO;
import com.study.chat.common.result.Result;
import com.study.chat.module.message.dto.MessageVO;
import com.study.chat.module.message.dto.SendMessageReq;
import com.study.chat.module.message.dto.SyncReq;
import com.study.chat.module.message.service.MessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @PostMapping("/messages")
    public Result<MessageVO> send(@Valid @RequestBody SendMessageReq req) {
        return Result.success(messageService.send(AuthContext.userId(), AuthContext.deviceId(), req));
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public Result<PageVO<MessageVO>> history(@PathVariable Long conversationId,
                                             @RequestParam(required = false) Long cursor,
                                             @RequestParam(defaultValue = "20") int size) {
        return Result.success(messageService.history(AuthContext.userId(), conversationId, cursor, size));
    }

    /**
     * 断线重连后的离线补拉。
     */
    @PostMapping("/messages/sync")
    public Result<List<MessageVO>> sync(@Valid @RequestBody SyncReq req) {
        return Result.success(messageService.sync(AuthContext.userId(), req));
    }

    @PostMapping("/messages/{msgNo}/recall")
    public Result<Void> recall(@PathVariable String msgNo) {
        messageService.recall(AuthContext.userId(), msgNo, AuthContext.deviceId());
        return Result.success();
    }
}
