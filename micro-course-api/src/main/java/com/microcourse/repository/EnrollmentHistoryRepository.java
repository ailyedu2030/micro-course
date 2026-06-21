package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.EnrollmentHistory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 选课变更历史 Mapper（P0-2 修复：启用 enrollment_histories 审计写入）。
 */
@Mapper
public interface EnrollmentHistoryRepository extends BaseMapper<EnrollmentHistory> {
}
