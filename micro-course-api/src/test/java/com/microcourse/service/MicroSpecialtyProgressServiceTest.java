package com.microcourse.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.microcourse.entity.Course;
import com.microcourse.entity.Enrollment;
import com.microcourse.entity.MicroSpecialty;
import com.microcourse.entity.MicroSpecialtyCourse;
import com.microcourse.entity.MicroSpecialtyEnrollment;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.repository.MicroSpecialtyCourseRepository;
import com.microcourse.repository.MicroSpecialtyEnrollmentRepository;
import com.microcourse.repository.MicroSpecialtyRepository;
import com.microcourse.service.impl.MicroSpecialtyProgressServiceImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MicroSpecialtyProgressServiceImpl — 进度聚合实现")
class MicroSpecialtyProgressServiceTest {

    private static final Pattern VERSION_PARAM_PATTERN =
            Pattern.compile("version = \\#\\{ew\\.paramNameValuePairs\\.(MPGENVAL\\d+)\\}");

    @Mock private MicroSpecialtyEnrollmentRepository enrollmentRepository;
    @Mock private MicroSpecialtyRepository msRepository;
    @Mock private MicroSpecialtyCourseRepository msCourseRepository;
    @Mock private EnrollmentRepository courseEnrollmentRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private NotificationService notificationService;
    @Mock private MicroSpecialtyEnrollmentService enrollmentService;

    private MicroSpecialtyProgressServiceImpl service;

    @BeforeAll
    static void init() {
        MybatisPlusTestHelper.initTableInfo();
    }

    @BeforeEach
    void setUp() {
        service = new MicroSpecialtyProgressServiceImpl(
                enrollmentRepository,
                msRepository,
                msCourseRepository,
                courseEnrollmentRepository,
                courseRepository,
                notificationService,
                enrollmentService
        );
    }

    @Test
    @DisplayName("aggregateProgress: APPROVED 首次聚合后第二次写库使用递增 version")
    void aggregateProgress_approvedToInProgress_usesIncrementedVersion() {
        MicroSpecialtyEnrollment enrollment = enrollment(1L, "APPROVED", 0, 8L, 3L);
        MicroSpecialty specialty = specialty();
        MicroSpecialtyCourse requiredCourse = requiredCourse(11L, 1L);
        Enrollment courseEnrollment = courseEnrollment(11L, 8L, 80, 0.5);
        Course course = new Course();
        course.setId(11L);
        course.setTitle("课程A");

        when(enrollmentRepository.selectById(1L)).thenReturn(enrollment);
        when(msRepository.selectById(3L)).thenReturn(specialty);
        when(msCourseRepository.selectList(any()))
                .thenReturn(List.of(requiredCourse))
                .thenReturn(Collections.emptyList());
        when(courseRepository.selectBatchIds(any())).thenReturn(List.of(course));
        when(courseEnrollmentRepository.selectOne(any())).thenReturn(courseEnrollment);

        List<Integer> versionConditions = new ArrayList<>();
        when(enrollmentRepository.update(any(), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            LambdaUpdateWrapper<MicroSpecialtyEnrollment> wrapper = invocation.getArgument(1);
            String sqlSegment = wrapper.getSqlSegment();
            Map<String, Object> params = wrapper.getParamNameValuePairs();
            Matcher matcher = VERSION_PARAM_PATTERN.matcher(sqlSegment);
            if (matcher.find()) {
                String versionKey = matcher.group(1);
                Object versionValue = params.get(versionKey);
                versionConditions.add(((Number) versionValue).intValue());
            }
            return 1;
        });

        service.aggregateProgress(1L);

        assertEquals(List.of(0, 1), versionConditions);
    }

