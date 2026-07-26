package com.microcourse.service;

import com.microcourse.BaseIntegrationTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.springframework.test.context.jdbc.Sql;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P1: null安全与ACADEMIC角色断裂回归测试
 *
 * 覆盖范围:
 *   - EnrollmentServiceImpl.assertCourseOwnership (TEACHER + cross-teacher)
 *   - LearningProgressServiceImpl.assertTeacherOwnsCourse / getProgressWithGuard / getCourseCompletionWithGuard
 *   - GradeServiceImpl.getById
 *
 * 角色矩阵:
 *   ACADEMIC → 任意进度/完成度/成绩 200
 *   TEACHER  → 自己课程 200，他课 403
 *   STUDENT  → 本人 200
 *   ADMIN    → 任意 200 (基准参照)
 *
 * 种子数据: p0-seed.sql + p1-academic-role-seed.sql（ACADEMIC用户、同校教师课程、跨教师课程、选课+成绩记录）
 */
@DisplayName("P1: null安全与ACADEMIC角色断裂回归测试")
@Sql(scripts = {"/sql/p0-seed.sql", "/sql/p1-academic-role-seed.sql"},
     executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
public class NullSafetyAndAcademicRoleRegressionTest extends BaseIntegrationTest {

    private String adminToken;        // id=1,  ADMIN
    private String academicToken;     // id=100, ACADEMIC
    private String teacherToken;      // id=6,  TEACHER (owns courses 1-4)
    private String otherTeacherToken; // id=22, TEACHER (owns course 6)
    private String studentToken;      // id=7,  STUDENT

    // 课程: 1→teacher_id=6, 5→teacher_id=6 (同 teacherToken), 6→teacher_id=22

    @BeforeEach
    void setupTokens() throws Exception {
        adminToken = bearerAdmin();
        academicToken = "Bearer " + loginAs("academic_user", "student123");
        teacherToken = "Bearer " + loginAs("p0_teacher", "student123");
        otherTeacherToken = "Bearer " + loginAs("invite_teacher", "student123");
        studentToken = "Bearer " + loginAs("student", "student123");
    }

    // ────────────────────────────────────────────────────────────────
    // 1. LearningProgress: getProgressWithGuard
    //    GET /api/learning-progress/progress?userId={target}&courseId={course}
    // ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getProgressWithGuard (GET /api/learning-progress/progress)")
    class ProgressGuardTest {

        @Test
        @DisplayName("ACADEMIC → 查看其他学生进度 → 200")
        void academicCanViewAnyProgress() throws Exception {
            mockMvc.perform(get("/api/learning-progress/progress?userId=7&courseId=1")
                            .header("Authorization", academicToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("ACADEMIC → 查看同校教师课程(teacher_id=6)学生进度 → 200")
        void academicCanViewPeerTeacherCourseProgress() throws Exception {
            mockMvc.perform(get("/api/learning-progress/progress?userId=7&courseId=5")
                            .header("Authorization", academicToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("TEACHER → 查看自己课程学生进度 → 200")
        void teacherCanViewOwnCourseProgress() throws Exception {
            mockMvc.perform(get("/api/learning-progress/progress?userId=7&courseId=1")
                            .header("Authorization", teacherToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("OTHER_TEACHER → 查看同校教师课程(teacher_id=6)学生进度 → 403")
        void otherTeacherCannotViewPeerTeacherCourseProgress() throws Exception {
            mockMvc.perform(get("/api/learning-progress/progress?userId=7&courseId=5")
                            .header("Authorization", otherTeacherToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("TEACHER → 查看其他教师课程学生进度 → 403")
        void teacherCannotViewOtherTeacherCourseProgress() throws Exception {
            // teacherToken(id=6) 查看 course 6(teacher_id=22) → 403
            mockMvc.perform(get("/api/learning-progress/progress?userId=7&courseId=6")
                            .header("Authorization", teacherToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("OTHER_TEACHER → 查看自己课程(他是课主)学生进度 → 200")
        void otherTeacherCanViewOwnCourseProgress() throws Exception {
            mockMvc.perform(get("/api/learning-progress/progress?userId=7&courseId=6")
                            .header("Authorization", otherTeacherToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("STUDENT → 查看本人进度（已选课） → 200")
        void studentCanViewOwnProgress() throws Exception {
            mockMvc.perform(get("/api/learning-progress/progress?userId=7&courseId=1")
                            .header("Authorization", studentToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("ADMIN → 查看任意进度 → 200")
        void adminCanViewAnyProgress() throws Exception {
            mockMvc.perform(get("/api/learning-progress/progress?userId=7&courseId=5")
                            .header("Authorization", adminToken))
                    .andExpect(status().isOk());
        }
    }

    // ────────────────────────────────────────────────────────────────
    // 2. LearningProgress: getCourseCompletionWithGuard
    //    GET /api/learning-progress/progress/completion?userId={target}&courseId={course}
    // ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getCourseCompletionWithGuard (GET /api/learning-progress/progress/completion)")
    class CompletionGuardTest {

        @Test
        @DisplayName("ACADEMIC → 查看其他学生完成度 → 200")
        void academicCanViewAnyCompletion() throws Exception {
            mockMvc.perform(get("/api/learning-progress/progress/completion?userId=7&courseId=1")
                            .header("Authorization", academicToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("ACADEMIC → 查看同校教师课程(teacher_id=6)完成度 → 200")
        void academicCanViewCompletionPeerTeacherCourse() throws Exception {
            mockMvc.perform(get("/api/learning-progress/progress/completion?userId=7&courseId=5")
                            .header("Authorization", academicToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("TEACHER → 查看自己课程完成度 → 200")
        void teacherCanViewOwnCourseCompletion() throws Exception {
            mockMvc.perform(get("/api/learning-progress/progress/completion?userId=7&courseId=1")
                            .header("Authorization", teacherToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("TEACHER → 查看其他教师课程完成度 → 403")
        void teacherCannotViewOtherTeacherCourseCompletion() throws Exception {
            mockMvc.perform(get("/api/learning-progress/progress/completion?userId=7&courseId=6")
                            .header("Authorization", teacherToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("OTHER_TEACHER → 查看同校教师课程(teacher_id=6)完成度 → 403")
        void otherTeacherCannotViewPeerTeacherCourseCompletion() throws Exception {
            mockMvc.perform(get("/api/learning-progress/progress/completion?userId=7&courseId=5")
                            .header("Authorization", otherTeacherToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("STUDENT → 查看本人完成度 → 200")
        void studentCanViewOwnCompletion() throws Exception {
            mockMvc.perform(get("/api/learning-progress/progress/completion?userId=7&courseId=1")
                            .header("Authorization", studentToken))
                    .andExpect(status().isOk());
        }
    }

    // ────────────────────────────────────────────────────────────────
    // 3. Grade: getById
    //    GET /api/grades/{id}
    //    grade 999001 → course 5 (teacher_id=6)     → 同校教师越权场景
    //    grade 999002 → course 1 (teacher_id=6)     → 正常课主场景
    // ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Grade getById (GET /api/grades/{id})")
    class GradeGetByIdTest {

        @Test
        @DisplayName("ACADEMIC → 查看任意成绩详情（含同校教师课程course 5）→ 200")
        void academicCanViewAnyGrade() throws Exception {
            // grade 999001: course 5 (teacher_id=6)
            mockMvc.perform(get("/api/grades/999001")
                            .header("Authorization", academicToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("ADMIN → 查看任意成绩详情 → 200")
        void adminCanViewAnyGrade() throws Exception {
            mockMvc.perform(get("/api/grades/999001")
                            .header("Authorization", adminToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("STUDENT → 查看本人成绩详情 → 200")
        void studentCanViewOwnGrade() throws Exception {
            mockMvc.perform(get("/api/grades/999001")
                            .header("Authorization", studentToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("TEACHER → 查看自己课程学生成绩详情 → 200")
        void teacherCanViewOwnCourseGrade() throws Exception {
            // grade 999001: course 5 (teacher_id=6), teacher(id=6) is owner → 200
            mockMvc.perform(get("/api/grades/999001")
                            .header("Authorization", teacherToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("OTHER_TEACHER → 查看同校教师课程成绩(teacher_id=6) → 403")
        void otherTeacherCannotViewPeerTeacherCourseGrade() throws Exception {
            // grade 999001: course 5 (teacher_id=6), otherTeacher(id=22) not owner → 403
            mockMvc.perform(get("/api/grades/999001")
                            .header("Authorization", otherTeacherToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("TEACHER → 查看其他教师课程成绩 → 403")
        void teacherCannotViewOtherTeacherCourseGrade() throws Exception {
            // grade 999002: course 6 (teacher_id=22), teacher(id=6) not owner → 403
            mockMvc.perform(get("/api/grades/999002")
                            .header("Authorization", teacherToken))
                    .andExpect(status().isForbidden());
        }
    }

    // ────────────────────────────────────────────────────────────────
    // 4. assertCourseOwnership (间接验证)
    //    EnrollmentServiceImpl.getCourseEnrollmentsWithOwnerCheck
    //    EnrollmentServiceImpl.getCourseEnrollmentPage
    //    GET /api/enrollments/course/{courseId}
    // ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("assertCourseOwnership (GET /api/enrollments/course/{id})")
    class CourseOwnershipTest {

        @Test
        @DisplayName("TEACHER → 查看自己课程学员列表 → 200")
        void teacherCanViewOwnCourseEnrollments() throws Exception {
            mockMvc.perform(get("/api/enrollments/course/1?page=0&size=10")
                            .header("Authorization", teacherToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("OTHER_TEACHER → 查看同校教师课程(teacher_id=6)学员 → 403")
        void otherTeacherCannotViewPeerTeacherCourseEnrollments() throws Exception {
            mockMvc.perform(get("/api/enrollments/course/5?page=0&size=10")
                            .header("Authorization", otherTeacherToken))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("ACADEMIC → 查看任意课程学员列表 → 200")
        void academicCanViewAnyCourseEnrollments() throws Exception {
            mockMvc.perform(get("/api/enrollments/course/5?page=0&size=10")
                            .header("Authorization", academicToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("ADMIN → 查看任意课程学员列表 → 200")
        void adminCanViewAnyCourseEnrollments() throws Exception {
            mockMvc.perform(get("/api/enrollments/course/5?page=0&size=10")
                            .header("Authorization", adminToken))
                    .andExpect(status().isOk());
        }
    }
}
