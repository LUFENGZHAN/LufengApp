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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
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
