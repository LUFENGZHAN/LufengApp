package com.study.chat.app.user.controller;


import com.study.chat.common.annotation.LoginRequired;
import com.study.chat.common.annotation.RequestDTO;
import com.study.chat.common.result.Result;
import com.study.chat.app.user.dto.LoginDTO;
import com.study.chat.app.user.dto.RegisterDTO;
import com.study.chat.app.user.service.AuthService;


import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;



@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {


    private final AuthService authService;



    /**
     * 登录
     */

    @PostMapping("/login")
    public Result login(
            @Valid @RequestDTO LoginDTO dto
    ){

        return Result.success(
                authService.login(dto)
        );

    }



    /**
     * 注册
     */
    @PostMapping("/register")
    public Result register(
            @Valid @RequestDTO RegisterDTO dto
    ){

        authService.register(dto);


        return Result.success(null);

    }

    /**
     * 个人信息
     */
    @LoginRequired
    @GetMapping("/user")
    public Result user(){
        return Result.success(null);
    }
}