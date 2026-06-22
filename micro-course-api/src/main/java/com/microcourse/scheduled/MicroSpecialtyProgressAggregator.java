package com.microcourse.scheduled;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.entity.*;
import com.microcourse.enums.*;
import com.microcourse.repository.*;
import com.microcourse.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 微专业进度聚合定时任务。
 * 每日 02:00 触发，分批处理 IN_PROGRESS 的 enrollment，
 * 按 §6.8 算法计算进度并判定完成/失败。
 */
@Component
public class MicroSpecialtyProgressAggregator {

    private static final Logger log = LoggerFactory.getLogger(MicroSpecialtyProgressAggregator.class);
    private static final int BATCH_SIZE = 100;

    private final MicroSpecialtyEnrollmentRepository enrollmentRepository;
    private final MicroSpecialtyEnrollmentService enrollmentService;

    public MicroSpecialtyProgressAggregator(MicroSpecialtyEnrollmentRepository enrollmentRepository,
                                            MicroSpecialtyEnrollmentService enrollmentService) {
        this.enrollmentRepository = enrollmentRepository;
        this.enrollmentService = enrollmentService;
    }

    /**
     * 每日凌晨 2:00 聚合所有进行中的修读记录进度。
     * 分批处理，乐观锁保护，进度数据写入 enrollment 表。
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional(rollbackFor = Exception.class)
    public void aggregateAll() {
        log.info("[MS-Progress] 开始聚合微专业进度");

        long offset = 0;
        int totalProcessed = 0;
        int completed = 0;
        int failed = 0;

        while (true) {
            // 分批查 IN_PROGRESS 和 APPROVED enrollment
            List<MicroSpecialtyEnrollment> batch = enrollmentRepository.selectList(
                    new LambdaQueryWrapper<MicroSpecialtyEnrollment>()
                            .in(MicroSpecialtyEnrollment::getStatus, "APPROVED", "IN_PROGRESS")
                            .orderByAsc(MicroSpecialtyEnrollment::getId)
                            .last("LIMIT " + BATCH_SIZE + " OFFSET " + offset));

            if (batch.isEmpty()) break;

            for (MicroSpecialtyEnrollment en : batch) {
                try {
                    // 首次处理 APPROVED → 自动转为 IN_PROGRESS
                    if ("APPROVED".equals(en.getStatus())) {
                        enrollmentRepository.update(null,
                                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<MicroSpecialtyEnrollment>()
                                        .eq(MicroSpecialtyEnrollment::getId, en.getId())
                                        .eq(MicroSpecialtyEnrollment::getStatus, "APPROVED")
                                        .set(MicroSpecialtyEnrollment::getStatus, "IN_PROGRESS")
                                        .setSql("version = version + 1"));
                    }

                    enrollmentService.aggregateProgress(en.getId());
                    totalProcessed++;

                    // 检查是否刚完成
                    MicroSpecialtyEnrollment updated = enrollmentRepository.selectById(en.getId());
                    if (updated != null) {
                        if ("COMPLETED".equals(updated.getStatus())) completed++;
                        if ("FAILED".equals(updated.getStatus())) {
                            failed++;
                            // 通知学生
                            MicroSpecialty ms = null;
                            if (updated.getMicroSpecialtyId() != null) {
                                ms = new MicroSpecialty(); // just placeholder
                            }
                            // notification handled by aggregateProgress internally
                        }
                    }
                } catch (Exception e) {
                    log.warn("[MS-Progress] 聚合失败 enrollmentId={}: {}", en.getId(), e.getMessage());
                }
            }

            offset += BATCH_SIZE;
            if (batch.size() < BATCH_SIZE) break;
        }

        log.info("[MS-Progress] 聚合完成: total={}, completed={}, failed={}", totalProcessed, completed, failed);
    }
}
