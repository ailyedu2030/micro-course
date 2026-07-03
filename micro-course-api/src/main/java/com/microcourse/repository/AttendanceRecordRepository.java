package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.AttendanceRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AttendanceRecordRepository extends BaseMapper<AttendanceRecord> {
}
