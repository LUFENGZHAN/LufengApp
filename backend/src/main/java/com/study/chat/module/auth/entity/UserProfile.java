package com.study.chat.module.auth.entity;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class UserProfile {

    private Long id;

    private Long userId;

    private String nickname;

    private String avatar;

    /**
     * 0未知 1男 2女
     */
    private Integer gender;

    private String signature;

    private String region;

    private LocalDate birthday;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
