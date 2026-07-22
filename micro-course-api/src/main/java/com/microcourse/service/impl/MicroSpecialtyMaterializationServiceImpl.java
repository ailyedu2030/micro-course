package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.entity.*;
import com.microcourse.entity.proposal.*;
import com.microcourse.enums.CourseStatus;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.*;
import com.microcourse.service.MicroSpecialtyMaterializationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class MicroSpecialtyMaterializationServiceImpl implements MicroSpecialtyMaterializationService {

    private static final Logger log = LoggerFactory.getLogger(MicroSpecialtyMaterializationServiceImpl.class);

    private final MicroSpecialtyProposalRepository proposalRepo;
    private final ProposalCourseRepository proposalCourseRepo;
    private final ProposalChapterRepository proposalChapterRepo;
    private final ChapterTeacherAssignmentRepository assignmentRepo;
    private final MicroSpecialtyCourseChapterRepository msCcRepo;
    private final MicroSpecialtyRepository msRepo;
    private final CourseRepository courseRepo;

    public MicroSpecialtyMaterializationServiceImpl(
            MicroSpecialtyProposalRepository proposalRepo,
            ProposalCourseRepository proposalCourseRepo,
            ProposalChapterRepository proposalChapterRepo,
            ChapterTeacherAssignmentRepository assignmentRepo,
            MicroSpecialtyCourseChapterRepository msCcRepo,
            MicroSpecialtyRepository msRepo,
            CourseRepository courseRepo) {
        this.proposalRepo = proposalRepo;
        this.proposalCourseRepo = proposalCourseRepo;
        this.proposalChapterRepo = proposalChapterRepo;
        this.assignmentRepo = assignmentRepo;
        this.msCcRepo = msCcRepo;
        this.msRepo = msRepo;
        this.courseRepo = courseRepo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void materialize(Long proposalId) {
        MicroSpecialtyProposal proposal = proposalRepo.selectById(proposalId);
        if (proposal == null) throw new BusinessException(ErrorCode.SA_NOT_FOUND);
        // 允许 PENDING_REVIEW（独立调用）或 APPROVED（从审批方法内调用）
        if (!"PENDING_REVIEW".equals(proposal.getStatus()) && !"APPROVED".equals(proposal.getStatus()))
            throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "仅待审批或已审批状态可物化");

        // 1. 查找关联的微专业
        // P1 修复: 加 status IN(DRAFT,RECRUITING) 限制,避免同名+同教师的历史关闭微专业被误关联
        MicroSpecialty ms = msRepo.selectOne(new LambdaQueryWrapper<MicroSpecialty>()
                .eq(MicroSpecialty::getTitle, proposal.getTitle())
                .eq(MicroSpecialty::getLeadTeacherId, proposal.getProposerId())
                .in(MicroSpecialty::getStatus, "DRAFT", "RECRUITING")
                .orderByDesc(MicroSpecialty::getCreatedAt)
                .last("LIMIT 1"));
        if (ms == null) {
            log.warn("materialize: no MicroSpecialty found for proposal {}, skipping", proposalId);
            return;
        }
        // P1-I-2 修复: 悲观锁锁定 micro_specialty 行，防止并发重复物化
        ms = msRepo.selectForUpdate(ms.getId());

        // 2. 物化每门课程
        List<ProposalCourse> pCourses = proposalCourseRepo.selectList(
                new LambdaQueryWrapper<ProposalCourse>().eq(ProposalCourse::getProposalId, proposalId));
        Map<Long, Course> createdCourses = new HashMap<>();

        for (ProposalCourse pc : pCourses) {
            // 查找该课程下的所有章节
            List<ProposalChapter> pChapters = proposalChapterRepo.selectList(
                    new LambdaQueryWrapper<ProposalChapter>().eq(ProposalChapter::getCourseId, pc.getId()));

            for (ProposalChapter pch : pChapters) {
                // 检查是否有已接受的教师分配
                List<ChapterTeacherAssignment> assignments = assignmentRepo.selectList(
                        new LambdaQueryWrapper<ChapterTeacherAssignment>()
                                .eq(ChapterTeacherAssignment::getChapterId, pch.getId())
                                .eq(ChapterTeacherAssignment::getAcceptStatus, "ACCEPTED")
                                .eq(ChapterTeacherAssignment::getDeletedAt, null));

                for (ChapterTeacherAssignment assign : assignments) {
                    Course targetCourse = null;
                    Long targetChapterId = null;
                    String sourceLabel = "from_proposal";

                    if ("existing".equals(assign.getSource()) && assign.getSourceChapterId() != null) {
                        // Phase 3 教师选了已有章节: 直接引用
                        // P0 修复: sourceChapterId 是章节 ID,不是课程 ID。
                        // 用 assign.getSourceCourseId() 正确查找课程
                        targetChapterId = assign.getSourceChapterId();
                        sourceLabel = "existing";
                        targetCourse = assign.getSourceCourseId() != null
                                ? courseRepo.selectById(assign.getSourceCourseId())
                                : null;
                    } else {
                        // 教师选了新建或 TBD: 创建新课程
                        if (!createdCourses.containsKey(pc.getId())) {
                            Course newCourse = new Course();
                            newCourse.setTitle(pc.getCourseName());
                            // P1-I-2 修复: frozen_price 可能为 NULL,兜底为 BigDecimal.ZERO
                            if (assign.getFrozenPrice() != null) {
                                newCourse.setPrice(assign.getFrozenPrice());
                            } else {
                                newCourse.setPrice(java.math.BigDecimal.ZERO);
                                log.warn("materialize: frozen_price is NULL for assignment chapterId={}, teacherId={}, defaulting to 0",
                                        assign.getChapterId(), assign.getTeacherId());
                            }
                            newCourse.setTeacherId(assign.getTeacherId());
                            newCourse.setStatus(CourseStatus.PENDING_REVIEW.getCode()); // 待审核 → 待发布
                            newCourse.setCreatedAt(LocalDateTime.now());
                            newCourse.setUpdatedAt(LocalDateTime.now());
                            courseRepo.insert(newCourse);
                            createdCourses.put(pc.getId(), newCourse);
                            sourceLabel = "new";
                            log.info("materialize: created course {} (id={}) from proposal chapter {}",
                                    pc.getCourseName(), newCourse.getId(), pch.getTitle());
                        }
                        targetCourse = createdCourses.get(pc.getId());
                        targetChapterId = pch.getId(); // 暂用 proposal_chapter.id
                    }

                    if (targetCourse != null && targetChapterId != null) {
                        MicroSpecialtyCourseChapter msCc = new MicroSpecialtyCourseChapter();
                        msCc.setMicroSpecialtyId(ms.getId());
                        msCc.setCourseId(targetCourse.getId());
                        msCc.setChapterId(targetChapterId);
                        msCc.setSource(sourceLabel);
                        msCc.setProposalChapterId(pch.getId());
                        msCcRepo.insert(msCc);

                        // 更新教师关联 (确认在 micro_specialty_courses 表中的关联)
                        // 简单记录存在即可
                    }
                }
            }
        }

        // 3. 更新 proposal 状态（确保状态正确）
        proposal.setStatus("APPROVED");
        proposal.setUpdatedAt(LocalDateTime.now());
        proposalRepo.updateById(proposal);

        log.info("materialize: proposal {} materialized successfully, ms_id={}", proposalId, ms.getId());
    }
}
