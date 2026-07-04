package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.dto.storage.*;
import com.microcourse.entity.MicroSpecialtyProposal;
import com.microcourse.entity.proposal.*;
import com.microcourse.repository.*;
import com.microcourse.service.StorageApplicationCudService;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Phase 15: 微专业申请表 CUD Service 实现
 *
 * <p>从 {@link StorageApplicationServiceImpl} 中抽取的写操作辅助方法，
 * 职责：请求到主表的字段映射、子表先删后插。</p>
 */
@Service
public class StorageApplicationCudServiceImpl implements StorageApplicationCudService {

    private static final Logger log = LoggerFactory.getLogger(StorageApplicationCudServiceImpl.class);

    private final ProposalCourseRepository courseRepository;
    private final ProposalChapterRepository chapterRepository;
    private final ProposalLeadCourseRepository leadCourseRepository;
    private final ProposalTeamMemberRepository teamMemberRepository;
    private final ProposalSignatureRepository signatureRepository;
    private final ProposalSharedUnitRepository sharedUnitRepository;
    private final ChapterTeacherAssignmentRepository assignmentRepository;
    private final SqlSessionFactory sqlSessionFactory;

    public StorageApplicationCudServiceImpl(
            ProposalCourseRepository courseRepository,
            ProposalChapterRepository chapterRepository,
            ProposalLeadCourseRepository leadCourseRepository,
            ProposalTeamMemberRepository teamMemberRepository,
            ProposalSignatureRepository signatureRepository,
            ProposalSharedUnitRepository sharedUnitRepository,
            ChapterTeacherAssignmentRepository assignmentRepository,
            SqlSessionFactory sqlSessionFactory) {
        this.courseRepository = courseRepository;
        this.chapterRepository = chapterRepository;
        this.leadCourseRepository = leadCourseRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.signatureRepository = signatureRepository;
        this.sharedUnitRepository = sharedUnitRepository;
        this.assignmentRepository = assignmentRepository;
        this.sqlSessionFactory = sqlSessionFactory;
    }

    // ================================================================
    // applyRequestToProposal
    // ================================================================
    @Override
    public void applyRequestToProposal(MicroSpecialtyProposal proposal, StorageApplicationSaveRequest request) {
        if (request.getTitle() != null) {
            proposal.setTitle(request.getTitle());
        }
        // P0-4 修复：持久化微专业名称
        if (request.getMicroSpecialtyName() != null) {
            proposal.setMicroSpecialtyName(request.getMicroSpecialtyName());
        }
        if (request.getLeadName() != null) {
            proposal.setLeadName(request.getLeadName());
        }
        if (request.getContactPhone() != null) {
            proposal.setContactPhone(request.getContactPhone());
        }
        // P1-C-1 修复：解析请求中的日期字符串，而非设为 now()
        if (request.getApplyDate() != null && !request.getApplyDate().isEmpty()) {
            try {
                proposal.setApplyDate(parseDate(request.getApplyDate()));
            } catch (Exception e) {
                log.warn("applyDate parse failed: {}", request.getApplyDate(), e);
            }
        }
        if (request.getType() != null) {
            proposal.setType(request.getType());
        }
        if (request.getTargetAudience() != null) {
            proposal.setTargetAudience(request.getTargetAudience());
        }
        if (request.getTargetDisciplines() != null) {
            proposal.setTargetDisciplines(request.getTargetDisciplines());
        }
        if (request.getTotalCredits() != null) {
            proposal.setTotalCredits(request.getTotalCredits());
        }
        if (request.getCourseCount() != null) {
            proposal.setCourseCount(request.getCourseCount());
        }
        if (request.getCoBuildUniversities() != null) {
            proposal.setCoBuildUniversities(request.getCoBuildUniversities());
        }
        if (request.getPlannedShareUniversities() != null) {
            proposal.setPlannedShareUniversities(request.getPlannedShareUniversities());
        }
        if (request.getEnrollmentQuota() != null) {
            proposal.setEnrollmentQuota(request.getEnrollmentQuota());
        }
        if (request.getClassSize() != null) {
            proposal.setClassSize(request.getClassSize());
        }
        // P1-C-1 修复：解析请求中的日期字符串，而非设为 now()
        if (request.getStartDate() != null && !request.getStartDate().isEmpty()) {
            try {
                proposal.setStartDate(parseDate(request.getStartDate()));
            } catch (Exception e) {
                log.warn("startDate parse failed: {}", request.getStartDate(), e);
            }
        }
        if (request.getDuration() != null) {
            proposal.setDuration(request.getDuration());
        }
        if (request.getIsIndustryAcademic() != null) {
            proposal.setIsIndustryAcademic(request.getIsIndustryAcademic());
        }
        if (request.getIndustryPartners() != null) {
            proposal.setIndustryPartners(request.getIndustryPartners());
        }
        if (request.getIntroduction() != null) {
            proposal.setIntroduction(request.getIntroduction());
        }
        if (request.getMarketDemandAnalysis() != null) {
            proposal.setMarketDemandAnalysis(request.getMarketDemandAnalysis());
        }
        if (request.getSpecialtyOverview() != null) {
            proposal.setSpecialtyOverview(request.getSpecialtyOverview());
        }
        if (request.getCurriculumDesign() != null) {
            proposal.setCurriculumDesign(request.getCurriculumDesign());
        }
        if (request.getConstructionGuarantee() != null) {
            proposal.setConstructionGuarantee(request.getConstructionGuarantee());
        }
        if (request.getLeadTitle() != null) {
            proposal.setLeadTitle(request.getLeadTitle());
        }
        if (request.getLeadPosition() != null) {
            proposal.setLeadPosition(request.getLeadPosition());
        }
        if (request.getLeadPhone() != null) {
            proposal.setLeadPhone(request.getLeadPhone());
        }
        if (request.getLeadResearchDirection() != null) {
            proposal.setLeadResearchDirection(request.getLeadResearchDirection());
        }
        if (request.getLeadMainTasks() != null) {
            proposal.setLeadMainTasks(request.getLeadMainTasks());
        }
        // A1 修复：持久化申报院系 ID
        if (request.getOfferDepartmentId() != null) {
            proposal.setOfferDepartmentId(request.getOfferDepartmentId());
        }
    }

