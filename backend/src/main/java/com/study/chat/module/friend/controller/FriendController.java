package com.study.chat.module.friend.controller;

import com.study.chat.common.auth.AuthContext;
import com.study.chat.common.result.Result;
import com.study.chat.module.friend.dto.ApplyFriendReq;
import com.study.chat.module.friend.dto.FriendRequestVO;
import com.study.chat.module.friend.dto.FriendVO;
import com.study.chat.module.friend.dto.HandleFriendReq;
import com.study.chat.module.friend.service.FriendService;
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
@RequestMapping("/api/v1/friends")
@RequiredArgsConstructor
public class FriendController {

    private final FriendService friendService;

    @PostMapping("/requests")
    public Result<Map<String, Long>> apply(@Valid @RequestBody ApplyFriendReq req) {
        return Result.success(Map.of("requestId", friendService.apply(AuthContext.userId(), req)));
    }

    @GetMapping("/requests")
    public Result<List<FriendRequestVO>> requests(@RequestParam(defaultValue = "in") String direction,
                                                  @RequestParam(required = false) Integer status) {
        return Result.success(friendService.requests(AuthContext.userId(), direction, status));
    }

    @PutMapping("/requests/{requestId}")
    public Result<Void> handle(@PathVariable Long requestId, @Valid @RequestBody HandleFriendReq req) {
        friendService.handle(AuthContext.userId(), requestId, req.getAction());
        return Result.success();
    }

    @GetMapping
    public Result<List<FriendVO>> friends() {
        return Result.success(friendService.friends(AuthContext.userId()));
    }

    @DeleteMapping("/{friendId}")
    public Result<Void> delete(@PathVariable Long friendId) {
        friendService.delete(AuthContext.userId(), friendId);
        return Result.success();
    }

    @PutMapping("/{friendId}/remark")
    public Result<Void> remark(@PathVariable Long friendId, @RequestBody Map<String, String> body) {
        friendService.remark(AuthContext.userId(), friendId, body.get("remark"));
        return Result.success();
    }
}
