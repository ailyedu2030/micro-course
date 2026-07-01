package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.proposal.ProposalTeamMember;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProposalTeamMemberRepository extends BaseMapper<ProposalTeamMember> {
}
