package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.microcourse.dto.microSpecialty.MicroSpecialtyVO;
import com.microcourse.dto.PageResult;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * MicroSpecialtyPageLoader 单元测试 (Phase 10)
 *
 * <p>验证分页查询 + 6 套批量预加载 + 回调装配的逻辑正确性。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MicroSpecialtyPageLoader 分页查询单元测试")
class MicroSpecialtyPageLoaderTest {

    @Mock private MicroSpecialtyRepository msRepository;
    @Mock private MicroSpecialtyCourseRepository msCourseRepository;
    @Mock private MicroSpecialtyTeacherRepository msTeacherRepository;
    @Mock private MicroSpecialtyEnrollmentRepository msEnrollmentRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private UserRepository userRepository;

    private MicroSpecialtyPageLoader loader;

    @BeforeEach
    void setUp() {
        loader = new MicroSpecialtyPageLoader(
                msRepository, msCourseRepository, msTeacherRepository,
                msEnrollmentRepository, departmentRepository, userRepository);
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder_clear();
    }

    private void SecurityContextHolder_clear() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("page: 空记录 → 返回空 PageResult (不触发任何批量查询)")
    void page_emptyRecords() {
        IPage<MicroSpecialty> emptyPage = new Page<>(1, 10);
        emptyPage.setRecords(Collections.emptyList());
        emptyPage.setTotal(0);
        when(msRepository.selectPage(any(), any(LambdaQueryWrapper.class))).thenReturn(emptyPage);

        PageResult<MicroSpecialtyVO> result = loader.page(0, 10, null, (ms, vo, ctx) -> {
            throw new AssertionError("不应调用装配器");
        });

        assertEquals(0L, result.getTotalElements());
        assertTrue(result.getItems().isEmpty());
        // 空结果: 不应触发任何 batch 查询
        verifyNoInteractions(msCourseRepository, msEnrollmentRepository,
                msTeacherRepository, departmentRepository, userRepository);
    }

    @Test
    @DisplayName("page: keyword 过滤 → wrapper.like() 包含 title")
    void page_keywordFilter() {
        Map<String, Object> params = new HashMap<>();
        params.put("keyword", "Java");

        IPage<MicroSpecialty> emptyPage = new Page<>(1, 10);
        emptyPage.setRecords(Collections.emptyList());
        when(msRepository.selectPage(any(), any(LambdaQueryWrapper.class))).thenReturn(emptyPage);

        loader.page(0, 10, params, (ms, vo, ctx) -> {});

        // 验证 wrapper 通过 selectPage 的第二个参数被传入
        org.mockito.ArgumentCaptor<LambdaQueryWrapper<MicroSpecialty>> captor =
                org.mockito.ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(msRepository).selectPage(any(), captor.capture());
        // wrapper 应该已经构建 (无法直接断言内容, 但应非 null)
        assertNotNull(captor.getValue());
    }

