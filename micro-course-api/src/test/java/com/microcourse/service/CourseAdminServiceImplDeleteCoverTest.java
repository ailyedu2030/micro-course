package com.microcourse.service;

import com.microcourse.entity.Course;
import com.microcourse.enums.CourseStatus;
import com.microcourse.repository.CourseCategoryRepository;
import com.microcourse.repository.CourseChapterRepository;
import com.microcourse.repository.CourseNoteRepository;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.CourseReviewRepository;
import com.microcourse.repository.DiscussionCommentRepository;
import com.microcourse.repository.DiscussionPostRepository;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.repository.ExerciseRepository;
import com.microcourse.repository.HermesCourseMappingRepository;
import com.microcourse.repository.LearningProgressRepository;
import com.microcourse.repository.PluginGrantRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.repository.VideoBookmarkRepository;
import com.microcourse.repository.VideoRepository;
import com.microcourse.service.impl.CourseAdminServiceImpl;
import com.microcourse.util.SecurityUtil;
import com.microcourse.event.DomainEventPublisher;
import com.microcourse.plugin.interactive.mapper.CourseSlideMapper;
import com.microcourse.plugin.interactive.mapper.SlidePageMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class CourseAdminServiceImplDeleteCoverTest {

    @TempDir
    Path tempDir;

    private CourseRepository courseRepository;
    private CourseChapterRepository chapterRepository;
    private VideoRepository videoRepository;
    private EnrollmentRepository enrollmentRepository;
    private LearningProgressRepository learningProgressRepository;
    private ExerciseRepository exerciseRepository;
    private CourseSlideMapper courseSlideMapper;
    private SlidePageMapper slidePageMapper;
    private DiscussionPostRepository discussionPostRepository;
    private DiscussionCommentRepository discussionCommentRepository;
    private CourseNoteRepository courseNoteRepository;
    private VideoBookmarkRepository videoBookmarkRepository;
    private HermesCourseMappingRepository hermesCourseMappingRepository;
    private CourseAdminServiceImpl service;
    private MockedStatic<SecurityUtil> securityUtilMock;

    @BeforeEach
    void setUp() {
        MybatisPlusTestHelper.initTableInfo();

        courseRepository = mock(CourseRepository.class);
        CourseCategoryRepository categoryRepository = mock(CourseCategoryRepository.class);
        chapterRepository = mock(CourseChapterRepository.class);
        videoRepository = mock(VideoRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        CourseReviewRepository reviewRepository = mock(CourseReviewRepository.class);
        enrollmentRepository = mock(EnrollmentRepository.class);
        PluginGrantRepository pluginGrantRepository = mock(PluginGrantRepository.class);
        learningProgressRepository = mock(LearningProgressRepository.class);
        discussionCommentRepository = mock(DiscussionCommentRepository.class);
        courseNoteRepository = mock(CourseNoteRepository.class);
        videoBookmarkRepository = mock(VideoBookmarkRepository.class);
        exerciseRepository = mock(ExerciseRepository.class);
        discussionPostRepository = mock(DiscussionPostRepository.class);
        courseSlideMapper = mock(CourseSlideMapper.class);
        slidePageMapper = mock(SlidePageMapper.class);
        CourseAuditService auditService = mock(CourseAuditService.class);
        CourseStateMachine courseStateMachine = mock(CourseStateMachine.class);
        DomainEventPublisher domainEventPublisher = mock(DomainEventPublisher.class);
        hermesCourseMappingRepository = mock(HermesCourseMappingRepository.class);

        service = new CourseAdminServiceImpl(
                courseRepository,
                categoryRepository,
                chapterRepository,
                videoRepository,
                userRepository,
                reviewRepository,
                enrollmentRepository,
                pluginGrantRepository,
                learningProgressRepository,
                discussionCommentRepository,
                courseNoteRepository,
                videoBookmarkRepository,
                exerciseRepository,
                discussionPostRepository,
                courseSlideMapper,
                slidePageMapper,
                auditService,
                courseStateMachine,
                new com.fasterxml.jackson.databind.ObjectMapper(),
                domainEventPublisher,
                hermesCourseMappingRepository
        );
        ReflectionTestUtils.setField(service, "uploadBaseDir", tempDir.toString());

        securityUtilMock = mockStatic(SecurityUtil.class);
        securityUtilMock.when(() -> SecurityUtil.isOwnerOrAdmin(88L)).thenReturn(true);

        when(enrollmentRepository.selectCount(any())).thenReturn(0L);
        when(courseRepository.update(eq(null), any())).thenReturn(1);
        when(discussionPostRepository.selectList(any())).thenReturn(Collections.emptyList());
        when(videoRepository.selectList(any())).thenReturn(Collections.emptyList());
        when(hermesCourseMappingRepository.selectOne(any())).thenReturn(null);
    }

    @AfterEach
    void tearDown() {
        securityUtilMock.close();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void deleteShouldRemoveCoverOnlyAfterCommit() throws Exception {
        Path coverDir = tempDir.resolve("covers");
        Files.createDirectories(coverDir);
        Path coverFile = coverDir.resolve("course-cover.jpg");
        Files.writeString(coverFile, "cover");

        Course course = buildCourse(1L, "covers/course-cover.jpg");
        when(courseRepository.selectById(1L)).thenReturn(course);

        TransactionSynchronizationManager.initSynchronization();

        service.delete(1L);

        assertTrue(Files.exists(coverFile));
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }
        assertFalse(Files.exists(coverFile));
    }

    @Test
    void deleteShouldSkipUnsafeCoverPath() throws Exception {
        Path outsideFile = tempDir.getParent().resolve("outside-cover.jpg");
        Files.writeString(outsideFile, "cover");

        Course course = buildCourse(2L, "../outside-cover.jpg");
        when(courseRepository.selectById(2L)).thenReturn(course);

        TransactionSynchronizationManager.initSynchronization();

        service.delete(2L);

        assertTrue(Files.exists(outsideFile));
        assertTrue(TransactionSynchronizationManager.getSynchronizations().isEmpty());
    }

    private Course buildCourse(Long courseId, String coverUrl) {
        Course course = new Course();
        course.setId(courseId);
        course.setTeacherId(88L);
        course.setStatus(CourseStatus.DRAFT.getCode());
        course.setCoverUrl(coverUrl);
        course.setUpdatedAt(LocalDateTime.now());
        return course;
    }
}