    @Test
    @DisplayName("aggregateProgress: 未通过选修课不计入学分且不触发 CREDITS_MIN 结业")
    void aggregateProgress_failedElectiveDoesNotCountCredits() {
        MicroSpecialtyEnrollment enrollment = enrollment(1L, "APPROVED", 0, 8L, 3L);
        MicroSpecialty specialty = specialty();
        specialty.setMinCredits(BigDecimal.valueOf(4));
        MicroSpecialtyCourse requiredCourse = requiredCourse(11L, 3L);
        MicroSpecialtyCourse electiveCourse = electiveCourse(12L, 3L);
        Enrollment requiredEnrollment = courseEnrollment(11L, 8L, 80, 0.5);
        Enrollment failedElectiveEnrollment = courseEnrollment(12L, 8L, 50, 0.3);
        Course required = new Course();
        required.setId(11L);
        required.setTitle("必修A");
        Course elective = new Course();
        elective.setId(12L);
        elective.setTitle("选修B");

        when(enrollmentRepository.selectById(1L)).thenReturn(enrollment);
        when(msRepository.selectById(3L)).thenReturn(specialty);
        when(msCourseRepository.selectList(any()))
                .thenReturn(List.of(requiredCourse))
                .thenReturn(List.of(electiveCourse));
        when(courseRepository.selectBatchIds(any())).thenReturn(List.of(required, elective));
        when(courseEnrollmentRepository.selectOne(any()))
                .thenReturn(requiredEnrollment)
                .thenReturn(failedElectiveEnrollment);

        List<String> sqlSets = new ArrayList<>();
        List<Map<String, Object>> paramsList = new ArrayList<>();
        when(enrollmentRepository.update(any(), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            LambdaUpdateWrapper<MicroSpecialtyEnrollment> wrapper = invocation.getArgument(1);
            sqlSets.add(wrapper.getSqlSet());
            paramsList.add(Map.copyOf(wrapper.getParamNameValuePairs()));
            return 1;
        });

        service.aggregateProgress(1L);

        assertEquals(2, sqlSets.size());
        assertEquals(false, sqlSets.get(1).contains("status=#{"));
        boolean creditsEarnedCorrect = paramsList.get(1).values().stream()
                .filter(BigDecimal.class::isInstance)
                .map(BigDecimal.class::cast)
                .anyMatch(bd -> bd.compareTo(BigDecimal.valueOf(2)) == 0);
        assertTrue(creditsEarnedCorrect, "creditsEarned 应为 2（仅必修课程，选修未通过）");
    }

    private static MicroSpecialtyEnrollment enrollment(Long id, String status, int version, Long userId, Long msId) {
        MicroSpecialtyEnrollment enrollment = new MicroSpecialtyEnrollment();
        enrollment.setId(id);
        enrollment.setStatus(status);
        enrollment.setVersion(version);
        enrollment.setUserId(userId);
        enrollment.setMicroSpecialtyId(msId);
        enrollment.setProgress(BigDecimal.ZERO);
        enrollment.setCreditsEarned(BigDecimal.ZERO);
        enrollment.setCoursesCompleted(0);
        enrollment.setCreatedAt(LocalDateTime.now());
        enrollment.setUpdatedAt(LocalDateTime.now());
        return enrollment;
    }

    private static MicroSpecialty specialty() {
        MicroSpecialty specialty = new MicroSpecialty();
        specialty.setId(3L);
        specialty.setTitle("测试微专业");
        specialty.setCompletionRule("CREDITS_MIN");
        specialty.setMinCredits(BigDecimal.valueOf(10));
        specialty.setRequiredCourseCount(2);
        return specialty;
    }

    private static MicroSpecialtyCourse requiredCourse(Long courseId, Long msId) {
        MicroSpecialtyCourse course = new MicroSpecialtyCourse();
        course.setCourseId(courseId);
        course.setMicroSpecialtyId(msId);
        course.setIsRequired(true);
        course.setCredits(BigDecimal.valueOf(2));
        course.setMinScore(BigDecimal.valueOf(60));
        return course;
    }

    private static MicroSpecialtyCourse electiveCourse(Long courseId, Long msId) {
        MicroSpecialtyCourse course = new MicroSpecialtyCourse();
        course.setCourseId(courseId);
        course.setMicroSpecialtyId(msId);
        course.setIsRequired(false);
        course.setCredits(BigDecimal.valueOf(2));
        course.setMinScore(BigDecimal.valueOf(60));
        return course;
    }

    private static Enrollment courseEnrollment(Long courseId, Long userId, int finalScore, double progress) {
        Enrollment enrollment = new Enrollment();
        enrollment.setCourseId(courseId);
        enrollment.setUserId(userId);
        enrollment.setFinalScore(BigDecimal.valueOf(finalScore));
        enrollment.setProgress(progress);
        return enrollment;
    }

    private static BigDecimal firstBigDecimal(Map<String, Object> params) {
        return params.values().stream()
                .filter(BigDecimal.class::isInstance)
                .map(BigDecimal.class::cast)
                .findFirst()
                .orElseThrow();
    }
}