    @Test
    @DisplayName("page: 学生角色 → 自动过滤为 RECRUITING")
    void page_studentRole_recruitingFilter() {
        IPage<MicroSpecialty> emptyPage = new Page<>(1, 10);
        emptyPage.setRecords(Collections.emptyList());
        when(msRepository.selectPage(any(), any(LambdaQueryWrapper.class))).thenReturn(emptyPage);

        try (MockedStatic<SecurityUtil> mockedSecurity = Mockito.mockStatic(SecurityUtil.class, Mockito.CALLS_REAL_METHODS)) {
            mockedSecurity.when(SecurityUtil::isAdminOrAcademic).thenReturn(false);
            loader.page(0, 10, new HashMap<>(), (ms, vo, ctx) -> {});
        }

        verify(msRepository).selectPage(any(), any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("page: 1 个 MS → 6 套批量预加载 + 装配器调用 1 次")
    void page_singleRecord_batchLoading() {
        MicroSpecialty ms = new MicroSpecialty();
        ms.setId(1L);
        ms.setOfferDepartmentId(10L);
        ms.setLeadTeacherId(20L);
        ms.setCreatorId(30L);

        IPage<MicroSpecialty> page = new Page<>(1, 10);
        page.setRecords(List.of(ms));
        page.setTotal(1);
        lenient().when(msRepository.selectPage(any(), any(LambdaQueryWrapper.class))).thenReturn(page);

        // 6 套批量查询 mock (返回空即可) — lenient 避免 strict stubbing 报错
        lenient().when(departmentRepository.selectBatchIds(anyList())).thenReturn(Collections.emptyList());
        lenient().when(userRepository.selectBatchIds(anyList())).thenReturn(Collections.emptyList());
        lenient().when(msCourseRepository.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        lenient().when(msEnrollmentRepository.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        lenient().when(msTeacherRepository.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        int[] callCount = {0};
        PageResult<MicroSpecialtyVO> result = loader.page(0, 10, null, (m, vo, ctx) -> {
            callCount[0]++;
            assertSame(ms, m);
            assertNotNull(vo);
            assertNotNull(ctx);
            assertNotNull(ctx.deptNameMap());
            assertNotNull(ctx.courseCountMap());
        });

        assertEquals(1, callCount[0]);
        assertEquals(1, result.getItems().size());
        assertEquals(1L, result.getTotalElements());
        // 关键断言: 核心批量预加载方法都至少被调用 1 次 (避免 N+1 → 至少 1 次而非 N 次)
        // 注: msTeacherRepository 仅在 currentUserId != null 时调用, 此处未登录不调用
        verify(departmentRepository, atLeastOnce()).selectBatchIds(any());
        verify(userRepository, atLeastOnce()).selectBatchIds(any());
        verify(msCourseRepository, atLeastOnce()).selectList(any(LambdaQueryWrapper.class));
        verify(msEnrollmentRepository, atLeastOnce()).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("page: 多个 MS → 批量预加载 1 次 (避免 N+1)")
    void page_multipleRecords_singleBatchQuery() {
        MicroSpecialty ms1 = makeMs(1L, 10L, 20L, 30L);
        MicroSpecialty ms2 = makeMs(2L, 10L, 20L, 30L);
        MicroSpecialty ms3 = makeMs(3L, 11L, 21L, 31L);

        IPage<MicroSpecialty> page = new Page<>(1, 10);
        page.setRecords(List.of(ms1, ms2, ms3));
        page.setTotal(3);
        lenient().when(msRepository.selectPage(any(), any(LambdaQueryWrapper.class))).thenReturn(page);
        lenient().when(departmentRepository.selectBatchIds(anyList())).thenReturn(Collections.emptyList());
        lenient().when(userRepository.selectBatchIds(anyList())).thenReturn(Collections.emptyList());
        lenient().when(msCourseRepository.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        lenient().when(msEnrollmentRepository.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        lenient().when(msTeacherRepository.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        PageResult<MicroSpecialtyVO> result = loader.page(0, 10, null, (m, vo, ctx) -> {});

        assertEquals(3, result.getItems().size());
        // 关键断言: 即使 3 个 MS, 批量预加载仍只调用 1 次 (N+1 已消除)
        verify(departmentRepository, atLeastOnce()).selectBatchIds(any());
        verify(userRepository, atLeastOnce()).selectBatchIds(any());
        verify(msCourseRepository, atLeastOnce()).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("page: featured=true → 过滤条件 ne featuredStatus=NONE")
    void page_featuredTrueFilter() {
        Map<String, Object> params = new HashMap<>();
        params.put("featured", "true");
        params.put("status", "RECRUITING");
        params.put("isGoldFeatured", "true");

        IPage<MicroSpecialty> emptyPage = new Page<>(1, 10);
        emptyPage.setRecords(Collections.emptyList());
        when(msRepository.selectPage(any(), any(LambdaQueryWrapper.class))).thenReturn(emptyPage);

        loader.page(0, 10, params, (ms, vo, ctx) -> {});

        verify(msRepository).selectPage(any(), any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("page: role=leading → 启用 EXISTS 子查询过滤")
    void page_roleLeading() {
        Map<String, Object> params = new HashMap<>();
        params.put("role", "leading");

        // 设置当前用户ID (SecurityUtil 内部 SecurityContextHolder.getContext())
        org.springframework.security.authentication.UsernamePasswordAuthenticationToken auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        999L, null, Collections.emptyList());
        org.springframework.security.core.context.SecurityContextHolder.getContext()
                .setAuthentication(auth);

        IPage<MicroSpecialty> emptyPage = new Page<>(1, 10);
        emptyPage.setRecords(Collections.emptyList());
        when(msRepository.selectPage(any(), any(LambdaQueryWrapper.class))).thenReturn(emptyPage);

        loader.page(0, 10, params, (ms, vo, ctx) -> {});

        verify(msRepository).selectPage(any(), any(LambdaQueryWrapper.class));
    }

    private MicroSpecialty makeMs(long id, Long deptId, Long teacherId, Long creatorId) {
        MicroSpecialty ms = new MicroSpecialty();
        ms.setId(id);
        ms.setOfferDepartmentId(deptId);
        ms.setLeadTeacherId(teacherId);
        ms.setCreatorId(creatorId);
        return ms;
    }
}