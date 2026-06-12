package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.BadgeDefinition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface BadgeDefinitionRepository extends BaseMapper<BadgeDefinition> {

    @Select("SELECT * FROM badge_definitions WHERE code = #{code} LIMIT 1")
    BadgeDefinition selectByCode(String code);

    @Select("SELECT * FROM badge_definitions WHERE category = #{category}")
    List<BadgeDefinition> selectByCategory(String category);

    @Select("SELECT * FROM badge_definitions ORDER BY created_at ASC")
    List<BadgeDefinition> selectAll();
}
