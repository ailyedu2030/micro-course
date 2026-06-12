package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.Achievement;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AchievementRepository extends BaseMapper<Achievement> {

    List<Achievement> selectByUserId(Long userId);

    boolean existsByUserIdAndBadgeCode(Long userId, String badgeCode);

    int deleteByUserIdAndBadgeCode(Long userId, String badgeCode);
}