    // ================================================================
    // replaceSubTables
    // ================================================================
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void replaceSubTables(Long proposalId, StorageApplicationSaveRequest request, boolean includeSharedUnits) {
        // A6 修复：运行为确保处于活跃事务中
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("replaceSubTables 必须在事务上下文中调用");
        }

        // courses — A2: 仅当 courses 数组非 null 时更新 (R-002: 批量插入)
        if (request.getCourses() != null) {
            courseRepository.delete(new LambdaQueryWrapper<ProposalCourse>()
                    .eq(ProposalCourse::getProposalId, proposalId));
            int sortOrder = 0;
            List<ProposalCourse> entities = new ArrayList<>();
            for (ProposalCourseItem item : request.getCourses()) {
                ProposalCourse entity = new ProposalCourse();
                entity.setProposalId(proposalId);
                entity.setModuleName(item.getModuleName());
                entity.setCourseName(item.getCourseName());
                entity.setHours(item.getHours());
                entity.setCredits(item.getCredits());
                entity.setSemester(item.getSemester());
                entity.setSortOrder(sortOrder++);
                entities.add(entity);
            }
            if (!entities.isEmpty()) {
                try (SqlSession sqlSession = sqlSessionFactory.openSession(ExecutorType.BATCH)) {
                    ProposalCourseRepository batchRepo = sqlSession.getMapper(ProposalCourseRepository.class);
                    for (ProposalCourse entity : entities) {
                        batchRepo.insert(entity);
                    }
                    sqlSession.flushStatements();  // 必须先 flush 让 course.id 可用
                }

                // Phase 1: 同步保存章节(嵌套在课程循环内,确保 course.id 可用)
                // P0-1 修复: 维护 oldId → newChapter 映射,DELETE+INSERT 后前端旧ID不再有效
                // P1-C-1 修复: 同时维护 oldToNewCourseIdMap 用于 courseId 回退查询
                List<ProposalChapter> allChapters = new ArrayList<>();
                Map<Long, ProposalChapter> oldIdToNewChapterMap = new HashMap<>();
                Map<Long, Long> oldToNewCourseIdMap = new HashMap<>();
                for (int i = 0; i < request.getCourses().size(); i++) {
                    ProposalCourseItem item = request.getCourses().get(i);
                    ProposalCourse courseEntity = entities.get(i);
                    // 记录旧→新课程序号映射
                    if (item.getId() != null) {
                        oldToNewCourseIdMap.put(item.getId(), courseEntity.getId());
                    }
                    if (item.getChapters() != null && !item.getChapters().isEmpty()) {
                        int chapterSort = 0;
                        for (ProposalChapterItem chItem : item.getChapters()) {
                            ProposalChapter ch = new ProposalChapter();
                            ch.setProposalId(proposalId);
                            ch.setCourseId(courseEntity.getId());
                            ch.setTitle(chItem.getTitle());
                            ch.setDescription(chItem.getDescription());
                            ch.setHours(chItem.getHours());
                            ch.setSortOrder(chapterSort++);
                            allChapters.add(ch);
                            if (chItem.getId() != null) {
                                oldIdToNewChapterMap.put(chItem.getId(), ch);
                            }
                        }
                    }
                }
                if (!allChapters.isEmpty()) {
                    try (SqlSession sqlSession = sqlSessionFactory.openSession(ExecutorType.BATCH)) {
                        ProposalChapterRepository chRepo = sqlSession.getMapper(ProposalChapterRepository.class);
                        for (ProposalChapter ch : allChapters) {
                            chRepo.insert(ch);
                        }
                        sqlSession.commit();
                    }
                }

                // Phase 2: 章节-教师分配同步
                // P0-1 修复: 用 oldIdToNewChapterMap 获取新章节 ID,解决 DELETE+INSERT 后
                // 前端旧 ID 导致 FK 约束 violation
                // P1-C-1 修复: 当映射找不到时,用 resolvedCourseId 在 allChapters 中回退匹配,
                // 仍然找不到则跳过该条 assignment 并记 warning,避免用过期 ID 插入 FK 崩溃
                if (request.getChapterAssignments() != null && !request.getChapterAssignments().isEmpty()) {
                    assignmentRepository.delete(new LambdaQueryWrapper<ChapterTeacherAssignment>()
                            .eq(ChapterTeacherAssignment::getProposalId, proposalId));
                    try (SqlSession sqlSession = sqlSessionFactory.openSession(ExecutorType.BATCH)) {
                        ChapterTeacherAssignmentRepository batchRepo = sqlSession.getMapper(ChapterTeacherAssignmentRepository.class);
                        for (ChapterAssignmentItem assignItem : request.getChapterAssignments()) {
                            Long oldChapterId = assignItem.getChapterId();
                            ProposalChapter mappedCh = oldIdToNewChapterMap.get(oldChapterId);

                            // 回退查找: 当直接 ID 映射失败时(如章节刚创建无旧 ID),
                            // 解析新课程序号后在 allChapters 中按课程匹配第一个章节
                            if (mappedCh == null) {
                                Long resolvedCourseId = oldToNewCourseIdMap.getOrDefault(
                                        assignItem.getCourseId(), assignItem.getCourseId());
                                for (ProposalChapter fallbackCh : allChapters) {
                                    if (Objects.equals(fallbackCh.getCourseId(), resolvedCourseId)) {
                                        mappedCh = fallbackCh;
                                        break;
                                    }
                                }
                            }

                            if (mappedCh == null) {
                                // P0-1 修复: 找不到匹配章节时跳过,避免用过期 ID 触发 FK 约束崩溃
                                log.warn("replaceSubTables: 跳过 assignment(courseId={}, chapterId={}, teacherId={}) — 未找到匹配章节",
                                        assignItem.getCourseId(), oldChapterId, assignItem.getTeacherId());
                                continue;
                            }

                            Long newChapterId = mappedCh.getId();
                            Long newCourseId = mappedCh.getCourseId();
                            ChapterTeacherAssignment entity = new ChapterTeacherAssignment();
                            entity.setProposalId(proposalId);
                            entity.setCourseId(newCourseId);
                            entity.setChapterId(newChapterId);
                            entity.setTeacherId(assignItem.getTeacherId());
                            entity.setSource("TBD");
                            entity.setAcceptStatus("PENDING");
                            batchRepo.insert(entity);
                        }
                        sqlSession.commit();
                    }
                }
            }
        }

