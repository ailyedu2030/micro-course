package com.microcourse.service;

import com.microcourse.dto.BatchOperationResult;
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
import java.util.Map;

/**
 * 微专业主表 Service 接口。
 * <p>
 * 职责：CRUD + 状态流转（DRAFT/PENDING_REVIEW/APPROVED/REJECTED/
 * RECRUITING/COMPLETED/CANCELLED/ARCHIVED）+ 课程编排 + 教师团队 +
 * 置顶管理 + 统计查询 + 角色鉴权（isLeadOf / isMemberOf / isOwnerOrLead）。
 * </p>
 *
 * @author 微课平台团队
 * @since Phase 14 v1.0
 */
public interface MicroSpecialtyService {

    // ======================== 查询 ========================

    /**
     * 分页列表查询：学生角色仅看 RECRUITING 状态；ACADEMIC/ADMIN 看全部。
     *
     * @param page   0-based 页码
     * @param size   每页大小
     * @param params 过滤参数：keyword（模糊搜索）、status（精确状态）、
     *               featuredStatus（置顶状态）、isGoldFeatured（金标）、
     *               featured（任意置顶）
     * @return 分页结果，包含 MicroSpecialtyVO 列表
     */
    PageResult<MicroSpecialtyVO> page(int page, int size, Map<String, Object> params);

    /**
     * 课程广场专区数据：返回 goldFeatured（金标） + featured（置顶） +
     * recruiting（招生中）三组数据，供前端 Hero 专区渲染。
     *
     * @return 广场三组数据聚合 VO
     */
    MicroSpecialtySquareVO getSquareData();

    /**
     * 微专业详情：含基本字段、已编排课程列表、教师团队、修读统计。
     * DRAFT/CANCELLED 状态对非 ADMIN/ACADEMIC 角色过滤。
     *
     * @param id 微专业 ID
     * @return 完整详情 VO
     */
    MicroSpecialtyDetailVO getDetail(Long id);

    /**
     * 微专业统计数据：选课率、完成率、平均分、质量分等。
     *
     * @param id 微专业 ID
     * @return 统计 VO
     */
    MicroSpecialtyStatsVO stats(Long id);

    // ======================== CUD ========================

    /**
     * 教务处直立创建微专业（初始状态 DRAFT）。
     *
     * @param request 创建请求（含 title / code / department / leadTeacher 等必填字段）
     * @return 创建后的 VO
     */
    MicroSpecialtyVO create(MicroSpecialtyCreateRequest request);

    /**
     * 更新基本信息：LEAD 或 ADMIN 可操作；REJECTED 状态下也允许编辑。
     *
     * @param id      微专业 ID
     * @param request 更新字段（partial update）
     * @return 更新后的 VO
     */
    MicroSpecialtyVO update(Long id, MicroSpecialtyUpdateRequest request);

    /**
     * 软删除微专业（仅 ADMIN）。
     *
     * @param id 微专业 ID
     */
    void delete(Long id);

    // ======================== 状态流转 ========================

    /**
     * 提交/重新提交审核：DRAFT → PENDING_REVIEW 或 REJECTED → PENDING_REVIEW。
     * 前置条件：≥1 门课程已编排、lead_teacher 已接受邀请。
     *
     * @param id 微专业 ID
     */
    void submit(Long id);

    /**
     * 教务处审批通过：PENDING_REVIEW → APPROVED。自动通知 LEAD。
     *
     * @param id 微专业 ID
     */
    void approve(Long id);

    /**
     * 教务处审批驳回：PENDING_REVIEW → REJECTED。需填写驳回原因，自动通知 LEAD。
     *
     * @param id     微专业 ID
     * @param reason 驳回原因（必填）
     */
    void reject(Long id, String reason);

    /**
     * LEAD 开课：APPROVED → RECRUITING。课程广场立即可见。
     * 前置条件：课程编排完成、团队 ≥2（含 LEAD）、LEAD 已接受。
     *
     * @param id 微专业 ID
     */
    void open(Long id);

    /**
     * LEAD 结业：RECRUITING → COMPLETED。所有必修课已结课或强制跳过。
     *
     * @param id 微专业 ID
     */
    void close(Long id);

    /**
     * 教务处强制取消：任意状态 → CANCELLED（终态）。
     * 事务内级联设置所有非终态 enrollments 为 DROPPED，通知所有相关人。
     *
     * @param id     微专业 ID
     * @param reason 取消原因（必填）
     */
    void cancel(Long id, String reason);

    /**
     * 【C-17】重新开课：COMPLETED/CANCELLED → APPROVED。仅 ACADEMIC/ADMIN。
     *
     * @param id 微专业 ID
     */
    void reopen(Long id);

    /**
     * 归档：COMPLETED → ARCHIVED。通知 LEAD。
     *
     * @param id 微专业 ID
     */
    void archive(Long id);

