package com.microcourse.service;

import com.microcourse.dto.microSpecialty.MicroSpecialtyStatsVO;
import com.microcourse.entity.MicroSpecialty;
import com.microcourse.entity.MicroSpecialtyEnrollment;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.DepartmentRepository;
import com.microcourse.repository.MicroSpecialtyCourseRepository;
import com.microcourse.repository.MicroSpecialtyEnrollmentRepository;
import com.microcourse.repository.MicroSpecialtyRepository;
import com.microcourse.repository.MicroSpecialtyTeacherRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.impl.MicroSpecialtyQueryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MicroSpecialtyQueryServiceImpl - stats")
class MicroSpecialtyQueryServiceTest {

    @Mock private MicroSpecialtyRepository msRepository;
    @Mock private MicroSpecialtyCourseRepository msCourseRepository;
    @Mock private MicroSpecialtyTeacherRepository msTeacherRepository;
    @Mock private MicroSpecialtyEnrollmentRepository msEnrollmentRepository;
    @Mock private UserRepository userRepository;
    @Mock private MicroSpecialtyQualityScoreService qualityScoreService;
    @Mock private AdminSettingService adminSettingService;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private CourseRepository courseRepository;

    private MicroSpecialtyQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MicroSpecialtyQueryServiceImpl(
                msRepository,
                msCourseRepository,
                msTeacherRepository,
                msEnrollmentRepository,
                userRepository,
                qualityScoreService,
                adminSettingService,
                departmentRepository,
                courseRepository
        );
    }

    @Test
    @DisplayName("stats: CERTIFIED 计入已结业，FAILED 不进入完成率分母")
    void stats_certifiedCountsAsCompleted_failedExcludedFromCompletionRate() {
        MicroSpecialty ms = new MicroSpecialty();
        ms.setId(1L);
        ms.setStudentCount(4);
        ms.setMaxStudents(10);

        MicroSpecialtyEnrollment completed = scoredEnrollment("COMPLETED", 90);
        MicroSpecialtyEnrollment certified = scoredEnrollment("CERTIFIED", 80);

        when(msRepository.selectById(1L)).thenReturn(ms);
        when(msEnrollmentRepository.selectCount(any()))
                .thenReturn(5L)  // totalCount
                .thenReturn(2L)  // completedCount (COMPLETED + CERTIFIED)
                .thenReturn(1L)  // inProgress
                .thenReturn(1L); // failed
        when(msEnrollmentRepository.selectList(any())).thenReturn(List.of(completed, certified));
        when(qualityScoreService.calculate(1L)).thenReturn(BigDecimal.valueOf(0.88));

        MicroSpecialtyStatsVO vo = service.stats(1L);

        assertEquals(2, vo.getCompletedCount());
        assertEquals(1, vo.getInProgressCount());
        assertEquals(1, vo.getFailedCount());
        assertEquals(new BigDecimal("0.6667"), vo.getCompletionRate());
        assertEquals(new BigDecimal("85.0"), vo.getAverageScore());
    }

    private static MicroSpecialtyEnrollment scoredEnrollment(String status, int score) {
        MicroSpecialtyEnrollment enrollment = new MicroSpecialtyEnrollment();
        enrollment.setStatus(status);
        enrollment.setFinalScore(BigDecimal.valueOf(score));
        return enrollment;
    }
}