        // leadCourses — A2: 仅当 leadCourses 数组非 null 时更新 (R-002: 批量插入)
        if (request.getLeadCourses() != null) {
            leadCourseRepository.delete(new LambdaQueryWrapper<ProposalLeadCourse>()
                    .eq(ProposalLeadCourse::getProposalId, proposalId));
            int sortOrder = 0;
            List<ProposalLeadCourse> entities = new ArrayList<>();
            for (ProposalLeadCourseItem item : request.getLeadCourses()) {
                ProposalLeadCourse entity = new ProposalLeadCourse();
                entity.setProposalId(proposalId);
                entity.setCourseName(item.getCourseName());
                entity.setCredits(item.getCredits());
                entity.setHours(item.getHours());
                entity.setSortOrder(sortOrder++);
                entities.add(entity);
            }
            if (!entities.isEmpty()) {
                try (SqlSession sqlSession = sqlSessionFactory.openSession(ExecutorType.BATCH)) {
                    ProposalLeadCourseRepository batchRepo = sqlSession.getMapper(ProposalLeadCourseRepository.class);
                    for (ProposalLeadCourse entity : entities) {
                        batchRepo.insert(entity);
                    }
                    sqlSession.commit();
                }
            }
        }

        // teamMembers — A2: 仅当 teamMembers 数组非 null 时更新 (R-002: 批量插入)
        if (request.getTeamMembers() != null) {
            teamMemberRepository.delete(new LambdaQueryWrapper<ProposalTeamMember>()
                    .eq(ProposalTeamMember::getProposalId, proposalId));
            List<ProposalTeamMember> entities = new ArrayList<>();
            for (ProposalTeamMemberItem item : request.getTeamMembers()) {
                ProposalTeamMember entity = new ProposalTeamMember();
                entity.setProposalId(proposalId);
                entity.setMemberType(item.getMemberType() != null ? item.getMemberType() : "MEMBER");
                entity.setSeq(item.getSeq());
                entity.setName(item.getName());
                entity.setAge(item.getAge());
                entity.setTitle(item.getTitle());
                entity.setOrganization(item.getOrganization());
                entity.setProfession(item.getProfession());
                entity.setTaughtCourses(item.getTaughtCourses());
                entity.setPlannedCourses(item.getPlannedCourses());
                entities.add(entity);
            }
            if (!entities.isEmpty()) {
                try (SqlSession sqlSession = sqlSessionFactory.openSession(ExecutorType.BATCH)) {
                    ProposalTeamMemberRepository batchRepo = sqlSession.getMapper(ProposalTeamMemberRepository.class);
                    for (ProposalTeamMember entity : entities) {
                        batchRepo.insert(entity);
                    }
                    sqlSession.commit();
                }
            }
        }

