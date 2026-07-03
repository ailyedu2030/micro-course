package com.microcourse.service;

import com.microcourse.dto.PageResult;
import com.microcourse.dto.PendingTaskVO;
import com.microcourse.dto.StudentActivityVO;
import com.microcourse.dto.TeacherCourseVO;
import com.microcourse.dto.TeacherNotificationVO;
import com.microcourse.dto.TeacherStatsVO;
import java.util.List;

public interface TeacherService {

    TeacherStatsVO getStats(Long teacherId);

    /** Phase 2.3: 获取教师收益看板 */
    com.microcourse.dto.TeacherRevenueVO getRevenue(Long teacherId);

    List<StudentActivityVO> getStudentActivity(Long teacherId, int days);

    List<PendingTaskVO> getPendingTasks(Long teacherId, int size);

    List<TeacherNotificationVO> getNotifications(Long teacherId, int size);

    PageResult<TeacherCourseVO> getMyCourses(Long teacherId, int page, int size);
}