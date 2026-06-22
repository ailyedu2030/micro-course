package com.microcourse.scheduled;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.entity.*;
import com.microcourse.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 微专业邀请过期扫描定时任务。
 * 每小时扫描 INVITED 过期记录 → DECLINED + 通知；
 * 同时告警 PENDING_ACADEMIC 超 14 天未处理的记录。
 */
@Component
public class MicroSpecialtyInviteExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(MicroSpecialtyInviteExpiryJob.class);
    private static final int BATCH_SIZE = 50;

    private final MicroSpecialtyTeacherRepository teacherRepository;
    private final MicroSpecialtyRepository msRepository;

    public MicroSpecialtyInviteExpiryJob(MicroSpecialtyTeacherRepository teacherRepository,
                                         MicroSpecialtyRepository msRepository) {
        this.teacherRepository = teacherRepository;
        this.msRepository = msRepository;
    }

    /**
     * 每小时整点扫描过期邀请。
     * 分批处理，乐观锁更新。
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void scanExpired() {
        log.debug("[MS-InviteExpiry] 开始扫描过期邀请");

        int totalExpired = 0;
        int totalStale = 0;

        // 1. 扫 INVITED 过期 → DECLINED
        List<MicroSpecialtyTeacher> expiredList = teacherRepository.selectList(
                new LambdaQueryWrapper<MicroSpecialtyTeacher>()
                        .eq(MicroSpecialtyTeacher::getInviteStatus, "INVITED")
                        .lt(MicroSpecialtyTeacher::getInviteExpiresAt, LocalDateTime.now()));

        for (int i = 0; i < expiredList.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, expiredList.size());
            for (int j = i; j < end; j++) {
                MicroSpecialtyTeacher record = expiredList.get(j);
                int affected = teacherRepository.update(null,
                        new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<MicroSpecialtyTeacher>()
                                .eq(MicroSpecialtyTeacher::getId, record.getId())
                                .eq(MicroSpecialtyTeacher::getInviteStatus, "INVITED")
                                .set(MicroSpecialtyTeacher::getInviteStatus, "DECLINED")
                                .set(MicroSpecialtyTeacher::getRespondedAt, LocalDateTime.now()));
                if (affected > 0) {
                    totalExpired++;
                }
            }
        }

        // 2. PENDING_ACADEMIC 超 14 天告警
        List<MicroSpecialtyTeacher> staleList = teacherRepository.selectList(
                new LambdaQueryWrapper<MicroSpecialtyTeacher>()
                        .eq(MicroSpecialtyTeacher::getInviteStatus, "PENDING_ACADEMIC")
                        .lt(MicroSpecialtyTeacher::getRespondedAt, LocalDateTime.now().minusDays(14)));
        totalStale = staleList.size();

        if (totalExpired > 0 || totalStale > 0) {
            log.info("[MS-InviteExpiry] 扫描完成: expired={}, stalePendingAcademic={}", totalExpired, totalStale);
        }
    }
}
