package com.microcourse.plugin.interactive;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.plugin.interactive.dto.TtsStatusResponse;
import com.microcourse.plugin.interactive.mapper.SlidePageMapper;
import com.microcourse.plugin.interactive.service.impl.TtsServiceImpl;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.CourseSectionRepository;
import com.microcourse.repository.EnrollmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class TtsServiceTaskStatePersistenceTest {

    @TempDir
    Path tempDir;

    private TtsServiceImpl ttsService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        SlidePageMapper slidePageMapper = mock(SlidePageMapper.class);
        CourseRepository courseRepository = mock(CourseRepository.class);
        CourseSectionRepository courseSectionRepository = mock(CourseSectionRepository.class);
        EnrollmentRepository enrollmentRepository = mock(EnrollmentRepository.class);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        ExecutorService executorService = mock(ExecutorService.class);

        objectMapper = new ObjectMapper();
        ttsService = new TtsServiceImpl(
                slidePageMapper,
                courseRepository,
                enrollmentRepository,
                courseSectionRepository,
                transactionTemplate,
                objectMapper,
                executorService
        );
        ReflectionTestUtils.setField(ttsService, "storagePath", tempDir.toString());
    }

    @Test
    void getSectionTtsStatusShouldReadPersistedTaskState() throws Exception {
        writeTaskState(52L, "tts-completed", Map.of(
                "taskId", "tts-completed",
                "courseId", 52L,
                "sectionId", 650L,
                "estimatedSeconds", 45,
                "status", "completed",
                "segments", List.of(Map.of(
                        "page", 1,
                        "url", "/audio/1.mp3",
                        "duration", 12,
                        "size", 1024
                )),
                "mergedAudioUrl", "/audio/merged.mp3",
                "totalDuration", 12,
                "completedAt", System.currentTimeMillis(),
                "updatedAt", System.currentTimeMillis()
        ));

        TtsStatusResponse response = ttsService.getSectionTtsStatus(52L, 650L, "tts-completed");

        assertEquals("completed", response.getStatus());
        assertEquals("/audio/merged.mp3", response.getMergedAudioUrl());
        assertEquals(12L, response.getTotalDuration());
        assertNotNull(response.getSegments());
        assertEquals(1, response.getSegments().size());
        assertEquals(1, response.getSegments().get(0).getPage());
    }

    @Test
    void getSectionTtsStatusShouldRejectInvalidTaskId() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> ttsService.getSectionTtsStatus(52L, 650L, "../bad"));

        assertEquals(ErrorCode.BAD_REQUEST_PARAM.getCode(), ex.getCode());
    }

    @Test
    void cleanupExpiredTaskStatesShouldDeleteOnlyExpiredCompletedFiles() throws Exception {
        writeTaskState(52L, "tts-expired", Map.of(
                "taskId", "tts-expired",
                "courseId", 52L,
                "sectionId", 650L,
                "estimatedSeconds", 30,
                "status", "completed",
                "completedAt", System.currentTimeMillis() - (31 * 60 * 1000L),
                "updatedAt", System.currentTimeMillis() - (31 * 60 * 1000L)
        ));
        writeTaskState(52L, "tts-fresh", Map.of(
                "taskId", "tts-fresh",
                "courseId", 52L,
                "sectionId", 651L,
                "estimatedSeconds", 30,
                "status", "completed",
                "completedAt", System.currentTimeMillis(),
                "updatedAt", System.currentTimeMillis()
        ));
        writeTaskState(53L, "tts-queued", Map.of(
                "taskId", "tts-queued",
                "courseId", 53L,
                "sectionId", 652L,
                "estimatedSeconds", 60,
                "status", "queued",
                "completedAt", 0,
                "updatedAt", System.currentTimeMillis()
        ));

        Path expired = taskFile(52L, "tts-expired");
        Path fresh = taskFile(52L, "tts-fresh");
        Path queued = taskFile(53L, "tts-queued");

        ttsService.cleanupExpiredTaskStates();

        assertFalse(Files.exists(expired));
        assertTrue(Files.exists(fresh));
        assertTrue(Files.exists(queued));
    }

    private void writeTaskState(Long courseId, String taskId, Map<String, Object> state) throws Exception {
        Path taskFile = taskFile(courseId, taskId);
        Files.createDirectories(taskFile.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(taskFile.toFile(), state);
    }

    private Path taskFile(Long courseId, String taskId) {
        return tempDir.resolve(String.valueOf(courseId))
                .resolve("audio")
                .resolve("tasks")
                .resolve(taskId + ".json");
    }
}
