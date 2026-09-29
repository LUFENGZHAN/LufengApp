package com.study.chat.module.friend.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 好友关系：双向存两行，便于单侧查询走索引。
 */
@Data
public class Friend {

    private Long id;

    private Long userId;

    private Long friendId;

    /** NOT NULL 列：实体层给默认值，杜绝显式写入 null */
    private String remark = "";

    private String source;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
