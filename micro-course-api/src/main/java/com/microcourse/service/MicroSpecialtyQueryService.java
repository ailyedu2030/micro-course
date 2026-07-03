package com.microcourse.service;

import com.microcourse.dto.PageResult;
import com.microcourse.dto.microSpecialty.MicroSpecialtyCourseVO;
import com.microcourse.dto.microSpecialty.MicroSpecialtyDetailVO;
import com.microcourse.dto.microSpecialty.MicroSpecialtySquareVO;
import com.microcourse.dto.microSpecialty.MicroSpecialtyStatsVO;
import com.microcourse.dto.microSpecialty.MicroSpecialtyTeacherVO;
import com.microcourse.dto.microSpecialty.MicroSpecialtyVO;
import com.microcourse.entity.MicroSpecialty;
import com.microcourse.entity.MicroSpecialtyCourse;
import com.microcourse.entity.MicroSpecialtyTeacher;

import java.util.List;
import java.util.Map;

/**
 * 微专业查询 Service，仅含只读/查询方法。
 * <p>
 * 从 MicroSpecialtyService 中拆分，用于瘦身 MicroSpecialtyServiceImpl (1632→&lt;800行)。
 * MicroSpecialtyService 接口保持不变（含查询方法签名），由 MicroSpecialtyServiceImpl
 * 委托本服务实现查询逻辑。
 * </p>
 */
public interface MicroSpecialtyQueryService {

    /**
     * 分页列表查询：学生角色仅看 RECRUITING 状态；ACADEMIC/ADMIN 看全部。
     */
    PageResult<MicroSpecialtyVO> page(int page, int size, Map<String, Object> params);

    /**
     * 课程广场专区数据：返回 goldFeatured + featured + recruiting 三组数据。
     */
    MicroSpecialtySquareVO getSquareData();

    /**
     * 微专业详情：含基本字段、已编排课程列表、教师团队、修读统计。
     */
    MicroSpecialtyDetailVO getDetail(Long id);

    /**
     * 微专业统计数据：选课率、完成率、平均分、质量分等。
     */
    MicroSpecialtyStatsVO stats(Long id);

    /**
     * 获取微专业下所有已编排课程（按 sort_order 排序）。
     */
    List<MicroSpecialtyCourseVO> listCourses(Long msId);

    /**
     * 获取微专业教师团队列表（含 LEAD、MEMBER、ASSISTANT）。
     */
    List<MicroSpecialtyTeacherVO> listTeachers(Long msId);

    /**
     * 判断指定用户是否是该微专业的 LEAD（ACTIVE 状态）。
     */
    boolean isLeadOf(Long msId, Long userId);

    /**
     * 判断指定用户是否是该微专业的成员（MEMBER 或 ASSISTANT，ACTIVE 状态）。
     */
    boolean isMemberOf(Long msId, Long userId);

    /**
     * 判断指定用户是否为 LEAD 或创建者。
     */
    boolean isOwnerOrLead(Long msId, Long userId);

    /**
     * 获取当前用户在该微专业中的角色。
     */
    String getMyRole(Long msId);

    // ======================== 转换方法（供 MicroSpecialtyServiceImpl CUD 委托调用） ========================

    /**
     * 将微专业实体转换为 VO（含部门名、负责人名、创建者名、课程数等统计）。
     */
    MicroSpecialtyVO toVO(MicroSpecialty ms);

    /**
     * 将课程编排项转换为 VO（含课程详情）。
     */
    MicroSpecialtyCourseVO toCourseVO(MicroSpecialtyCourse item);

    /**
     * 将教师团队成员转换为 VO（含教师姓名、头像、所授课程标题）。
     */
    MicroSpecialtyTeacherVO toTeacherVO(MicroSpecialtyTeacher t);
}
