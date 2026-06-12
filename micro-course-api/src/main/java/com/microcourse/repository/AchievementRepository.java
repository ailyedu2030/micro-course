package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.Achievement;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AchievementRepository extends BaseMapper<Achievement> {

    @Select("SELECT * FROM achievements WHERE user_id = #{userId} ORDER BY earned_at DESC")
    List<Achievement> selectByUserId(Long userId);

    @Select("SELECT EXISTS(SELECT 1 FROM achievements WHERE user_id = #{userId} AND badge_code = #{badgeCode})")
    boolean existsByUserIdAndBadgeCode(Long userId, String badgeCode);

    @Delete("DELETE FROM achievements WHERE user_id = #{userId} AND badge_code = #{badgeCode}")
    int deleteByUserIdAndBadgeCode(Long userId, String badgeCode);
}
