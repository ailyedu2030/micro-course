package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.MicroSpecialtyProposal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MicroSpecialtyProposalRepository extends BaseMapper<MicroSpecialtyProposal> {

    @Select("SELECT * FROM micro_specialty_proposals WHERE id = #{id} FOR UPDATE")
    MicroSpecialtyProposal selectByIdForUpdate(Long id);
}
