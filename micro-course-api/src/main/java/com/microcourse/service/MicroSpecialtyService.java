package com.microcourse.service;

import com.microcourse.dto.PageResult;
import com.microcourse.dto.microSpecialty.MicroSpecialtyCreateRequest;
import com.microcourse.dto.microSpecialty.MicroSpecialtyDetailVO;
import com.microcourse.dto.microSpecialty.MicroSpecialtySquareVO;
import com.microcourse.dto.microSpecialty.MicroSpecialtyUpdateRequest;
import com.microcourse.dto.microSpecialty.MicroSpecialtyVO;
import com.microcourse.dto.microSpecialty.MicroSpecialtyStatsVO;
import com.microcourse.dto.microSpecialty.MicroSpecialtyCourseRequest;
import com.microcourse.dto.microSpecialty.MicroSpecialtyCourseVO;
import com.microcourse.dto.microSpecialty.MicroSpecialtyTeacherRequest;
import com.microcourse.dto.microSpecialty.MicroSpecialtyTeacherVO;
import com.microcourse.dto.microSpecialty.MicroSpecialtyLeadTransferRequest;

import java.util.List;

/**
 * 微专业主表 Service 接口。
 * 职责：CRUD + 状态流转 + 课程编排 + 教师团队 + 置顶 + 统计 + 角色鉴权。
 */
public interface MicroSpecialtyService {

    // ====== 查询 ======

    /** 分页列表（学生看 RECRUITING；ACADEMIC/ADMIN 看全部） */
    PageResult<MicroSpecialtyVO> page(int page, int size, String keyword, String status);

    /** 课程广场专区：返回 goldFeatured + featured + recruiting 三组 */
    MicroSpecialtySquareVO getSquareData();

    /** 详情（含课程编排 + 教师团队 + 统计） */
    MicroSpecialtyDetailVO getDetail(Long id);

    /** 统计数据 */
    MicroSpecialtyStatsVO stats(Long id);

    // ====== CUD ======

    /** 教务处直立创建（DRAFT） */
    MicroSpecialtyVO create(MicroSpecialtyCreateRequest request);

    /** 更新基本信息（LEAD/ADMIN；REJECTED 状态也可编辑） */
    MicroSpecialtyVO update(Long id, MicroSpecialtyUpdateRequest request);

    /** 软删除（ADMIN） */
    void delete(Long id);

    // ====== 状态流转 ======

    /** 提交/重新提交审核（DRAFT/REJECTED → PENDING_REVIEW） */
    void submit(Long id);

    /** 审批通过（PENDING_REVIEW → APPROVED） */
    void approve(Long id);

    /** 审批驳回（PENDING_REVIEW → REJECTED） */
    void reject(Long id, String reason);

    /** 开课（APPROVED → RECRUITING） */
    void open(Long id);

    /** 结业（RECRUITING → COMPLETED） */
    void close(Long id);

    /** 强制取消（任意 → CANCELLED，级联 DROPPED） */
    void cancel(Long id);

    /** 归档（COMPLETED → ARCHIVED） */
    void archive(Long id);

    // ====== 课程编排 ======

    /** 获取微专业课程列表 */
    List<MicroSpecialtyCourseVO> listCourses(Long msId);

    /** 添加课程 */
    MicroSpecialtyCourseVO addCourse(Long msId, MicroSpecialtyCourseRequest request);

    /** 更新课程编排（排序/必修/学分） */
    MicroSpecialtyCourseVO updateCourseItem(Long msId, Long itemId, MicroSpecialtyCourseRequest request);

    /** 移除课程 */
    void removeCourse(Long msId, Long itemId);

    // ====== 教师团队 ======

    /** 获取教师团队列表 */
    List<MicroSpecialtyTeacherVO> listTeachers(Long msId);

    /** 发送邀请 */
    MicroSpecialtyTeacherVO inviteTeacher(Long msId, MicroSpecialtyTeacherRequest request);

    /** 移除教师 */
    void removeTeacher(Long msId, Long teacherId);

    /** 重新邀请（复用 REMOVED/DECLINED 记录） */
    MicroSpecialtyTeacherVO reinviteTeacher(Long msId, Long teacherId);

    // ====== LEAD 继任 ======

    /** LEAD 继任：指定新 LEAD（事务内转移） */
    void transferLeadership(Long msId, MicroSpecialtyLeadTransferRequest request);

    // ====== 置顶（委托 FeaturedService） ======

    /** 申请置顶 */
    void applyFeatured(Long msId, String reason);

    /** 批准置顶 */
    void approveFeatured(Long msId);

    /** 驳回置顶 */
    void rejectFeatured(Long msId, String reason);

    /** 取消置顶 */
    void unsetFeatured(Long msId);

    // ====== 角色鉴权 ======

    /** 当前用户是否是微专业 LEAD */
    boolean isLeadOf(Long msId, Long userId);

    /** 当前用户是否是微专业成员（MEMBER/ASSISTANT） */
    boolean isMemberOf(Long msId, Long userId);

    /** 是否是 LEAD 或创建者（用于 submit/delete） */
    boolean isOwnerOrLead(Long msId, Long userId);
}
