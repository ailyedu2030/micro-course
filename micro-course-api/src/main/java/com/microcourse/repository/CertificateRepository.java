package com.microcourse.repository;

import com.microcourse.entity.Certificate;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CertificateRepository extends com.baomidou.mybatisplus.core.mapper.BaseMapper<Certificate> {
}