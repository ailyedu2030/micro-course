package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.Certificate;
import org.apache.ibatis.annotations.Mapper;

/**
 * 课程证书 Mapper
 */
@Mapper
public interface CertificateRepository extends BaseMapper<Certificate> {
}
