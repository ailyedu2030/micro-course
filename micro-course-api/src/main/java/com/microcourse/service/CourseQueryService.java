package com.microcourse.service;

import com.microcourse.dto.CoursePageQuery;
import com.microcourse.dto.CourseVO;
import com.microcourse.dto.PageResult;

public interface CourseQueryService {

    PageResult<CourseVO> page(CoursePageQuery query);

    CourseVO getById(Long id);

    /**
     * 按教师 ID 查询课程列表 (权限矩阵 v4.0 §3.3 GET_TEACHER_COURSES)
     * @param teacherId 教师 ID
     * @param includeDrafts 是否包含草稿/驳回等非发布状态 (true: 课程详情页用, false: 学生端列表用)
     */
    java.util.List<CourseVO> listByTeacherId(Long teacherId, boolean includeDrafts);

    /**
     * 按教师查询课程列表 (含 TEACHER owner 校验)
     */
    java.util.List<CourseVO> listByTeacherIdWithOwnerCheck(Long teacherId, boolean includeDrafts);

    /**
     * P1 Stage 5: 按 hid 查询课程(幂等性,Trae 用来检测课程是否已创建)
     */
    CourseVO getByHid(String hid);
}
