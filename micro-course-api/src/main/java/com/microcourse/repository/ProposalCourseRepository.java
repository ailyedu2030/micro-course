package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.proposal.ProposalCourse;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProposalCourseRepository extends BaseMapper<ProposalCourse> {
}
