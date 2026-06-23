package com.microcourse.scheduled;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.entity.*;
import com.microcourse.enums.*;
import com.microcourse.repository.*;
import com.microcourse.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

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
    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public MicroSpecialtyInviteExpiryJob(MicroSpecialtyTeacherRepository teacherRepository,
                                          MicroSpecialtyRepository msRepository,
                                          NotificationService notificationService,
                                          UserRepository userRepository) {
        this.teacherRepository = teacherRepository;
        this.msRepository = msRepository;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
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

        // P0-4 修复：预加载 ACADEMIC 用户列表（用于通知）
        List<User> academicUsers = userRepository.selectList(
                new LambdaQueryWrapper<User>().eq(User::getRole, UserRole.ACADEMIC));
        List<Long> academicUserIds = academicUsers.stream()
                .map(User::getId).collect(Collectors.toList());

        // 1. 扫 INVITED 过期 → DECLINED + 通知
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
                    MicroSpecialty ms = msRepository.selectById(record.getMicroSpecialtyId());

                    // P0-4: 通知被邀请人
                    notificationService.notifyAsync(record.getTeacherId(),
                            NotificationType.MS_INVITE_EXPIRED,
                            "邀请已过期",
                            (ms != null ? "微专业《" + ms.getTitle() + "》" : "微专业") + "邀请已过期",
                            ms != null ? ms.getId() : null);

                    // P0-4: 通知 LEAD
                    if (ms != null && ms.getLeadTeacherId() != null) {
                        notificationService.notifyAsync(ms.getLeadTeacherId(),
                                NotificationType.MS_INVITE_EXPIRED,
                                "邀请已过期",
                                "微专业《" + ms.getTitle() + "》教师邀请已过期",
                                ms.getId());
                    }

                    // P0-4: 如果过期的是 LEAD → 额外通知 ACADEMIC
                    if ("LEAD".equals(record.getRole())) {
                        for (Long auId : academicUserIds) {
                            notificationService.notifyAsync(auId,
                                    NotificationType.MS_INVITE_EXPIRED,
                                    "微专业负责人邀请过期",
                                    (ms != null ? "微专业《" + ms.getTitle() + "》" : "微专业")
                                            + "负责人邀请已过期，请关注",
                                    ms != null ? ms.getId() : null);
                        }
                    }
                }
            }
        }

        // 2. PENDING_ACADEMIC 超 14 天告警（§9.3 step 3）
        List<MicroSpecialtyTeacher> staleList = teacherRepository.selectList(
                new LambdaQueryWrapper<MicroSpecialtyTeacher>()
                        .eq(MicroSpecialtyTeacher::getInviteStatus, "PENDING_ACADEMIC")
                        .lt(MicroSpecialtyTeacher::getRespondedAt, LocalDateTime.now().minusDays(14)));
        totalStale = staleList.size();

        if (totalStale > 0) {
            for (MicroSpecialtyTeacher stale : staleList) {
                MicroSpecialty ms = msRepository.selectById(stale.getMicroSpecialtyId());
                for (Long auId : academicUserIds) {
                    notificationService.notifyAsync(auId,
                            NotificationType.MS_INVITE_EXPIRED,
                            "跨学院审批超时未处理",
                            (ms != null ? "微专业《" + ms.getTitle() + "》" : "微专业")
                                    + "跨学院邀请审批超过 14 天未处理，请及时审批",
                            ms != null ? ms.getId() : null);
                }
            }
        }

        if (totalExpired > 0 || totalStale > 0) {
            log.info("[MS-InviteExpiry] 扫描完成: expired={}, stalePendingAcademic={}", totalExpired, totalStale);
        }
    }
}
