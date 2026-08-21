package com.study.chat.app.user.service.impl;


import com.study.chat.app.user.dto.LoginDTO;
import com.study.chat.app.user.dto.RegisterDTO;
import com.study.chat.app.user.service.AuthService;


import org.springframework.stereotype.Service;


import java.util.HashMap;
import java.util.Map;



@Service
public class AuthServiceImpl implements AuthService {



    @Override
    public Map<String,Object> login(LoginDTO dto) {


        // TODO 登录逻辑


        Map<String,Object> result = new HashMap<>();

        result.put("token","test-token");


        return result;

    }



    @Override
    public void register(RegisterDTO dto) {


        // TODO 注册逻辑

        /*
          1. 查询账号是否存在
          2. BCrypt加密密码
          3. 保存 user_account
          4. 创建 user_profile
        */


    }

    @Override
    public void logout() {

    }

    @Override
    public void user(){

    }
}