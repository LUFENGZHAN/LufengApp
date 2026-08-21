package com.study.chat.app.user.entity;


import lombok.Data;


@Data
public class UserProfile {


    private Long id;


    private Long userId;


    private String nickname;


    private String avatar;


    private Integer gender;


}