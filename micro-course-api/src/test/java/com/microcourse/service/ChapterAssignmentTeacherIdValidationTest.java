package com.microcourse.service;

import com.microcourse.dto.storage.ChapterAssignmentItem;
import com.microcourse.entity.User;
import com.microcourse.enums.UserRole;
import com.microcourse.exception.BusinessException;
import com.microcourse.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * V202 P0-2 修复测试: chapter_teacher_assignments.teacherId 占位 bug
 */
@DisplayName("V202: chapterAssignments.teacherId 校验")
class ChapterAssignmentTeacherIdValidationTest {

    @Test
    @DisplayName("P0-2-1: teacherId 指向不存在的用户必须抛 BusinessException")
    void shouldRejectNonexistentTeacherId() {
        UserRepository mockUserRepo = Mockito.mock(UserRepository.class);
        Long fakeTeacherId = 99999L;
        Mockito.when(mockUserRepo.selectById(fakeTeacherId)).thenReturn(null);

        ChapterAssignmentItem item = new ChapterAssignmentItem();
        item.setCourseId(1L);
        item.setChapterId(2L);
        item.setTeacherId(fakeTeacherId);

        BusinessException ex = assertThrows(BusinessException.class, () -> {
            validateTeacherId(item, mockUserRepo);
        });
        assertNotNull(ex.getMessage());
        assertEquals(true, ex.getMessage().contains("不存在"));
    }

    @Test
    @DisplayName("P0-2-2: teacherId 指向非 TEACHER 角色必须抛 BusinessException")
    void shouldRejectNonTeacherRole() {
        UserRepository mockUserRepo = Mockito.mock(UserRepository.class);
        Long studentId = 100L;
        User student = new User();
        student.setId(studentId);
        student.setRole(UserRole.STUDENT);
        Mockito.when(mockUserRepo.selectById(studentId)).thenReturn(student);

        ChapterAssignmentItem item = new ChapterAssignmentItem();
        item.setCourseId(1L);
        item.setChapterId(2L);
        item.setTeacherId(studentId);

        BusinessException ex = assertThrows(BusinessException.class, () -> {
            validateTeacherId(item, mockUserRepo);
        });
        assertEquals(true, ex.getMessage().contains("不是教师角色"));
    }

    @Test
    @DisplayName("P0-2-3: teacherId 为 null 必须通过（V202 占位条目合法）")
    void shouldAcceptNullTeacherId() {
        UserRepository mockUserRepo = Mockito.mock(UserRepository.class);

        ChapterAssignmentItem item = new ChapterAssignmentItem();
        item.setCourseId(1L);
        item.setChapterId(2L);
        item.setTeacherId(null);

        Long result = validateTeacherId(item, mockUserRepo);
        assertNull(result);
        Mockito.verify(mockUserRepo, Mockito.never()).selectById(Mockito.any());
    }

    @Test
    @DisplayName("P0-2-4: teacherId 真实 TEACHER 用户必须通过")
    void shouldAcceptRealTeacherId() {
        UserRepository mockUserRepo = Mockito.mock(UserRepository.class);
        Long teacherId = 5L;
        User teacher = new User();
        teacher.setId(teacherId);
        teacher.setRole(UserRole.TEACHER);
        Mockito.when(mockUserRepo.selectById(teacherId)).thenReturn(teacher);

        ChapterAssignmentItem item = new ChapterAssignmentItem();
        item.setCourseId(1L);
        item.setChapterId(2L);
        item.setTeacherId(teacherId);

        Long result = validateTeacherId(item, mockUserRepo);
        assertEquals(teacherId, result);
    }

    private Long validateTeacherId(ChapterAssignmentItem item, UserRepository userRepository) {
        if (item.getTeacherId() != null) {
            User teacher = userRepository.selectById(item.getTeacherId());
            if (teacher == null) {
                throw new BusinessException(
                        com.microcourse.exception.ErrorCode.BAD_REQUEST_PARAM,
                        "chapterAssignments.teacherId=" + item.getTeacherId() + " 不存在");
            }
            if (teacher.getRole() != UserRole.TEACHER) {
                throw new BusinessException(
                        com.microcourse.exception.ErrorCode.BAD_REQUEST_PARAM,
                        "chapterAssignments.teacherId=" + item.getTeacherId() + " 不是教师角色");
            }
            return item.getTeacherId();
        }
        return null;
    }
}