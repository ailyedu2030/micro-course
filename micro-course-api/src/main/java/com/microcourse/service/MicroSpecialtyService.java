package com.microcourse.service;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.microSpecialty.*;
public interface MicroSpecialtyService {
    PageResult<MicroSpecialtyVO> page(int page, int size, String statusFilter);
    MicroSpecialtySquareVO getSquareData();
    MicroSpecialtyDetailVO getById(Long id);
    MicroSpecialtyVO create(MicroSpecialtyCreateRequest request);
    MicroSpecialtyVO update(Long id, MicroSpecialtyUpdateRequest request);
    void delete(Long id);
    void submit(Long id); void approve(Long id); void reject(Long id, String reason);
    void open(Long id); void close(Long id); void cancel(Long id); void archive(Long id);
    MicroSpecialtyCourseVO addCourse(Long msId, MicroSpecialtyCourseRequest request);
    MicroSpecialtyCourseVO updateCourse(Long msId, Long itemId, MicroSpecialtyCourseRequest request);
    void removeCourse(Long msId, Long itemId);
    MicroSpecialtyTeacherVO inviteTeacher(Long msId, MicroSpecialtyTeacherRequest request);
    void removeTeacher(Long msId, Long teacherId);
    void reinviteTeacher(Long msId, Long teacherId);
    void applyFeatured(Long msId, String reason);
    void approveFeatured(Long msId); void rejectFeatured(Long msId);
    void setGoldFeatured(Long msId); void unsetGoldFeatured(Long msId);
    void transferLeadership(Long msId, Long newLeadTeacherId);
    boolean isLeadOf(Long msId, Long userId);
    boolean isMemberOf(Long msId, Long userId);
    MicroSpecialtyStatsVO getStats(Long msId);
}