        // signatures — A2: 仅当 signatures 数组非 null 时更新（不处理 SHARED_UNIT 级别）(R-002: 批量插入)
        if (request.getSignatures() != null) {
            signatureRepository.delete(new LambdaQueryWrapper<ProposalSignature>()
                    .eq(ProposalSignature::getProposalId, proposalId)
                    .ne(ProposalSignature::getSignLevel, "SHARED_UNIT"));
            int sigSeq = 0;
            List<ProposalSignature> entities = new ArrayList<>();
            for (ProposalSignatureItem item : request.getSignatures()) {
                ProposalSignature entity = new ProposalSignature();
                entity.setProposalId(proposalId);
                entity.setSignLevel(item.getSignLevel());
                entity.setUnitSeq(sigSeq++);
                entity.setOpinionText(item.getOpinionText());
                entity.setSignatureType(item.getSignatureType());
                entity.setSignatureText(item.getSignatureText());
                entity.setSignatureImageUrl(item.getSignatureImageUrl());
                entity.setSealImageUrl(item.getSealImageUrl());
                entity.setSignDate(parseDate(item.getSignDate()));
                entity.setRemark(item.getRemark());
                entities.add(entity);
            }
            if (!entities.isEmpty()) {
                try (SqlSession sqlSession = sqlSessionFactory.openSession(ExecutorType.BATCH)) {
                    ProposalSignatureRepository batchRepo = sqlSession.getMapper(ProposalSignatureRepository.class);
                    for (ProposalSignature entity : entities) {
                        batchRepo.insert(entity);
                    }
                    sqlSession.commit();
                }
            }
        }

