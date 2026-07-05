package com.microcourse.service;

import com.microcourse.dto.CourseCreateRequest;
import com.microcourse.dto.CoursePageQuery;
import com.microcourse.dto.CoursePricingRequest;
import com.microcourse.dto.PricingForAdopterVO;
import com.microcourse.dto.CourseStatsVO;
import com.microcourse.dto.CourseUpdateRequest;
import com.microcourse.dto.CourseVO;
import com.microcourse.dto.PageResult;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface CourseService {

    PageResult<CourseVO> page(CoursePageQuery query);

    CourseVO getById(Long id);

    CourseVO create(CourseCreateRequest request);

    CourseVO update(Long id, CourseUpdateRequest request);

    void updateStatus(Long id, Integer status);

    /**
     * 提交课程审核（草稿 → 待审核）
     * @param id 课程ID
     */
    void submitForReview(Long id);

    /**
     * 审核通过（待审核 → 已通过）
     * @param id 课程ID
     */
    void approve(Long id);

    /**
     * 审核拒绝（待审核 → 已驳回）
     * @param id 课程ID
     * @param reason 拒绝原因
     */
    void reject(Long id, String reason);

    /**
     * 发布课程（已通过 → 已发布）
     * @param id 课程ID
     */
    void publish(Long id);

    void delete(Long id);

    /**
     * 复制课程（模板复制：复制课程基本信息 + 章节结构，不含视频文件）
     * @param id 被复制的课程ID
     * @return 新课程VO
     */
    CourseVO copy(Long id);

    /**
     * 更新课程封面
     * @param id 课程ID
     * @param file 封面文件
     * @return 课程VO
     */
    CourseVO updateCover(Long id, MultipartFile file);

    /**
     * Round 5-3 (P1-10): 计算课程统计数据（选课人数 / 完成率 / 平均分等）。
     *
     * <p>仅做数据装配，按角色的权限校验（TEACHER 必须课主 / ADMIN / ACADEMIC）在 Controller 层执行。</p>
     *
     * @param courseId 课程 ID
     * @return 课程统计 VO
     */
    CourseStatsVO computeStats(Long courseId);

    /**
     * 下架课程（已发布 → CLOSED），并通知在学学生。
     * 权限：ADMIN（含 @PreAuthorize 在 Controller 层）
     */
    void unpublish(Long id);

    /** Phase 4: 更新课程定价 */
    void updatePricing(Long courseId, CoursePricingRequest request);

    /** Phase 4: 查询课程对某教师的费用 */
    PricingForAdopterVO getPricingForAdopter(Long courseId);

    /** Round 1: 查询课程对当前登录用户的价格（学生端可见） */
    com.microcourse.dto.CoursePricingInfoVO getMyPricing(Long courseId);

    /** P0 修复: 提交定价审核 (DRAFT → PENDING) */
    void submitPricingForReview(Long courseId);

    /** P0 修复: 审核定价 (PENDING → APPROVED / REJECTED) */
    void reviewPricing(Long courseId, boolean approved, String reason);
}