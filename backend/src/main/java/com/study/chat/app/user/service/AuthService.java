package com.study.chat.app.user.service;


import com.study.chat.app.user.dto.LoginDTO;
import com.study.chat.app.user.dto.RegisterDTO;


import java.util.Map;



public interface AuthService {


    /**
     * 登录
     */
    Map<String,Object> login(LoginDTO dto);



    /**
     * 注册
     */
    void register(RegisterDTO dto);

    /**
     * 退出登录
     */

    void logout();

    /**
     * 个人信息
     */
    void user();

}