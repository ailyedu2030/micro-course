package com.microcourse.service;

import com.microcourse.dto.AcademicOverviewVO;
import com.microcourse.dto.CompletionWarningVO;
import com.microcourse.dto.DepartmentDetailVO;
import com.microcourse.dto.DepartmentStatsVO;
import com.microcourse.dto.TrendPointVO;

import java.util.List;

/**
 * 教务处驾驶舱统计服务接口
 * 面向 ACADEMIC 角色的跨院系统计 API
 */
public interface AcademicStatsService {

    /**
     * 获取全校概览数据
     * @return 全校概览 VO
     */
    AcademicOverviewVO getOverview();

    /**
     * 获取院系列表（含统计）
     * @return 各院系统计列表
     */
    List<DepartmentStatsVO> getDepartmentStats();

    /**
     * 获取单院详情
     * @param departmentId 院系 ID
     * @return 院系详情 VO
     */
    DepartmentDetailVO getDepartmentDetail(Long departmentId);

    /**
     * 获取完成率预警课程（完成率 < 30%）
     * @return 完成率预警列表
     */
    List<CompletionWarningVO> getCompletionWarnings();

    /**
     * 获取参与率趋势
     * @param semester 学期（如 "2025-1"，可为空）
     * @return 参与率趋势点列表
     */
    List<TrendPointVO> getParticipationTrend(String semester);

    /**
     * 获取完成率趋势
     * @param semester 学期（如 "2025-1"，可为空）
     * @return 完成率趋势点列表
     */
    List<TrendPointVO> getCompletionTrend(String semester);
}