    /** P2-11: 批量审批通过 */
    BatchOperationResult batchApprove(java.util.List<Long> ids);

    /** P2-11: 批量审批驳回 */
    BatchOperationResult batchReject(java.util.List<Long> ids, String reason);

    // ======================== 课程编排 ========================

    /**
     * 获取微专业下所有已编排课程（按 sort_order 排序）。
     *
     * @param msId 微专业 ID
     * @return 课程 VO 列表
     */
    List<MicroSpecialtyCourseVO> listCourses(Long msId);

    /**
     * 添加课程到微专业编排。
     *
     * @param msId    微专业 ID
     * @param request 课程编排请求（courseId / sortOrder / isRequired / credits / minScore 等）
     * @return 编排后的课程 VO
     */
    MicroSpecialtyCourseVO addCourse(Long msId, MicroSpecialtyCourseRequest request);

    /**
     * 更新课程编排项：排序序号、必修/选修标记、学分、通过分等。
     *
     * @param msId   微专业 ID
     * @param itemId 编排项 ID
     * @param request 更新字段
     * @return 更新后的课程 VO
     */
    MicroSpecialtyCourseVO updateCourseItem(Long msId, Long itemId, MicroSpecialtyCourseRequest request);

    /**
     * 从微专业编排中移除课程。
     *
     * @param msId   微专业 ID
     * @param itemId 编排项 ID
     */
    void removeCourse(Long msId, Long itemId);

    // ======================== 教师团队 ========================

    /**
     * 获取微专业教师团队列表（含 LEAD、MEMBER、ASSISTANT）。
     *
     * @param msId 微专业 ID
     * @return 教师 VO 列表
     */
    List<MicroSpecialtyTeacherVO> listTeachers(Long msId);

    /**
     * LEAD 发送教师邀请。自动判断跨学院：同学院直接 ACTIVE，
     * 跨学院 → PENDING_ACADEMIC（需教务处审批）。
     *
     * @param msId    微专业 ID
     * @param request 邀请请求（teacherId / role / courseId / responsibility）
     * @return 教师团队 VO
     */
    MicroSpecialtyTeacherVO inviteTeacher(Long msId, MicroSpecialtyTeacherRequest request);

    /**
     * LEAD/ADMIN 移除教师。发通知给被移除教师。
     *
     * @param msId      微专业 ID
     * @param teacherId 教师 ID
     */
    void removeTeacher(Long msId, Long teacherId);

    // ======================== LEAD 继任 ========================

    /**
     * 教务处发起 LEAD 继任：指定新 LEAD，事务内转移 role 和 lead_teacher_id。
     * 原 LEAD 降为 MEMBER 或移除；若新 LEAD 不在团队中自动创建 ACTIVE 记录。
     * DB 触发器 trg_ms_one_lead 在事务提交时确保恰好 1 条 ACTIVE LEAD。
     * 仅非终态（DRAFT/PENDING_REVIEW/APPROVED/REJECTED/RECRUITING）可转移。
     *
     * @param msId    微专业 ID
     * @param request 继任请求（newLeadTeacherId）
     */
    void transferLeadership(Long msId, MicroSpecialtyLeadTransferRequest request);

    // ======================== 角色鉴权 ========================

    /**
     * 判断指定用户是否是该微专业的 LEAD（ACTIVE 状态）。
     *
     * @param msId   微专业 ID
     * @param userId 用户 ID
     * @return true 如果是 ACTIVE LEAD
     */
    boolean isLeadOf(Long msId, Long userId);

    /**
     * 校验当前用户是否为微专业负责人或系统管理员，否则抛出业务异常。
     *
     * @param msId 微专业 ID
     * @throws com.microcourse.exception.BusinessException 如果不是 LEAD 且非 ADMIN
     */
    void requireLeadOf(Long msId);

    /**
     * 判断指定用户是否是该微专业的成员（MEMBER 或 ASSISTANT，ACTIVE 状态）。
     *
     * @param msId   微专业 ID
     * @param userId 用户 ID
     * @return true 如果是 ACTIVE MEMBER/ASSISTANT
     */
    boolean isMemberOf(Long msId, Long userId);

    /**
     * 判断指定用户是否为 LEAD 或创建者（用于 submit / delete 等需要更高权限的操作）。
     *
     * @param msId   微专业 ID
     * @param userId 用户 ID
     * @return true 如果是 ACTIVE LEAD 或 micro_specialties.creator_id == userId
     */
    boolean isOwnerOrLead(Long msId, Long userId);

    /**
     * 获取当前用户在该微专业中的角色。
     *
     * @param msId 微专业 ID
     * @return "LEAD" / "MEMBER" / "ASSISTANT" / null (无角色)
     */
    String getMyRole(Long msId);
}
