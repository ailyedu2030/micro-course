package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.microSpecialty.MicroSpecialtyVO;
import com.microcourse.entity.Department;
import com.microcourse.entity.MicroSpecialty;
import com.microcourse.entity.MicroSpecialtyCourse;
import com.microcourse.entity.MicroSpecialtyEnrollment;
import com.microcourse.entity.MicroSpecialtyTeacher;
import com.microcourse.entity.User;
import com.microcourse.repository.DepartmentRepository;
import com.microcourse.repository.MicroSpecialtyCourseRepository;
import com.microcourse.repository.MicroSpecialtyEnrollmentRepository;
import com.microcourse.repository.MicroSpecialtyRepository;
import com.microcourse.repository.MicroSpecialtyTeacherRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.util.SecurityUtil;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 微专业分页查询执行器 (Phase 10 拆分 MicroSpecialtyQueryServiceImpl)
 *
 * <p>原 {@code page()} 方法 98 行（含 6 次批量预加载 + 4 次 LambdaQueryWrapper + 1 次 copyToVO 编排），
 * 单独提取到本类后，ServiceImpl 仅保留 ~20 行委托，文件行数从 803 → ~550。</p>
 *
 * <h3>【现象】</h3>
 * MicroSpecialtyQueryServiceImpl 803 行超过 precheck 800 行上限，被加入 whitelist 受控观察（pre-existing）。
 *
 * <h3>【根因】</h3>
 * 一个 ServiceImpl 类承担了 5 类职责：分页查询 / 广场数据 / 详情 / 统计 / 课程教师列表，
 * 每个职责都有自己的批量预加载和 VO 转换逻辑，叠加后总行数膨胀。
 *
 * <h3>【修复】</h3>
 * 把"分页查询"职责（含 6 套批量预加载）提取到本类。
 * 后续类（SquareLoader / DetailLoader）按相同模式继续拆分。
 *
 * <h3>【设计原则】</h3>
 * <ul>
 *   <li>构造函数注入所有依赖，可独立 Mockito 测试
 *   <li>不可变 record 传递批量预加载上下文，避免共享可变状态
 *   <li>ServiceImpl 保留 1 行委托，公共 API 零变化
 * </ul>
 *
 * @author refactor Phase 10 (2026-08-18)
 */
@Component
public class MicroSpecialtyPageLoader {

    private final MicroSpecialtyRepository msRepository;
    private final MicroSpecialtyCourseRepository msCourseRepository;
    private final MicroSpecialtyTeacherRepository msTeacherRepository;
    private final MicroSpecialtyEnrollmentRepository msEnrollmentRepository;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;

    public MicroSpecialtyPageLoader(MicroSpecialtyRepository msRepository,
                                    MicroSpecialtyCourseRepository msCourseRepository,
                                    MicroSpecialtyTeacherRepository msTeacherRepository,
                                    MicroSpecialtyEnrollmentRepository msEnrollmentRepository,
                                    DepartmentRepository departmentRepository,
                                    UserRepository userRepository) {
        this.msRepository = msRepository;
        this.msCourseRepository = msCourseRepository;
        this.msTeacherRepository = msTeacherRepository;
        this.msEnrollmentRepository = msEnrollmentRepository;
        this.departmentRepository = departmentRepository;
        this.userRepository = userRepository;
    }

    /**
     * 分页查询微专业列表（批量预加载关联字段避免 N+1）
     *
     * @param copyToVO 回调: 将 MicroSpecialty + 预加载 Map 转换为 MicroSpecialtyVO。
     *                  保留在 ServiceImpl 中以避免循环依赖。
     */
    public PageResult<MicroSpecialtyVO> page(int page, int size, Map<String, Object> params,
                                            PageVoAssembler assembler) {
        LambdaQueryWrapper<MicroSpecialty> wrapper = buildQueryWrapper(params);
        IPage<MicroSpecialty> ipage = msRepository.selectPage(new Page<>(page + 1, size), wrapper);
        List<MicroSpecialty> records = ipage.getRecords();

        if (records.isEmpty()) {
            return PageResult.of(Collections.emptyList(), 0L, page, size);
        }

        BatchContext ctx = batchLoadContext(records);
        List<MicroSpecialtyVO> vos = new ArrayList<>(records.size());
        for (MicroSpecialty ms : records) {
            MicroSpecialtyVO vo = new MicroSpecialtyVO();
            assembler.assemble(ms, vo, ctx);
            vos.add(vo);
        }
        return PageResult.of(vos, ipage.getTotal(), page, size);
    }

    /**
     * 构建查询 Wrapper（拆出便于独立测试）
     */
    private LambdaQueryWrapper<MicroSpecialty> buildQueryWrapper(Map<String, Object> params) {
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
        if (params != null && params.containsKey("featuredStatus")) {
            wrapper.eq(MicroSpecialty::getFeaturedStatus, params.get("featuredStatus").toString());
        }
        if (params != null && params.containsKey("isGoldFeatured")) {
            boolean isGold = Boolean.parseBoolean(params.get("isGoldFeatured").toString());
            wrapper.eq(MicroSpecialty::getIsGoldFeatured, isGold);
        }
        if (params != null && params.containsKey("featured")
                && Boolean.parseBoolean(params.get("featured").toString())) {
            wrapper.ne(MicroSpecialty::getFeaturedStatus, "NONE");
        }
        // Teacher role filter
        Long teacherId = SecurityUtil.getCurrentUserIdOpt();
        if (teacherId != null && params != null && params.containsKey("role")) {
            String roleFilter = (String) params.get("role");
            if ("leading".equals(roleFilter)) {
                wrapper.apply("EXISTS (SELECT 1 FROM micro_specialty_teachers WHERE micro_specialty_id = micro_specialties.id AND teacher_id = {0} AND role = 'LEAD')", teacherId);
            } else if ("participating".equals(roleFilter)) {
                wrapper.apply("EXISTS (SELECT 1 FROM micro_specialty_teachers WHERE micro_specialty_id = micro_specialties.id AND teacher_id = {0} AND role IN ('LEAD','MEMBER','ASSISTANT'))", teacherId);
            }
        }
        wrapper.orderByDesc(MicroSpecialty::getCreatedAt);
        return wrapper;
    }

