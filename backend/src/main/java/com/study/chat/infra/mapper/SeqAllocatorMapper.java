package com.study.chat.infra.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * Redis 不可用时的 seq 降级方案：乐观锁递增号段表。
 */
public interface SeqAllocatorMapper {

    /**
     * 会话创建时初始化号段，保证 Redis 挂掉也能分配 seq。
     */
    @Insert("insert into seq_allocator (biz_key, current_seq, version) values (#{bizKey}, #{currentSeq}, 0) "
            + "on duplicate key update current_seq = greatest(current_seq, values(current_seq))")
    int init(@Param("bizKey") String bizKey, @Param("currentSeq") long currentSeq);

    @Select("select current_seq from seq_allocator where biz_key = #{bizKey}")
    Long selectCurrent(@Param("bizKey") String bizKey);

    /**
     * version 乐观锁：更新成功返回 1，失败返回 0（调用方重试）。
     */
    @Update("update seq_allocator set current_seq = #{nextSeq}, version = version + 1 "
            + "where biz_key = #{bizKey} and version = #{version}")
    int increase(@Param("bizKey") String bizKey, @Param("nextSeq") long nextSeq, @Param("version") long version);

    @Select("select version from seq_allocator where biz_key = #{bizKey}")
    Long selectVersion(@Param("bizKey") String bizKey);
}
