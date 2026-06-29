package com.microcourse.scheduled;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.microcourse.entity.MicroSpecialty;
import com.microcourse.entity.MicroSpecialtyEnrollment;
import com.microcourse.repository.MicroSpecialtyEnrollmentRepository;
import com.microcourse.repository.MicroSpecialtyRepository;
import com.microcourse.service.MicroSpecialtyEnrollmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 微专业进度聚合定时任务。
 * 每日 02:00 触发，分批处理 IN_PROGRESS 的 enrollment，
 * 按 §6.8 算法计算进度并判定完成/失败。
 */
@Component
public class MicroSpecialtyProgressAggregator {

    private static final Logger log = LoggerFactory.getLogger(MicroSpecialtyProgressAggregator.class);
    private static final int BATCH_SIZE = 100;

    // CON-002 修复: 防止定时任务重叠执行
    private volatile boolean running = false;

    private final MicroSpecialtyEnrollmentRepository enrollmentRepository;
    private final MicroSpecialtyEnrollmentService enrollmentService;
    private final MicroSpecialtyRepository msRepository;

    public MicroSpecialtyProgressAggregator(MicroSpecialtyEnrollmentRepository enrollmentRepository,
                                            MicroSpecialtyEnrollmentService enrollmentService,
                                            MicroSpecialtyRepository msRepository) {
        this.enrollmentRepository = enrollmentRepository;
        this.enrollmentService = enrollmentService;
        this.msRepository = msRepository;
    }

    /**
     * 每日凌晨 2:00 聚合所有进行中的修读记录进度。
     * 分批处理，乐观锁保护，进度数据写入 enrollment 表。
     *
     * <p>注意：本方法故意不加 @Transactional。
     * 每个 enrollment 通过 enrollmentService.aggregateProgress() 独立事务处理，
     * 避免大批量处理导致事务过大，以及死锁风险。
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void aggregateAll() {
        // CON-002 修复: 防止重叠执行
        if (running) {
            log.warn("[MS-Progress] 上一轮聚合仍在执行，跳过本轮");
            return;
        }
        running = true;
        try {
            doAggregateAll();
        } finally {
            running = false;
        }
    }

    private void doAggregateAll() {
        log.info("[MS-Progress] 开始聚合微专业进度");

        long offset = 0;
        int totalProcessed = 0;
        int completed = 0;
        int failed = 0;
        Set<Long> affectedMsIds = new HashSet<>();

        while (true) {
            // 分批查 IN_PROGRESS 和 APPROVED enrollment
            List<MicroSpecialtyEnrollment> batch = enrollmentRepository.selectList(
                    new LambdaQueryWrapper<MicroSpecialtyEnrollment>()
                            .in(MicroSpecialtyEnrollment::getStatus, "APPROVED", "IN_PROGRESS")
                            .orderByAsc(MicroSpecialtyEnrollment::getId)
                            .last("LIMIT " + BATCH_SIZE + " OFFSET " + offset));

            if (batch.isEmpty()) break;

            Set<Long> batchIds = new HashSet<>();
            for (MicroSpecialtyEnrollment en : batch) {
                try {
                    // 首次处理 APPROVED → 自动转为 IN_PROGRESS（version 乐观锁）
                    if ("APPROVED".equals(en.getStatus())) {
                        enrollmentRepository.update(null,
                                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<MicroSpecialtyEnrollment>()
                                        .eq(MicroSpecialtyEnrollment::getId, en.getId())
                                        .eq(MicroSpecialtyEnrollment::getStatus, "APPROVED")
                                        .eq(MicroSpecialtyEnrollment::getVersion, en.getVersion())
                                        .set(MicroSpecialtyEnrollment::getStatus, "IN_PROGRESS")
                                        .setSql("version = version + 1"));
                    }

                    enrollmentService.aggregateProgress(en.getId());
                    totalProcessed++;
                    if (en.getMicroSpecialtyId() != null) {
                        affectedMsIds.add(en.getMicroSpecialtyId());
                    }
                    batchIds.add(en.getId());
                } catch (Exception e) {
                    // ERR-002 修复: 异常升级为 error 日志 + count 追踪
                    failed++;
                    log.error("[MS-Progress] 聚合失败 enrollmentId={}: {}", en.getId(), e.getMessage(), e);
                }
            }

            // RES-003 修复: 批量查询替代 N+1 逐条 selectById
            if (!batchIds.isEmpty()) {
                List<MicroSpecialtyEnrollment> updatedBatch = enrollmentRepository.selectBatchIds(batchIds);
                for (MicroSpecialtyEnrollment updated : updatedBatch) {
                    if ("COMPLETED".equals(updated.getStatus())) completed++;
                    if ("FAILED".equals(updated.getStatus())) failed++;
                }
            }

            offset += BATCH_SIZE;
            if (batch.size() < BATCH_SIZE) break;
        }

        // Recalculate student_count for all affected micro specialties
        for (Long msId : affectedMsIds) {
            try {
                Long count = enrollmentRepository.selectCount(
                        new LambdaQueryWrapper<MicroSpecialtyEnrollment>()
                                .eq(MicroSpecialtyEnrollment::getMicroSpecialtyId, msId)
                                .in(MicroSpecialtyEnrollment::getStatus, "APPROVED", "IN_PROGRESS"));
                LambdaUpdateWrapper<MicroSpecialty> uw = new LambdaUpdateWrapper<MicroSpecialty>()
                        .eq(MicroSpecialty::getId, msId)
                        .set(MicroSpecialty::getStudentCount, count.intValue());
                msRepository.update(null, uw);
            } catch (Exception e) {
                // ERR-002 修复: 异常升级为 error 日志
                log.error("[MS-Progress] student_count recalibration failed msId={}: {}", msId, e.getMessage(), e);
            }
        }

        log.info("[MS-Progress] 聚合完成: total={}, completed={}, failed={}", totalProcessed, completed, failed);
    }
}
