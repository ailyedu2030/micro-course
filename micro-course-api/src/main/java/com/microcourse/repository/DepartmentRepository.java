package com.microcourse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.microcourse.entity.Department;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DepartmentRepository extends BaseMapper<Department> {
}