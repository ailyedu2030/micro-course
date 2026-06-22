package com.microcourse.service;

import com.microcourse.dto.PageResult;
import com.microcourse.dto.microSpecialty.MicroSpecialtyEnrollmentVO;

import java.util.List;

/**
 * 微专业修读 Service 接口。
 * 职责：报名 + 审批 + 班级导入 + 退出/重新申请 + 证书 + 进度聚合。
 */
public interface MicroSpecialtyEnrollmentService {

    /** 学生自主报名 → PENDING */
    MicroSpecialtyEnrollmentVO apply(Long msId);

    /** LEAD/ACADEMIC 审批通过 → APPROVED（自动 enroll 必修课，含前置检查） */
    MicroSpecialtyEnrollmentVO approve(Long id);

    /** LEAD/ACADEMIC 驳回报名 → REJECTED */
    void reject(Long id, String reason);

    /** 班级批量导入（ACADEMIC/ADMIN） → APPROVED */
    int classImport(Long msId, Long classId);

    /** 退出修读（STUDENT 本人/ADMIN） */
    void drop(Long id, boolean cascade, String reason);

    /** 重新申请（REJECTED/DROPPED/FAILED → PENDING） */
    MicroSpecialtyEnrollmentVO reapply(Long id);

    /** 我的修读列表 */
    List<MicroSpecialtyEnrollmentVO> getMyEnrollments();

    /** 微专业修读名单（LEAD/ACADEMIC） */
    PageResult<MicroSpecialtyEnrollmentVO> listEnrollments(Long msId, int page, int size, String status);

    /** 手动颁发证书 */
    void issueCertificate(Long enrollmentId);

    /** 进度聚合：cron 调用，按 enrollment 聚合必修课数据 */
    void aggregateProgress(Long enrollmentId);
}
