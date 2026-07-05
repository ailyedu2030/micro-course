package com.microcourse.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.microSpecialty.MicroSpecialtyCourseVO;
import com.microcourse.dto.microSpecialty.MicroSpecialtyDetailVO;
import com.microcourse.dto.microSpecialty.MicroSpecialtySquareVO;
import com.microcourse.dto.microSpecialty.MicroSpecialtyStatsVO;
import com.microcourse.dto.microSpecialty.MicroSpecialtyTeacherVO;
import com.microcourse.dto.microSpecialty.MicroSpecialtyVO;
import com.microcourse.entity.Course;
import com.microcourse.entity.Department;
import com.microcourse.entity.MicroSpecialty;
import com.microcourse.entity.MicroSpecialtyCourse;
import com.microcourse.entity.MicroSpecialtyEnrollment;
import com.microcourse.entity.MicroSpecialtyTeacher;
import com.microcourse.entity.User;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.DepartmentRepository;
import com.microcourse.repository.MicroSpecialtyCourseRepository;
import com.microcourse.repository.MicroSpecialtyEnrollmentRepository;
import com.microcourse.repository.MicroSpecialtyRepository;
import com.microcourse.repository.MicroSpecialtyTeacherRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.AdminSettingService;
import com.microcourse.service.MicroSpecialtyQualityScoreService;
import com.microcourse.service.MicroSpecialtyQueryService;
import com.microcourse.util.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
@Service
public class MicroSpecialtyQueryServiceImpl implements MicroSpecialtyQueryService {
    private static final Logger log = LoggerFactory.getLogger(MicroSpecialtyQueryServiceImpl.class);
    private final MicroSpecialtyRepository msRepository;
    private final MicroSpecialtyCourseRepository msCourseRepository;
    private final MicroSpecialtyTeacherRepository msTeacherRepository;
    private final MicroSpecialtyEnrollmentRepository msEnrollmentRepository;
    private final UserRepository userRepository;
    private final MicroSpecialtyQualityScoreService qualityScoreService;
    private final AdminSettingService adminSettingService;
    private final DepartmentRepository departmentRepository;
    private final CourseRepository courseRepository;
    public MicroSpecialtyQueryServiceImpl(MicroSpecialtyRepository msRepository,
                                          MicroSpecialtyCourseRepository msCourseRepository,
                                          MicroSpecialtyTeacherRepository msTeacherRepository,
                                          MicroSpecialtyEnrollmentRepository msEnrollmentRepository,
                                          UserRepository userRepository,
                                          MicroSpecialtyQualityScoreService qualityScoreService,
                                          AdminSettingService adminSettingService,
                                          DepartmentRepository departmentRepository,
                                          CourseRepository courseRepository) {
        this.msRepository = msRepository;
        this.msCourseRepository = msCourseRepository;
        this.msTeacherRepository = msTeacherRepository;
        this.msEnrollmentRepository = msEnrollmentRepository;
        this.userRepository = userRepository;
        this.qualityScoreService = qualityScoreService;
        this.adminSettingService = adminSettingService;
        this.departmentRepository = departmentRepository;
        this.courseRepository = courseRepository;
    }
    // ====== 查询 ======
    @Override
    public PageResult<MicroSpecialtyVO> page(int page, int size, Map<String, Object> params) {
        LambdaQueryWrapper<MicroSpecialty> wrapper = new LambdaQueryWrapper<>();
        String keyword = params != null ? (String) params.get("keyword") : null;
        String status = params != null ? (String) params.get("status") : null;
        // 学生只能看 RECRUITING 状态的微专业
        if (!SecurityUtil.isAdminOrAcademic()) {
            wrapper.eq(MicroSpecialty::getStatus, "RECRUITING");
        } else if (status != null && !status.isEmpty()) {
            wrapper.eq(MicroSpecialty::getStatus, status);
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(MicroSpecialty::getTitle, keyword);
        }
        // Featured / gold featured filters
        if (params != null && params.containsKey("featuredStatus")) {
            wrapper.eq(MicroSpecialty::getFeaturedStatus, params.get("featuredStatus").toString());
        }
        if (params != null && params.containsKey("isGoldFeatured")) {
            boolean isGold = Boolean.parseBoolean(params.get("isGoldFeatured").toString());
            wrapper.eq(MicroSpecialty::getIsGoldFeatured, isGold);
        }
        if (params != null && params.containsKey("featured") && Boolean.parseBoolean(params.get("featured").toString())) {
            wrapper.ne(MicroSpecialty::getFeaturedStatus, "NONE");
        }
        // Teacher role filter
        Long teacherId = SecurityUtil.getCurrentUserId();
        if (teacherId != null && params != null && params.containsKey("role")) {
            String roleFilter = (String) params.get("role");
            if ("leading".equals(roleFilter)) {
                wrapper.apply("EXISTS (SELECT 1 FROM micro_specialty_teachers WHERE micro_specialty_id = micro_specialties.id AND teacher_id = {0} AND role = 'LEAD')", teacherId);
            } else if ("participating".equals(roleFilter)) {
                wrapper.apply("EXISTS (SELECT 1 FROM micro_specialty_teachers WHERE micro_specialty_id = micro_specialties.id AND teacher_id = {0} AND role IN ('LEAD','MEMBER','ASSISTANT'))", teacherId);
            }
        }
        wrapper.orderByDesc(MicroSpecialty::getCreatedAt);
        IPage<MicroSpecialty> ipage = msRepository.selectPage(new Page<>(page + 1, size), wrapper);
        List<MicroSpecialty> records = ipage.getRecords();
        List<MicroSpecialtyVO> vos = new java.util.ArrayList<>(records.size());
        // P2-7: 批量预加载 N+1 → 用 IN 查询一次取完
        java.util.Set<Long> deptIds = new java.util.HashSet<>();
        java.util.Set<Long> teacherIds = new java.util.HashSet<>();
        java.util.Set<Long> creatorIds = new java.util.HashSet<>();
        for (MicroSpecialty ms : records) {
            if (ms.getOfferDepartmentId() != null) deptIds.add(ms.getOfferDepartmentId());
            if (ms.getLeadTeacherId() != null) teacherIds.add(ms.getLeadTeacherId());
            if (ms.getCreatorId() != null) creatorIds.add(ms.getCreatorId());
        }
        java.util.Map<Long, String> deptNameMap = new java.util.HashMap<>();
        if (!deptIds.isEmpty()) {
            departmentRepository.selectBatchIds(deptIds).forEach(d -> deptNameMap.put(d.getId(), d.getName()));
        }
        java.util.Map<Long, String> teacherNameMap = new java.util.HashMap<>();
        if (!teacherIds.isEmpty()) {
            userRepository.selectBatchIds(teacherIds).forEach(u -> teacherNameMap.put(u.getId(), u.getRealName()));
        }
        java.util.Map<Long, String> creatorNameMap = new java.util.HashMap<>();
        if (!creatorIds.isEmpty()) {
            userRepository.selectBatchIds(creatorIds).forEach(u -> creatorNameMap.put(u.getId(), u.getRealName()));
        }
        // P1-C-4: 批量预计算统计字段
        java.util.Map<Long, Integer> courseCountMapBatch = new java.util.HashMap<>();
        java.util.Map<Long, Integer> pendingEnrollCountMapBatch = new java.util.HashMap<>();
        java.util.Map<Long, Integer> totalEnrollmentsMapBatch = new java.util.HashMap<>();
        java.util.Map<Long, String> roleMapBatch = new java.util.HashMap<>();
        if (!records.isEmpty()) {
            java.util.List<Long> msIds = records.stream().map(MicroSpecialty::getId).collect(java.util.stream.Collectors.toList());
            // 课程数
            msCourseRepository.selectList(new LambdaQueryWrapper<MicroSpecialtyCourse>()
                            .in(MicroSpecialtyCourse::getMicroSpecialtyId, msIds))
                    .forEach(mc -> courseCountMapBatch.merge(mc.getMicroSpecialtyId(), 1, Integer::sum));
            // 待审报名数
            msEnrollmentRepository.selectList(new LambdaQueryWrapper<MicroSpecialtyEnrollment>()
                            .in(MicroSpecialtyEnrollment::getMicroSpecialtyId, msIds)
                            .eq(MicroSpecialtyEnrollment::getStatus, "PENDING"))
                    .forEach(e -> pendingEnrollCountMapBatch.merge(e.getMicroSpecialtyId(), 1, Integer::sum));
            // 总报名数
            msEnrollmentRepository.selectList(new LambdaQueryWrapper<MicroSpecialtyEnrollment>()
                            .in(MicroSpecialtyEnrollment::getMicroSpecialtyId, msIds))
                    .forEach(e -> totalEnrollmentsMapBatch.merge(e.getMicroSpecialtyId(), 1, Integer::sum));
            // 当前用户的角色
            Long currentUserId = SecurityUtil.getCurrentUserId();
            if (currentUserId != null) {
                msTeacherRepository.selectList(new LambdaQueryWrapper<MicroSpecialtyTeacher>()
                                .in(MicroSpecialtyTeacher::getMicroSpecialtyId, msIds)
                                .eq(MicroSpecialtyTeacher::getTeacherId, currentUserId))
                        .forEach(t -> roleMapBatch.put(t.getMicroSpecialtyId(), t.getRole()));
            }
        }
        for (MicroSpecialty ms : records) {
            MicroSpecialtyVO vo = new MicroSpecialtyVO();
            // P1-C-4 修复: 传所有预加载 map,避免 copyToVO 内任何 selectById
            copyToVO(ms, vo, deptNameMap, teacherNameMap, creatorNameMap,
                    courseCountMapBatch, pendingEnrollCountMapBatch,
                    totalEnrollmentsMapBatch, roleMapBatch);
            vos.add(vo);
        }
        return PageResult.of(vos, ipage.getTotal(), page, size);
    }
    @Override
    public MicroSpecialtySquareVO getSquareData() {
        MicroSpecialtySquareVO result = new MicroSpecialtySquareVO();
        // 金标（最多 2 个）
        List<MicroSpecialty> goldList = msRepository.selectList(
                new LambdaQueryWrapper<MicroSpecialty>()
                        .eq(MicroSpecialty::getIsGoldFeatured, true)
                        .eq(MicroSpecialty::getStatus, "RECRUITING")
                        .isNull(MicroSpecialty::getDeletedAt)
                        .orderByDesc(MicroSpecialty::getGoldFeaturedAt)
                        .last("LIMIT 2"));
        // 置顶已审批
        List<MicroSpecialty> featuredList = msRepository.selectList(
                new LambdaQueryWrapper<MicroSpecialty>()
                        .eq(MicroSpecialty::getIsFeatured, true)
                        .eq(MicroSpecialty::getFeaturedStatus, "APPROVED")
                        .eq(MicroSpecialty::getStatus, "RECRUITING")
                        .isNull(MicroSpecialty::getDeletedAt)
                        .orderByAsc(MicroSpecialty::getFeaturedRank)
                        .orderByDesc(MicroSpecialty::getApprovedAt));
        // 普通招生中（排除已置顶的）— 修复 G1：按质量分降序排序（次按 approvedAt DESC）
        // P1I-032: 增加 LIMIT 1000 防止全量加载 OOM，质量分排序在内存中完成
        List<MicroSpecialty> recruitingList = msRepository.selectList(
                new LambdaQueryWrapper<MicroSpecialty>()
                        .eq(MicroSpecialty::getStatus, "RECRUITING")
                        .isNull(MicroSpecialty::getDeletedAt)
                        .and(w -> w.eq(MicroSpecialty::getIsFeatured, false)
                                .or().isNull(MicroSpecialty::getIsFeatured))
                        .and(w -> w.eq(MicroSpecialty::getIsGoldFeatured, false)
                                .or().isNull(MicroSpecialty::getIsGoldFeatured))
                        .orderByDesc(MicroSpecialty::getApprovedAt)
                        .last("LIMIT 1000"));
        // P1-C-3: 一次性批量加载所有关联数据，消除 N+1
        List<MicroSpecialty> allList = new ArrayList<>();
        allList.addAll(goldList);
        allList.addAll(featuredList);
        allList.addAll(recruitingList);
        List<Long> allIds = allList.stream().map(MicroSpecialty::getId).collect(Collectors.toList());
        java.util.Set<Long> allDeptIds = allList.stream()
                .map(MicroSpecialty::getOfferDepartmentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        java.util.Set<Long> allTeacherIds = allList.stream()
                .map(MicroSpecialty::getLeadTeacherId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        // 质量分批量计算
        Map<Long, BigDecimal> qualityScoreMap = qualityScoreService.calculateBatch(allIds);
        // 部门名称批量加载
        Map<Long, String> deptNameMap = new HashMap<>();
        if (!allDeptIds.isEmpty()) {
            departmentRepository.selectBatchIds(allDeptIds)
                    .forEach(d -> deptNameMap.put(d.getId(), d.getName()));
        }
        // 教师名称批量加载
        Map<Long, String> teacherNameMap = new HashMap<>();
        if (!allTeacherIds.isEmpty()) {
            userRepository.selectBatchIds(allTeacherIds)
                    .forEach(u -> teacherNameMap.put(u.getId(), u.getRealName()));
        }
        // 课程数批量计算
        Map<Long, Integer> courseCountMap = new HashMap<>();
        if (!allIds.isEmpty()) {
            msCourseRepository.selectList(new LambdaQueryWrapper<MicroSpecialtyCourse>()
                            .in(MicroSpecialtyCourse::getMicroSpecialtyId, allIds))
                    .forEach(mc -> courseCountMap.merge(mc.getMicroSpecialtyId(), 1, Integer::sum));
        }
        result.setGoldFeatured(goldList.stream()
                .map(ms -> toFeaturedVO(ms, qualityScoreMap, teacherNameMap, deptNameMap, courseCountMap))
                .collect(Collectors.toList()));
        result.setFeatured(featuredList.stream()
                .map(ms -> toFeaturedVO(ms, qualityScoreMap, teacherNameMap, deptNameMap, courseCountMap))
                .collect(Collectors.toList()));
        // G1: 用质量分降序重排（次按 approvedAt DESC）
        recruitingList = recruitingList.stream()
                .sorted((a, b) -> {
                    BigDecimal sa = qualityScoreMap.getOrDefault(a.getId(), BigDecimal.ZERO);
                    BigDecimal sb = qualityScoreMap.getOrDefault(b.getId(), BigDecimal.ZERO);
                    int cmp = sb.compareTo(sa); // 质量分 DESC
                    if (cmp != 0) return cmp;
                    // 次按 approvedAt DESC
                    LocalDateTime ta = a.getApprovedAt();
                    LocalDateTime tb = b.getApprovedAt();
                    if (ta == null && tb == null) return 0;
                    if (ta == null) return 1;
                    if (tb == null) return -1;
                    return tb.compareTo(ta);
                })
                .collect(Collectors.toList());
        result.setRecruiting(recruitingList.stream()
                .map(ms -> toFeaturedVO(ms, qualityScoreMap, teacherNameMap, deptNameMap, courseCountMap))
                .collect(Collectors.toList()));
        return result;
    }
    /**
     * P1-C-3: 使用批量预加载 map 的 toFeaturedVO，消除 N+1 selectById。
     */
    private MicroSpecialtySquareVO.FeaturedVO toFeaturedVO(MicroSpecialty ms,
                                                           Map<Long, BigDecimal> qualityScoreMap,
                                                           Map<Long, String> teacherNameMap,
                                                           Map<Long, String> deptNameMap,
                                                           Map<Long, Integer> courseCountMap) {
        MicroSpecialtySquareVO.FeaturedVO vo = new MicroSpecialtySquareVO.FeaturedVO();
        vo.setId(ms.getId());
        vo.setTitle(ms.getTitle());
        vo.setCoverUrl(ms.getCoverUrl());
        vo.setTotalCredits(ms.getTotalCredits());
        vo.setStudentCount(ms.getStudentCount());
        vo.setStatus(ms.getStatus());
        vo.setIsGoldFeatured(ms.getIsGoldFeatured());
        // 使用批量预加载的质量分
        vo.setQualityScore(qualityScoreMap.getOrDefault(ms.getId(), BigDecimal.ZERO));
        // G3: 7 天保护期内显示 NEW 角标
        vo.setIsNew(isNewlyCreated(ms));
        if (ms.getLeadTeacherId() != null) {
            String name = teacherNameMap.get(ms.getLeadTeacherId());
            vo.setLeadTeacherName(name != null ? name : "—");
        }
        if (ms.getOfferDepartmentId() != null) {
            String name = deptNameMap.get(ms.getOfferDepartmentId());
            if (name != null) vo.setDepartmentName(name);
        }
        vo.setCourseCount(courseCountMap.getOrDefault(ms.getId(), 0));
        return vo;
    }
    /**
     * G3: 7 天保护期判断。
     * 读取 admin_settings 中微专业 NEW 角标保护期配置 key="micro_specialty.new_protection_days"，
     * 若未配置或解析失败则使用默认值 7 天。
     */
    private boolean isNewlyCreated(MicroSpecialty ms) {
        if (ms.getApprovedAt() == null) return false;
        int protectionDays = 7; // 默认 7 天
        try {
            String setting = adminSettingService.getByKey("micro_specialty.new_protection_days");
            if (setting != null && !setting.isEmpty()) {
                protectionDays = Integer.parseInt(setting);
            }
        } catch (Exception e) {
            log.warn("读取新微专业保护期配置失败，使用默认值 7 天: {}", e.getMessage());
        }
        try {
            return ms.getApprovedAt().plusDays(protectionDays).isAfter(LocalDateTime.now());
        } catch (Exception e) {
            log.warn("判断新微专业保护期失败: {}", e.getMessage());
            return false;
        }
    }
    @Override
    public MicroSpecialtyDetailVO getDetail(Long id) {
        MicroSpecialty ms = msRepository.selectById(id);
        if (ms == null) throw new BusinessException(ErrorCode.MS_NOT_FOUND);
        // 非管理员/教务/创建者/负责人不可查看 DRAFT / CANCELLED
        String status = ms.getStatus();
        Long userId = SecurityUtil.getCurrentUserId();
        boolean isAdminOrAcademic = SecurityUtil.isAdminOrAcademic();
        boolean isCreator = ms.getCreatorId().equals(userId);
        if (("DRAFT".equals(status) || "CANCELLED".equals(status))
                && !isAdminOrAcademic && !isCreator && !isLeadOf(id, userId)) {
            throw new BusinessException(ErrorCode.MS_NOT_FOUND);
        }
        MicroSpecialtyDetailVO detail = new MicroSpecialtyDetailVO();
        // P1-C-12-08 fix: 消除 getDetail() 的 N+1
        Long msId = ms.getId();
        Map<Long, String> deptNameMap = new java.util.HashMap<>();
        if (ms.getOfferDepartmentId() != null) {
            Department d = departmentRepository.selectById(ms.getOfferDepartmentId());
            if (d != null) deptNameMap.put(d.getId(), d.getName());
        }
        Map<Long, String> teacherNameMap = new java.util.HashMap<>();
        if (ms.getLeadTeacherId() != null) {
            User lead = userRepository.selectById(ms.getLeadTeacherId());
            if (lead != null) teacherNameMap.put(lead.getId(), lead.getRealName());
        }
        Map<Long, String> creatorNameMap = new java.util.HashMap<>();
        if (ms.getCreatorId() != null) {
            User creator = userRepository.selectById(ms.getCreatorId());
            if (creator != null) creatorNameMap.put(creator.getId(), creator.getRealName());
        }
        Map<Long, Integer> courseCountMap = new java.util.HashMap<>();
        Map<Long, Integer> pendingEnrollCountMap = new java.util.HashMap<>();
        Map<Long, Integer> totalEnrollmentsMap = new java.util.HashMap<>();
        Map<Long, String> roleMap = new java.util.HashMap<>();
        Long courseCount = msCourseRepository.selectCount(
            new LambdaQueryWrapper<MicroSpecialtyCourse>()
                .eq(MicroSpecialtyCourse::getMicroSpecialtyId, msId));
        courseCountMap.put(msId, courseCount != null ? courseCount.intValue() : 0);
        Long pendingCount = msEnrollmentRepository.selectCount(
            new LambdaQueryWrapper<MicroSpecialtyEnrollment>()
                .eq(MicroSpecialtyEnrollment::getMicroSpecialtyId, msId)
                .eq(MicroSpecialtyEnrollment::getStatus, "PENDING"));
        pendingEnrollCountMap.put(msId, pendingCount != null ? pendingCount.intValue() : 0);
        Long totalCount = msEnrollmentRepository.selectCount(
            new LambdaQueryWrapper<MicroSpecialtyEnrollment>()
                .eq(MicroSpecialtyEnrollment::getMicroSpecialtyId, msId));
        totalEnrollmentsMap.put(msId, totalCount != null ? totalCount.intValue() : 0);
        if (userId != null) {
            MicroSpecialtyTeacher teacher = msTeacherRepository.selectOne(
                new LambdaQueryWrapper<MicroSpecialtyTeacher>()
                    .eq(MicroSpecialtyTeacher::getMicroSpecialtyId, msId)
                    .eq(MicroSpecialtyTeacher::getTeacherId, userId)
                    .eq(MicroSpecialtyTeacher::getInviteStatus, "ACTIVE"));
            if (teacher != null) roleMap.put(msId, teacher.getRole());
        }
        copyToVO(ms, detail, deptNameMap, teacherNameMap, creatorNameMap,
                courseCountMap, pendingEnrollCountMap, totalEnrollmentsMap, roleMap);
        // 课程编排
        List<MicroSpecialtyCourse> courses = msCourseRepository.selectList(
                new LambdaQueryWrapper<MicroSpecialtyCourse>()
                        .eq(MicroSpecialtyCourse::getMicroSpecialtyId, id)
                        .orderByAsc(MicroSpecialtyCourse::getSortOrder));
        detail.setCourses(toCourseVOBatch(courses));
        // 教师团队
        List<MicroSpecialtyTeacher> teachers = msTeacherRepository.selectList(
                new LambdaQueryWrapper<MicroSpecialtyTeacher>()
                        .eq(MicroSpecialtyTeacher::getMicroSpecialtyId, id)
                        .eq(MicroSpecialtyTeacher::getInviteStatus, "ACTIVE"));
        detail.setTeachers(teachers.stream().map(this::toTeacherVO).collect(Collectors.toList()));
        // 统计
        detail.setStats(buildStats(ms));
        return detail;
    }
    @Override
    public MicroSpecialtyStatsVO stats(Long id) {
        MicroSpecialty ms = msRepository.selectById(id);
        if (ms == null) throw new BusinessException(ErrorCode.MS_NOT_FOUND);
        return buildStats(ms);
    }
    private MicroSpecialtyStatsVO buildStats(MicroSpecialty ms) {
        MicroSpecialtyStatsVO vo = new MicroSpecialtyStatsVO();
        long totalCount = msEnrollmentRepository.selectCount(
                new LambdaQueryWrapper<MicroSpecialtyEnrollment>()
                        .eq(MicroSpecialtyEnrollment::getMicroSpecialtyId, ms.getId()));
        long completedCount = msEnrollmentRepository.selectCount(
                new LambdaQueryWrapper<MicroSpecialtyEnrollment>()
                        .eq(MicroSpecialtyEnrollment::getMicroSpecialtyId, ms.getId())
                        .eq(MicroSpecialtyEnrollment::getStatus, "COMPLETED"));
        long inProgress = msEnrollmentRepository.selectCount(
                new LambdaQueryWrapper<MicroSpecialtyEnrollment>()
                        .eq(MicroSpecialtyEnrollment::getMicroSpecialtyId, ms.getId())
                        .eq(MicroSpecialtyEnrollment::getStatus, "IN_PROGRESS"));
        long failed = msEnrollmentRepository.selectCount(
                new LambdaQueryWrapper<MicroSpecialtyEnrollment>()
                        .eq(MicroSpecialtyEnrollment::getMicroSpecialtyId, ms.getId())
                        .eq(MicroSpecialtyEnrollment::getStatus, "FAILED"));
        vo.setTotalEnrollments((int) totalCount);
        vo.setCompletedCount((int) completedCount);
        vo.setInProgressCount((int) inProgress);
        vo.setFailedCount((int) failed);
        long activeTotal = completedCount + inProgress + failed;
        if (ms.getMaxStudents() != null && ms.getMaxStudents() > 0) {
            vo.setEnrollmentRate(BigDecimal.valueOf(Math.min(ms.getStudentCount(), ms.getMaxStudents()))
                    .divide(BigDecimal.valueOf(ms.getMaxStudents()), 4, java.math.RoundingMode.HALF_UP));
        }
        vo.setCompletionRate(activeTotal > 0
                ? BigDecimal.valueOf(completedCount).divide(BigDecimal.valueOf(activeTotal), 4, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO);
        // 计算平均成绩（来自已完成且已评分的报名记录）
        List<MicroSpecialtyEnrollment> completedEnrollments = msEnrollmentRepository.selectList(
                new LambdaQueryWrapper<MicroSpecialtyEnrollment>()
                        .eq(MicroSpecialtyEnrollment::getMicroSpecialtyId, ms.getId())
                        .eq(MicroSpecialtyEnrollment::getStatus, "COMPLETED")
                        .isNotNull(MicroSpecialtyEnrollment::getFinalScore));
        if (!completedEnrollments.isEmpty()) {
            double avg = completedEnrollments.stream()
                    .mapToDouble(e -> e.getFinalScore() != null ? e.getFinalScore().doubleValue() : 0.0)
                    .average().orElse(0.0);
            vo.setAverageScore(BigDecimal.valueOf(avg));
        }
        // 计算质量分
        vo.setQualityScore(qualityScoreService.calculate(ms.getId()));
        return vo;
    }
    // ====== 课程编排 ======
    @Override
    public List<MicroSpecialtyCourseVO> listCourses(Long msId) {
        // 信息泄露防护：公开端点不过滤 DRAFT/CANCELLED 状态（P2-1）
        // 与 getDetail() 一致的过滤规则
        MicroSpecialty ms = msRepository.selectById(msId);
        if (ms == null) throw new BusinessException(ErrorCode.MS_NOT_FOUND);
        String status = ms.getStatus();
        Long userId = SecurityUtil.getCurrentUserId();
        // 未登录用户（SecurityConfig permitAll）userId 为 null，isAdminOrAcademic/isLeadOf 均返回 false
        if (("DRAFT".equals(status) || "CANCELLED".equals(status))
                && userId != null
                && !SecurityUtil.isAdminOrAcademic()
                && !userId.equals(ms.getCreatorId())
                && !isLeadOf(msId, userId)) {
            throw new BusinessException(ErrorCode.MS_NOT_FOUND);
        }
        List<MicroSpecialtyCourse> courseItems = msCourseRepository.selectList(
                new LambdaQueryWrapper<MicroSpecialtyCourse>()
                        .eq(MicroSpecialtyCourse::getMicroSpecialtyId, msId)
                        .orderByAsc(MicroSpecialtyCourse::getSortOrder));
        return toCourseVOBatch(courseItems);
    }
    // ====== 教师团队 ======
    @Override
    public List<MicroSpecialtyTeacherVO> listTeachers(Long msId) {
        // 信息泄露防护：公开端点不再暴露 INVITED/PENDING_ACADEMIC 等中间态邀请（P2-2）
        // 与 getDetail() 保持一致：仅返回 invite_status='ACTIVE' 的教师
        List<MicroSpecialtyTeacher> teachers = msTeacherRepository.selectList(
                new LambdaQueryWrapper<MicroSpecialtyTeacher>()
                        .eq(MicroSpecialtyTeacher::getMicroSpecialtyId, msId)
                        .eq(MicroSpecialtyTeacher::getInviteStatus, "ACTIVE"));
        return teachers.stream().map(this::toTeacherVO).collect(Collectors.toList());
    }
    // ====== 角色鉴权 ======
    @Override
    public boolean isLeadOf(Long msId, Long userId) {
        return msTeacherRepository.selectCount(
                new LambdaQueryWrapper<MicroSpecialtyTeacher>()
                        .eq(MicroSpecialtyTeacher::getMicroSpecialtyId, msId)
                        .eq(MicroSpecialtyTeacher::getTeacherId, userId)
                        .eq(MicroSpecialtyTeacher::getRole, "LEAD")
                        .eq(MicroSpecialtyTeacher::getInviteStatus, "ACTIVE")) > 0;
    }
    @Override
    public boolean isMemberOf(Long msId, Long userId) {
        return msTeacherRepository.selectCount(
                new LambdaQueryWrapper<MicroSpecialtyTeacher>()
                        .eq(MicroSpecialtyTeacher::getMicroSpecialtyId, msId)
                        .eq(MicroSpecialtyTeacher::getTeacherId, userId)
                        .in(MicroSpecialtyTeacher::getRole, "MEMBER", "ASSISTANT")
                        .eq(MicroSpecialtyTeacher::getInviteStatus, "ACTIVE")) > 0;
    }
    @Override
    public boolean isOwnerOrLead(Long msId, Long userId) {
        if (isLeadOf(msId, userId)) return true;
        MicroSpecialty ms = msRepository.selectById(msId);
        return ms != null && userId.equals(ms.getCreatorId());
    }
    @Override
    public String getMyRole(Long msId) {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) return null;
        MicroSpecialtyTeacher record = msTeacherRepository.selectOne(
                new LambdaQueryWrapper<MicroSpecialtyTeacher>()
                        .eq(MicroSpecialtyTeacher::getMicroSpecialtyId, msId)
                        .eq(MicroSpecialtyTeacher::getTeacherId, userId)
                        .eq(MicroSpecialtyTeacher::getInviteStatus, "ACTIVE"));
        return record != null ? record.getRole() : null;
    }
    // ====== 转换方法 ======
    @Override
    public MicroSpecialtyVO toVO(MicroSpecialty ms) {
        MicroSpecialtyVO vo = new MicroSpecialtyVO();
        copyToVO(ms, vo);
        return vo;
    }
    private void copyToVO(MicroSpecialty ms, MicroSpecialtyVO vo) {
        vo.setId(ms.getId());
        vo.setCode(ms.getCode());
        vo.setTitle(ms.getTitle());
        vo.setSubtitle(ms.getSubtitle());
        vo.setCoverUrl(ms.getCoverUrl());
        vo.setDescription(ms.getDescription());
        vo.setOfferDepartmentId(ms.getOfferDepartmentId());
        vo.setLeadTeacherId(ms.getLeadTeacherId());
        vo.setTargetAudience(ms.getTargetAudience());
        vo.setTrainingObjective(ms.getTrainingObjective());
        vo.setAdmissionRequirement(ms.getAdmissionRequirement());
        vo.setCompletionRule(ms.getCompletionRule());
        vo.setTotalCredits(ms.getTotalCredits());
        vo.setTotalHours(ms.getTotalHours());
        vo.setRequiredCourseCount(ms.getRequiredCourseCount());
        vo.setElectiveCourseCount(ms.getElectiveCourseCount());
        vo.setMinCredits(ms.getMinCredits());
        vo.setMaxStudents(ms.getMaxStudents());
        vo.setStudentCount(ms.getStudentCount());
        vo.setSemester(ms.getSemester());
        vo.setIsFeatured(ms.getIsFeatured());
        vo.setFeaturedRank(ms.getFeaturedRank());
        vo.setFeaturedStatus(ms.getFeaturedStatus());
        vo.setIsGoldFeatured(ms.getIsGoldFeatured());
        vo.setStatus(ms.getStatus());
        vo.setRejectReason(ms.getRejectReason());
        vo.setSubmittedAt(ms.getSubmittedAt());
        vo.setApprovedAt(ms.getApprovedAt());
        vo.setOpenedAt(ms.getOpenedAt());
        vo.setClosedAt(ms.getClosedAt());
        vo.setCreatorId(ms.getCreatorId());
        vo.setCreatedAt(ms.getCreatedAt());
        vo.setUpdatedAt(ms.getUpdatedAt());
        vo.setFeaturedApplyAt(ms.getFeaturedApplyAt());
        vo.setFeaturedApplyReason(ms.getFeaturedApplyReason());
        // Set department name
        Department dept = departmentRepository.selectById(ms.getOfferDepartmentId());
        if (dept != null) {
            vo.setDepartmentName(dept.getName());
        }
        // Set lead teacher name
        if (ms.getLeadTeacherId() != null) {
            User leadUser = userRepository.selectById(ms.getLeadTeacherId());
            if (leadUser != null) {
                vo.setLeadTeacherName(leadUser.getRealName());
            }
        }
        // Set creator name
        if (ms.getCreatorId() != null) {
            User creatorUser = userRepository.selectById(ms.getCreatorId());
            if (creatorUser != null) {
                vo.setCreatorName(creatorUser.getRealName());
            }
        }
        // Count courses
        Long courseCount = msCourseRepository.selectCount(
                new LambdaQueryWrapper<MicroSpecialtyCourse>()
                        .eq(MicroSpecialtyCourse::getMicroSpecialtyId, ms.getId()));
        vo.setCourseCount(courseCount.intValue());
        // Count pending enrollments (待审报名)
        Long pendingCount = msEnrollmentRepository.selectCount(
                new LambdaQueryWrapper<MicroSpecialtyEnrollment>()
                        .eq(MicroSpecialtyEnrollment::getMicroSpecialtyId, ms.getId())
                        .eq(MicroSpecialtyEnrollment::getStatus, "PENDING"));
        vo.setPendingEnrollCount(pendingCount.intValue());
        // Set teacher role for current user in this micro-specialty
        Long currentUserId = SecurityUtil.getCurrentUserId();
        if (currentUserId != null) {
            MicroSpecialtyTeacher teacher = msTeacherRepository.selectOne(
                    new LambdaQueryWrapper<MicroSpecialtyTeacher>()
                            .eq(MicroSpecialtyTeacher::getMicroSpecialtyId, ms.getId())
                            .eq(MicroSpecialtyTeacher::getTeacherId, currentUserId));
            if (teacher != null) {
                vo.setRole(teacher.getRole());
            }
        }
        // Count total enrollments
        Long totalEnrollments = msEnrollmentRepository.selectCount(
                new LambdaQueryWrapper<MicroSpecialtyEnrollment>()
                        .eq(MicroSpecialtyEnrollment::getMicroSpecialtyId, ms.getId()));
        vo.setTotalEnrollments(totalEnrollments.intValue());
    }
    @Override
    public MicroSpecialtyCourseVO toCourseVO(MicroSpecialtyCourse item) {
        MicroSpecialtyCourseVO vo = new MicroSpecialtyCourseVO();
        vo.setId(item.getId());
        vo.setMicroSpecialtyId(item.getMicroSpecialtyId());
        vo.setCourseId(item.getCourseId());
        vo.setSortOrder(item.getSortOrder());
        vo.setIsRequired(item.getIsRequired());
        vo.setCredits(item.getCredits());
        vo.setHours(item.getHours());
        vo.setMinScore(item.getMinScore());
        vo.setRecommendedSemester(item.getRecommendedSemester());
        // Query course details
        if (item.getCourseId() != null) {
            Course course = courseRepository.selectById(item.getCourseId());
            if (course != null) {
                vo.setCourseTitle(course.getTitle());
                vo.setCourseType(course.getCourseType());
                // set teacher name from course
                if (course.getTeacherId() != null) {
                    User teacher = userRepository.selectById(course.getTeacherId());
                    if (teacher != null) {
                        vo.setTeacherName(teacher.getRealName());
                    }
                }
            }
        }
        return vo;
    }

    /**
     * P1I-033: 批量转换 toCourseVO，使用 selectBatchIds 替代逐条 selectById 消除 N+1。
     */
    private List<MicroSpecialtyCourseVO> toCourseVOBatch(List<MicroSpecialtyCourse> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        // 批量预加载所有 course
        java.util.Set<Long> courseIds = items.stream()
                .map(MicroSpecialtyCourse::getCourseId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        java.util.Map<Long, Course> courseMap = new HashMap<>();
        if (!courseIds.isEmpty()) {
            courseRepository.selectBatchIds(courseIds)
                    .forEach(c -> courseMap.put(c.getId(), c));
        }
        // 批量预加载所有 teacher
        java.util.Set<Long> teacherIds = courseMap.values().stream()
                .map(Course::getTeacherId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        java.util.Map<Long, User> teacherMap = new HashMap<>();
        if (!teacherIds.isEmpty()) {
            userRepository.selectBatchIds(teacherIds)
                    .forEach(u -> teacherMap.put(u.getId(), u));
        }
        // 转换为 VO
        List<MicroSpecialtyCourseVO> result = new ArrayList<>(items.size());
        for (MicroSpecialtyCourse item : items) {
            MicroSpecialtyCourseVO vo = new MicroSpecialtyCourseVO();
            vo.setId(item.getId());
            vo.setMicroSpecialtyId(item.getMicroSpecialtyId());
            vo.setCourseId(item.getCourseId());
            vo.setSortOrder(item.getSortOrder());
            vo.setIsRequired(item.getIsRequired());
            vo.setCredits(item.getCredits());
            vo.setHours(item.getHours());
            vo.setMinScore(item.getMinScore());
            vo.setRecommendedSemester(item.getRecommendedSemester());
            // 使用预加载的 map 填充课程和教师信息
            if (item.getCourseId() != null) {
                Course course = courseMap.get(item.getCourseId());
                if (course != null) {
                    vo.setCourseTitle(course.getTitle());
                    vo.setCourseType(course.getCourseType());
                    if (course.getTeacherId() != null) {
                        User teacher = teacherMap.get(course.getTeacherId());
                        if (teacher != null) {
                            vo.setTeacherName(teacher.getRealName());
                        }
                    }
                }
            }
            result.add(vo);
        }
        return result;
    }

    @Override
    public MicroSpecialtyTeacherVO toTeacherVO(MicroSpecialtyTeacher t) {
        MicroSpecialtyTeacherVO vo = new MicroSpecialtyTeacherVO();
        vo.setId(t.getId());
        vo.setMicroSpecialtyId(t.getMicroSpecialtyId());
        vo.setTeacherId(t.getTeacherId());
        vo.setRoleLabel(t.getRole());
        vo.setRole(t.getRole());
        vo.setCourseId(t.getCourseId());
        vo.setResponsibility(t.getResponsibility());
        vo.setInviteStatus(t.getInviteStatus());
        vo.setInviteExpiresAt(t.getInviteExpiresAt());
        if (t.getTeacherId() != null) {
            User u = userRepository.selectById(t.getTeacherId());
            if (u != null) {
                vo.setTeacherName(u.getRealName());
                vo.setTeacherAvatar(u.getAvatar());
            }
        }
        // Query course title for this teacher's assignment
        if (t.getCourseId() != null) {
            Course course = courseRepository.selectById(t.getCourseId());
            if (course != null) {
                vo.setCourseTitle(course.getTitle());
            }
        }
        return vo;
    }
    /**
     * P1-2/P1-C-4: 批量列表场景用预加载 map,避免 N+1 selectById。
     */
    private void copyToVO(MicroSpecialty ms, MicroSpecialtyVO vo,
                          java.util.Map<Long, String> deptNameMap,
                          java.util.Map<Long, String> teacherNameMap,
                          java.util.Map<Long, String> creatorNameMap,
                          java.util.Map<Long, Integer> courseCountMap,
                          java.util.Map<Long, Integer> pendingEnrollCountMap,
                          java.util.Map<Long, Integer> totalEnrollmentsMap,
                          java.util.Map<Long, String> roleMap) {
        // 复制基础字段（同单参数版本，不调用来避免 N+1）
        vo.setId(ms.getId());
        vo.setCode(ms.getCode());
        vo.setTitle(ms.getTitle());
        vo.setSubtitle(ms.getSubtitle());
        vo.setCoverUrl(ms.getCoverUrl());
        vo.setDescription(ms.getDescription());
        vo.setOfferDepartmentId(ms.getOfferDepartmentId());
        vo.setLeadTeacherId(ms.getLeadTeacherId());
        vo.setTargetAudience(ms.getTargetAudience());
        vo.setTrainingObjective(ms.getTrainingObjective());
        vo.setAdmissionRequirement(ms.getAdmissionRequirement());
        vo.setCompletionRule(ms.getCompletionRule());
        vo.setTotalCredits(ms.getTotalCredits());
        vo.setTotalHours(ms.getTotalHours());
        vo.setRequiredCourseCount(ms.getRequiredCourseCount());
        vo.setElectiveCourseCount(ms.getElectiveCourseCount());
        vo.setMinCredits(ms.getMinCredits());
        vo.setMaxStudents(ms.getMaxStudents());
        vo.setStudentCount(ms.getStudentCount());
        vo.setSemester(ms.getSemester());
        vo.setIsFeatured(ms.getIsFeatured());
        vo.setFeaturedRank(ms.getFeaturedRank());
        vo.setFeaturedStatus(ms.getFeaturedStatus());
        vo.setIsGoldFeatured(ms.getIsGoldFeatured());
        vo.setStatus(ms.getStatus());
        vo.setRejectReason(ms.getRejectReason());
        vo.setSubmittedAt(ms.getSubmittedAt());
        vo.setApprovedAt(ms.getApprovedAt());
        vo.setOpenedAt(ms.getOpenedAt());
        vo.setClosedAt(ms.getClosedAt());
        vo.setCreatorId(ms.getCreatorId());
        vo.setCreatedAt(ms.getCreatedAt());
        vo.setUpdatedAt(ms.getUpdatedAt());
        vo.setFeaturedApplyAt(ms.getFeaturedApplyAt());
        vo.setFeaturedApplyReason(ms.getFeaturedApplyReason());
        // 使用预加载 map 填充关联字段
        if (ms.getOfferDepartmentId() != null) {
            String deptName = deptNameMap.get(ms.getOfferDepartmentId());
            if (deptName != null) vo.setDepartmentName(deptName);
        }
        if (ms.getLeadTeacherId() != null) {
            String teacherName = teacherNameMap.get(ms.getLeadTeacherId());
            if (teacherName != null) vo.setLeadTeacherName(teacherName);
        }
        if (ms.getCreatorId() != null) {
            String creatorName = creatorNameMap.get(ms.getCreatorId());
            if (creatorName != null) vo.setCreatorName(creatorName);
        }
        // 使用批量预计算统计值
        if (courseCountMap != null) {
            vo.setCourseCount(courseCountMap.getOrDefault(ms.getId(), 0));
        }
        if (pendingEnrollCountMap != null) {
            vo.setPendingEnrollCount(pendingEnrollCountMap.getOrDefault(ms.getId(), 0));
        }
        if (totalEnrollmentsMap != null) {
            vo.setTotalEnrollments(totalEnrollmentsMap.getOrDefault(ms.getId(), 0));
        }
}
}
