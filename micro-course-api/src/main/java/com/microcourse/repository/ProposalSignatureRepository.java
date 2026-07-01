package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.proposal.ProposalSignature;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProposalSignatureRepository extends BaseMapper<ProposalSignature> {
}
