package com.microcourse.service;

import com.microcourse.BaseIntegrationTest;
import com.microcourse.dto.PendingTaskVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("教师待办口径一致性测试")
class TeacherPendingTasksConsistencyTest extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TeacherService teacherService;

    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<Long> createdCourseIds = new ArrayList<>();
    private final List<Long> createdCategoryIds = new ArrayList<>();
    private final List<Long> createdExerciseIds = new ArrayList<>();
    private final List<Long> createdExerciseRecordIds = new ArrayList<>();
    private final List<Long> createdPostIds = new ArrayList<>();
    private final List<Long> createdCommentIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (Long id : createdCommentIds) {
            try { jdbc.update("DELETE FROM discussion_comments WHERE id = ?", id); } catch (Exception ignored) {}
        }
        for (Long id : createdPostIds) {
            try { jdbc.update("DELETE FROM discussion_posts WHERE id = ?", id); } catch (Exception ignored) {}
        }
        for (Long id : createdExerciseRecordIds) {
            try { jdbc.update("DELETE FROM exercise_records WHERE id = ?", id); } catch (Exception ignored) {}
        }
        for (Long id : createdExerciseIds) {
            try { jdbc.update("DELETE FROM exercises WHERE id = ?", id); } catch (Exception ignored) {}
        }
        for (Long id : createdCourseIds) {
            try { jdbc.update("DELETE FROM courses WHERE id = ?", id); } catch (Exception ignored) {}
        }
        for (Long id : createdUserIds) {
            try { jdbc.update("DELETE FROM users WHERE id = ?", id); } catch (Exception ignored) {}
        }
        for (Long id : createdCategoryIds) {
            try { jdbc.update("DELETE FROM course_categories WHERE id = ?", id); } catch (Exception ignored) {}
        }
        SecurityContextHolder.clearContext();
    }

    private Long insertCategory() {
        Long id = jdbc.queryForObject(
                "INSERT INTO course_categories(name, level, sort_order, created_at, updated_at) " +
                        "VALUES (?, 1, 0, now(), now()) RETURNING id",
                Long.class,
                "pending-task-cat-" + System.nanoTime()
        );
        createdCategoryIds.add(id);
        return id;
    }

    private Long insertUser(String role) {
        Long id = jdbc.queryForObject(
                "INSERT INTO users(username, password, real_name, role, status, cas_bound, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, 1, false, now(), now()) RETURNING id",
                Long.class,
                "pending-task-" + role.toLowerCase() + "-" + System.nanoTime(),
                "$2b$12$abcdefghijklmnopqrstuvabcdefghijklmnopqrstuv",
                "待办测试-" + role,
                role
        );
        createdUserIds.add(id);
        return id;
    }

    private Long insertCourse(Long categoryId, Long teacherId) {
        Long id = jdbc.queryForObject(
                "INSERT INTO courses(title, category_id, teacher_id, status, is_free, price, course_type, version, created_at, updated_at) " +
                        "VALUES (?, ?, ?, 4, true, 0, 'VIDEO', 0, now(), now()) RETURNING id",
                Long.class,
                "pending-task-course-" + System.nanoTime(),
                categoryId,
                teacherId
        );
        createdCourseIds.add(id);
        return id;
    }

    private Long insertExercise(Long courseId) {
        Long id = jdbc.queryForObject(
                "INSERT INTO exercises(course_id, title, pass_score, time_limit, max_attempts, total_score, question_count, version, created_at, updated_at) " +
                        "VALUES (?, ?, 60, 0, 1, 100, 1, 0, now(), now()) RETURNING id",
                Long.class,
                courseId,
                "pending-task-exercise-" + System.nanoTime()
        );
        createdExerciseIds.add(id);
        return id;
    }

    private void insertExerciseRecord(Long exerciseId, Long studentId, int attemptNo, Integer score, String submittedAtExpr) {
        Long id = jdbc.queryForObject(
                "INSERT INTO exercise_records(exercise_id, user_id, attempt_no, score, total_score, passed, duration, answers, submitted_at, version) " +
                        "VALUES (?, ?, ?, ?, 100, ?, 120, '{}', " + submittedAtExpr + ", 0) RETURNING id",
                Long.class,
                exerciseId,
                studentId,
                attemptNo,
                score,
                score != null && score >= 60
        );
        createdExerciseRecordIds.add(id);
    }

    private Long insertPost(Long courseId, Long studentId, String title, String createdAtExpr) {
        Long id = jdbc.queryForObject(
                "INSERT INTO discussion_posts(course_id, user_id, title, content, is_anonymous, is_pinned, is_essence, comment_count, like_count, status, created_at, updated_at) " +
                        "VALUES (?, ?, ?, '测试内容', false, false, false, 0, 0, 1, " + createdAtExpr + ", " + createdAtExpr + ") RETURNING id",
                Long.class,
                courseId,
                studentId,
                title
        );
        createdPostIds.add(id);
        return id;
    }

    private void insertTeacherReply(Long postId, Long teacherId) {
        Long id = jdbc.queryForObject(
                "INSERT INTO discussion_comments(post_id, user_id, content, is_teacher_reply, like_count, status, created_at, updated_at) " +
                        "VALUES (?, ?, '教师已回复', true, 0, 1, now(), now()) RETURNING id",
                Long.class,
                postId,
                teacherId
        );
        createdCommentIds.add(id);
    }

    @Test
    @DisplayName("getPendingTasks 仅返回未批改练习和未回复讨论帖")
    void getPendingTasksOnlyReturnsUnresolvedItems() {
        Long categoryId = insertCategory();
        Long teacherId = insertUser("TEACHER");
        Long studentId = insertUser("STUDENT");
        Long courseId = insertCourse(categoryId, teacherId);
        Long exerciseId = insertExercise(courseId);

        insertExerciseRecord(exerciseId, studentId, 1, null, "now() - interval '2 hour'");
        insertExerciseRecord(exerciseId, studentId, 2, 88, "now() - interval '1 hour'");

        insertPost(courseId, studentId, "待回复讨论帖", "now() - interval '3 hour'");
        Long repliedPostId = insertPost(courseId, studentId, "已回复讨论帖", "now() - interval '30 minute'");
        insertTeacherReply(repliedPostId, teacherId);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(teacherId, null, List.of())
        );

        List<PendingTaskVO> tasks = teacherService.getPendingTasks(teacherId, 10);

        long homeworkCount = tasks.stream().filter(task -> "练习批改".equals(task.getType())).count();
        long questionCount = tasks.stream().filter(task -> "学员提问".equals(task.getType())).count();

        assertEquals(1, homeworkCount, "已批改练习不应继续出现在教师待办中");
        assertEquals(1, questionCount, "已回复讨论帖不应继续出现在教师待办中");
        assertTrue(tasks.stream().anyMatch(task -> "待回复讨论帖".equals(task.getTitle())), "未回复讨论帖应保留在待办中");
        assertFalse(tasks.stream().anyMatch(task -> "已回复讨论帖".equals(task.getTitle())), "已回复讨论帖应从待办中移除");
        assertTrue(tasks.stream().anyMatch(task -> "学员练习待批改".equals(task.getTitle())), "未批改练习应保留在待办中");
    }
}