        // sharedUnits — 仅 full save 时处理，且仅当 sharedUnits 数组非 null 时更新 (R-002: 批量插入)
        if (includeSharedUnits && request.getSharedUnits() != null) {
            sharedUnitRepository.delete(new LambdaQueryWrapper<ProposalSharedUnit>()
                    .eq(ProposalSharedUnit::getProposalId, proposalId));
            // 先清除旧的 SHARED_UNIT 级别签字，再重新插入
            signatureRepository.delete(new LambdaQueryWrapper<ProposalSignature>()
                    .eq(ProposalSignature::getProposalId, proposalId)
                    .eq(ProposalSignature::getSignLevel, "SHARED_UNIT"));
            int sortOrder = 0;
            List<ProposalSharedUnit> unitEntities = new ArrayList<>();
            List<ProposalSignature> sigEntities = new ArrayList<>();
            for (ProposalSharedUnitItem item : request.getSharedUnits()) {
                ProposalSharedUnit entity = new ProposalSharedUnit();
                entity.setProposalId(proposalId);
                entity.setUnitName(item.getUnitName());
                entity.setUnitType(item.getUnitType());
                entity.setSortOrder(sortOrder);
                unitEntities.add(entity);

                // 同步共享单位签字数据到 proposal_signatures 表
                ProposalSignature sig = new ProposalSignature();
                sig.setProposalId(proposalId);
                sig.setSignLevel("SHARED_UNIT");
                sig.setUnitSeq(sortOrder);
                sig.setOpinionText(item.getOpinionText());
                if (item.getSignature() != null) {
                    sig.setSignatureType(item.getSignature().getType());
                    sig.setSignatureText(item.getSignature().getText());
                    sig.setSignatureImageUrl(item.getSignature().getImageUrl());
                }
                if (item.getSeal() != null) {
                    sig.setSealImageUrl(item.getSeal().getImageUrl());
                }
                sig.setSignDate(item.getSignDate() != null && !item.getSignDate().isEmpty()
                        ? parseDate(item.getSignDate()) : null);
                sig.setRemark(item.getRemark());
                sigEntities.add(sig);

                sortOrder++;
            }
            // Batch insert shared units
            if (!unitEntities.isEmpty()) {
                try (SqlSession sqlSession = sqlSessionFactory.openSession(ExecutorType.BATCH)) {
                    ProposalSharedUnitRepository batchRepo = sqlSession.getMapper(ProposalSharedUnitRepository.class);
                    for (ProposalSharedUnit entity : unitEntities) {
                        batchRepo.insert(entity);
                    }
                    sqlSession.commit();
                }
            }
            // Batch insert shared unit signatures
            if (!sigEntities.isEmpty()) {
                try (SqlSession sqlSession = sqlSessionFactory.openSession(ExecutorType.BATCH)) {
                    ProposalSignatureRepository batchRepo = sqlSession.getMapper(ProposalSignatureRepository.class);
                    for (ProposalSignature entity : sigEntities) {
                        batchRepo.insert(entity);
                    }
                    sqlSession.commit();
                }
            }
        }
    }

    // ================================================================
    // parseDate
    // ================================================================

    /**
     * 解析前端传来的日期字符串，支持多种格式。
     * A3 修复：解析失败时打印 ERROR 级别日志（含实际输入），不再静默忽略。
     */
    private static LocalDateTime parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        String trimmed = dateStr.trim();
        try {
            // yyyy.M 格式（如 "2025.9"）
            if (trimmed.matches("\\d{4}\\.\\d{1,2}")) {
                java.time.YearMonth ym = java.time.YearMonth.parse(trimmed,
                        java.time.format.DateTimeFormatter.ofPattern("yyyy.M"));
                return ym.atDay(1).atStartOfDay();
            }
            // yyyy.M.d 格式（如 "2025.9.15"）
            if (trimmed.matches("\\d{4}\\.\\d{1,2}\\.\\d{1,2}")) {
                return LocalDateTime.parse(trimmed + " 00:00:00",
                        java.time.format.DateTimeFormatter.ofPattern("yyyy.M.d HH:mm:ss"));
            }
            // yyyy-MM 格式（如 "2025-09"）
            if (trimmed.matches("\\d{4}-\\d{1,2}") && !trimmed.contains("T") && !trimmed.contains(" ")) {
                java.time.YearMonth ym = java.time.YearMonth.parse(trimmed,
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
                return ym.atDay(1).atStartOfDay();
            }
            // ISO 格式 / yyyy-MM-dd / yyyy-MM-dd'T'HH:mm:ss / yyyy-MM-dd HH:mm:ss
            return LocalDateTime.parse(trimmed.replace(" ", "T"),
                    java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception e) {
            String redacted = dateStr.length() > 20 ? dateStr.substring(0, 10) + "..." : dateStr;
            log.error("日期解析失败: input='{}' (len={}), error={}", redacted, dateStr.length(), e.getMessage());
            return null;
        }
    }
}
