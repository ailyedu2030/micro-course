package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Optional;

@Mapper
public interface UserRepository extends BaseMapper<User> {

    @Select("SELECT * FROM users WHERE username = #{username} AND deleted_at IS NULL LIMIT 1")
    Optional<User> findByUsername(@Param("username") String username);

    /**
     * Round 6 状态机修复：按 ID 查询用户（含已软删除记录）。
     *
     * <p>全局逻辑删除字段为 deleted_at，BaseMapper.selectById 会自动追加
     * {@code deleted_at IS NULL}，导致 DELETED 用户无法被读取。恢复流程
     * （DELETED → INACTIVE）需要绕过该过滤，故使用显式 @Select 注解 SQL。</p>
     */
    @Select("SELECT * FROM users WHERE id = #{id} LIMIT 1")
    User selectByIdIncludingDeleted(@Param("id") Long id);

    /**
     * Round 6 状态机修复：将软删除用户恢复为 INACTIVE（未激活）。
     *
     * <p>同样绕过逻辑删除过滤：清空 deleted_at、置 status=0，并递增 version 以保持
     * 乐观锁语义一致。仅用于 DELETED → INACTIVE 的合法恢复路径。</p>
     *
     * @return 受影响行数（1 表示恢复成功）
     */
    @Update("UPDATE users SET status = 1, deleted_at = NULL, version = version + 1, updated_at = now() WHERE id = #{id}")
    int restoreToActive(@Param("id") Long id);
}