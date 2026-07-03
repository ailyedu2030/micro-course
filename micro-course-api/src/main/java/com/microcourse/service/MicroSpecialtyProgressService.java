package com.microcourse.service;

/**
 * 微专业修读进度聚合 Service 接口。
 * 职责：按 enrollment 计算进度，按 completionRule 判定 COMPLETED 或 FAILED，自动颁发证书。
 */
public interface MicroSpecialtyProgressService {

    /**
     * 聚合单个修读记录的进度（供 cron 调用）。
     *
     * <p>自动转换: APPROVED → IN_PROGRESS（version 乐观锁保护）。
     * <p>判定完成: 根据 spec §6.8 公式计算 progress/creditsEarned/coursesCompleted/finalScore/finalGrade，
     *   按 completionRule 判定 COMPLETED 或 FAILED。
     * <p>颁发证书: COMPLETED → issueCertificate（幂等）。
     * <p>并发安全: 每次 UPDATE 都带 version 条件；rows==0 时仅 log warn 不抛异常（cron 容错）。
     *
     * @param enrollmentId 修读记录 ID
     */
    void aggregateProgress(Long enrollmentId);
}
