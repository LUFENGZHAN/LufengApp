package com.study.chat.app.user.entity;


import lombok.Data;


@Data
public class UserAccount {


    /**
     * 主键
     */
    private Long id;


    /**
     * 用户编号
     */
    private String userNo;


    /**
     * 登录账号
     */
    private String account;


    /**
     * 密码
     */
    private String password;


    /**
     * 状态
     */
    private Integer status;

}