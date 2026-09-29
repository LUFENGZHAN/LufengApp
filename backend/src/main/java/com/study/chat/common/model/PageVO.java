package com.study.chat.common.model;

import lombok.Data;

import java.util.List;

/**
 * 游标分页结果：hasMore + nextCursor，避免深翻页时的 OFFSET 性能衰减。
 */
@Data
public class PageVO<T> {

    private List<T> list;

    private boolean hasMore;

    /**
     * 下一页游标（消息场景为 seq）
     */
    private Long nextCursor;

    public static <T> PageVO<T> of(List<T> list, boolean hasMore, Long nextCursor) {
        PageVO<T> page = new PageVO<>();
        page.setList(list);
        page.setHasMore(hasMore);
        page.setNextCursor(nextCursor);
        return page;
    }
}
