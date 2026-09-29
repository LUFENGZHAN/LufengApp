package com.study.chat.module.conversation.controller;

import com.study.chat.common.auth.AuthContext;
import com.study.chat.common.result.Result;
import com.study.chat.module.conversation.dto.ConversationReq;
import com.study.chat.module.conversation.dto.ConversationVO;
import com.study.chat.module.conversation.dto.ReadReq;
import com.study.chat.module.conversation.service.ConversationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    @PostMapping("/private")
    public Result<ConversationVO> createPrivate(@Valid @RequestBody ConversationReq req) {
        return Result.success(conversationService.createPrivate(AuthContext.userId(), req.getTargetUserId()));
    }

    @GetMapping
    public Result<List<ConversationVO>> list(@RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return Result.success(conversationService.list(AuthContext.userId(), page, size));
    }

    @GetMapping("/{conversationId}")
    public Result<ConversationVO> detail(@PathVariable Long conversationId) {
        return Result.success(conversationService.detail(AuthContext.userId(), conversationId));
    }

    @DeleteMapping("/{conversationId}")
    public Result<Void> delete(@PathVariable Long conversationId) {
        conversationService.delete(AuthContext.userId(), conversationId);
        return Result.success();
    }

    @PostMapping("/{conversationId}/read")
    public Result<Map<String, Long>> read(@PathVariable Long conversationId,
                                          @Valid @RequestBody ReadReq req) {
        Long unread = conversationService.markRead(
                AuthContext.userId(), conversationId, req.getLastReadSeq(), AuthContext.deviceId());
        return Result.success(Map.of("unreadCount", unread));
    }

    @PutMapping("/{conversationId}/mute")
    public Result<Void> mute(@PathVariable Long conversationId, @RequestParam boolean muted) {
        conversationService.mute(AuthContext.userId(), conversationId, muted);
        return Result.success();
    }
}
