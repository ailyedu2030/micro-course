package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.proposal.ChapterTeacherAssignment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChapterTeacherAssignmentRepository extends BaseMapper<ChapterTeacherAssignment> {
}
