package com.microcourse.service;
import com.microcourse.dto.microSpecialty.*;
import java.util.List;
public interface MicroSpecialtyEnrollmentService {
    MicroSpecialtyEnrollmentVO apply(Long microSpecialtyId);
    void approve(Long enrollmentId); void reject(Long enrollmentId, String reason);
    void classImport(Long msId, Long classId);
    void drop(Long enrollmentId, Boolean cascadeDropCourses);
    void reapply(Long enrollmentId);
    List<MicroSpecialtyEnrollmentVO> getMyEnrollments();
    List<MicroSpecialtyEnrollmentVO> getSpecialtyEnrollments(Long msId);
    MicroSpecialtyEnrollmentProgressVO getMyProgress(Long msId);
    void issueCertificate(Long enrollmentId);
    void aggregateProgress();
}