    /**
     * 批量预加载所有关联字段（P2-7: 消除 N+1）。
     * 6 套 IN 查询一次取完所有 course / pending / total enrollments / user role。
     */
    private BatchContext batchLoadContext(List<MicroSpecialty> records) {
        java.util.Set<Long> deptIds = new HashSet<>();
        java.util.Set<Long> teacherIds = new HashSet<>();
        java.util.Set<Long> creatorIds = new HashSet<>();
        for (MicroSpecialty ms : records) {
            if (ms.getOfferDepartmentId() != null) deptIds.add(ms.getOfferDepartmentId());
            if (ms.getLeadTeacherId() != null) teacherIds.add(ms.getLeadTeacherId());
            if (ms.getCreatorId() != null) creatorIds.add(ms.getCreatorId());
        }
        Map<Long, String> deptNameMap = new HashMap<>();
        if (!deptIds.isEmpty()) {
            for (Department d : departmentRepository.selectBatchIds(deptIds)) {
                deptNameMap.put(d.getId(), d.getName());
            }
        }
        Map<Long, String> teacherNameMap = new HashMap<>();
        Map<Long, String> creatorNameMap = new HashMap<>();
        if (!teacherIds.isEmpty() || !creatorIds.isEmpty()) {
            Set<Long> allUserIds = new HashSet<>();
            allUserIds.addAll(teacherIds);
            allUserIds.addAll(creatorIds);
            for (User u : userRepository.selectBatchIds(allUserIds)) {
                String name = u.getRealName();
                if (teacherIds.contains(u.getId())) teacherNameMap.put(u.getId(), name);
                if (creatorIds.contains(u.getId())) creatorNameMap.put(u.getId(), name);
            }
        }

        // P1-C-4: 批量预计算统计字段
        java.util.List<Long> msIds = records.stream().map(MicroSpecialty::getId).collect(Collectors.toList());
        Map<Long, Integer> courseCountMap = new HashMap<>();
        for (MicroSpecialtyCourse mc : msCourseRepository.selectList(
                new LambdaQueryWrapper<MicroSpecialtyCourse>().in(MicroSpecialtyCourse::getMicroSpecialtyId, msIds))) {
            courseCountMap.merge(mc.getMicroSpecialtyId(), 1, Integer::sum);
        }
        Map<Long, Integer> pendingEnrollCountMap = new HashMap<>();
        for (MicroSpecialtyEnrollment e : msEnrollmentRepository.selectList(
                new LambdaQueryWrapper<MicroSpecialtyEnrollment>()
                        .in(MicroSpecialtyEnrollment::getMicroSpecialtyId, msIds)
                        .eq(MicroSpecialtyEnrollment::getStatus, "PENDING"))) {
            pendingEnrollCountMap.merge(e.getMicroSpecialtyId(), 1, Integer::sum);
        }
        Map<Long, Integer> totalEnrollmentsMap = new HashMap<>();
        for (MicroSpecialtyEnrollment e : msEnrollmentRepository.selectList(
                new LambdaQueryWrapper<MicroSpecialtyEnrollment>().in(MicroSpecialtyEnrollment::getMicroSpecialtyId, msIds))) {
            totalEnrollmentsMap.merge(e.getMicroSpecialtyId(), 1, Integer::sum);
        }
        // 当前用户的角色
        Map<Long, String> roleMap = new HashMap<>();
        Long currentUserId = SecurityUtil.getCurrentUserIdOpt();
        if (currentUserId != null) {
            for (MicroSpecialtyTeacher t : msTeacherRepository.selectList(
                    new LambdaQueryWrapper<MicroSpecialtyTeacher>()
                            .in(MicroSpecialtyTeacher::getMicroSpecialtyId, msIds)
                            .eq(MicroSpecialtyTeacher::getTeacherId, currentUserId))) {
                roleMap.put(t.getMicroSpecialtyId(), t.getRole());
            }
        }
        return new BatchContext(deptNameMap, teacherNameMap, creatorNameMap,
                courseCountMap, pendingEnrollCountMap, totalEnrollmentsMap, roleMap);
    }

    /**
     * 批量预加载上下文（不可变 record，避免共享可变状态）。
     */
    public record BatchContext(
            Map<Long, String> deptNameMap,
            Map<Long, String> teacherNameMap,
            Map<Long, String> creatorNameMap,
            Map<Long, Integer> courseCountMap,
            Map<Long, Integer> pendingEnrollCountMap,
            Map<Long, Integer> totalEnrollmentsMap,
            Map<Long, String> roleMap) {}

    /**
     * VO 装配回调 — 由 ServiceImpl 实现，避免 executor 直接依赖 VO 字段。
     */
    @FunctionalInterface
    public interface PageVoAssembler {
        void assemble(MicroSpecialty ms, MicroSpecialtyVO vo, BatchContext ctx);
    }
}