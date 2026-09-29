package com.study.chat.module.user.controller;

import com.study.chat.common.auth.AuthContext;
import com.study.chat.common.result.Result;
import com.study.chat.module.auth.dto.UserVO;
import com.study.chat.module.user.dto.UpdateUserReq;
import com.study.chat.module.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 获取当前登录用户资料。
     * 同时支持 GET 与 POST：浏览器地址栏/工具手动探测多为 POST，
     * 仅 GET 会在该方法下被 Spring 拒为 405 → 10007，故一并放行。
     */
    @RequestMapping(value = "/me", method = {RequestMethod.GET, RequestMethod.POST})
    public Result<UserVO> me() {
        return Result.success(userService.me(AuthContext.userId()));
    }

    @PutMapping("/me")
    public Result<UserVO> update(@Valid @RequestBody UpdateUserReq req) {
        return Result.success(userService.update(AuthContext.userId(), req));
    }

    @GetMapping("/search")
    public Result<List<UserVO>> search(@RequestParam String keyword,
                                       @RequestParam(defaultValue = "20") int size) {
        return Result.success(userService.search(keyword, size));
    }

    @GetMapping("/{userNo}")
    public Result<UserVO> detail(@PathVariable String userNo) {
        return Result.success(userService.getByUserNo(userNo));
    }
}
