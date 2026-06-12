package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.BadgeDefinition;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface BadgeDefinitionRepository extends BaseMapper<BadgeDefinition> {

    BadgeDefinition selectByCode(String code);

    List<BadgeDefinition> selectByCategory(String category);

    List<BadgeDefinition> selectAll();
}
