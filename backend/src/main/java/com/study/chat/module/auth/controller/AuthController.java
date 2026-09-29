package com.study.chat.module.auth.controller;

import com.study.chat.common.result.Result;
import com.study.chat.module.auth.dto.ChangePasswordReq;
import com.study.chat.module.auth.dto.LoginReq;
import com.study.chat.module.auth.dto.RefreshReq;
import com.study.chat.module.auth.dto.RegisterReq;
import com.study.chat.module.auth.dto.TokenVO;
import com.study.chat.module.auth.dto.UserVO;
import com.study.chat.module.auth.service.AuthService;
import com.study.chat.common.util.IpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口：注册 / 登录 / 刷新 / 登出 / 身份校验。Web 与 App 完全复用。
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public Result<UserVO> register(@Valid @RequestBody RegisterReq req, HttpServletRequest request) {
        return Result.success(authService.register(req, IpUtil.resolve(request)));
    }

    @PostMapping("/login")
    public Result<TokenVO> login(@Valid @RequestBody LoginReq req, HttpServletRequest request) {
        return Result.success(authService.login(req, IpUtil.resolve(request)));
    }

    @PostMapping("/refresh")
    public Result<TokenVO> refresh(@Valid @RequestBody RefreshReq req) {
        return Result.success(authService.refresh(req));
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
                               String authorization) {
        authService.logout(authorization);
        return Result.success();
    }

    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.success(authService.me());
    }

    @PostMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordReq req) {
        authService.changePassword(req);
        return Result.success();
    }
}
