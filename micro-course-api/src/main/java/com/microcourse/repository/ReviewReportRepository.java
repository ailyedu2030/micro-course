package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.ReviewReport;
import org.apache.ibatis.annotations.Mapper;

/**
 * 举报处理 Repository
 */
@Mapper
public interface ReviewReportRepository extends BaseMapper<ReviewReport> {
}
