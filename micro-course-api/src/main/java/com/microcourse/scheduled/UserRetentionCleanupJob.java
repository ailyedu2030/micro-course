package com.microcourse.scheduled;

import com.microcourse.entity.User;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户留存数据清理定时任务。
 * 每日 02:00 执行，物理删除软删除超过 90 天的用户记录及其关联数据。
 * <p>关联清理：enrollments（选课记录）等数据。</p>
 * <p>引用来源：{@link com.microcourse.enums.UserStatus} §INACTIVE 文档注释：
 * "物理清理由 UserRetentionCleanupJob @Scheduled 处理"</p>
 */
@Component
public class UserRetentionCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(UserRetentionCleanupJob.class);

    private volatile boolean running = false;

    private final UserRepository userRepository;
    private final EnrollmentRepository enrollmentRepository;

    public UserRetentionCleanupJob(UserRepository userRepository,
                                   EnrollmentRepository enrollmentRepository) {
        this.userRepository = userRepository;
        this.enrollmentRepository = enrollmentRepository;
    }

    /**
     * 每日 02:00 执行物理清理。
     * 防止重叠执行。
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional(rollbackFor = Exception.class)
    public void cleanupExpiredUsers() {
        if (running) {
            log.warn("[UserRetentionCleanup] 上一轮清理仍在执行，跳过本轮");
            return;
        }
        running = true;
        try {
            log.info("[UserRetentionCleanup] 开始扫描软删除超过 90 天的用户");
            List<User> expiredUsers = userRepository.selectSoftDeletedOlderThan90Days();
            if (expiredUsers.isEmpty()) {
                log.info("[UserRetentionCleanup] 无需要清理的用户");
                return;
            }
            int totalDeleted = 0;
            for (User user : expiredUsers) {
                try {
                    // 级联清理关联的 enrollments
                    int enrollmentCount = enrollmentRepository.physicalDeleteByUserId(user.getId());
                    // 物理删除用户记录
                    userRepository.physicalDeleteById(user.getId());
                    totalDeleted++;
                    log.info("[UserRetentionCleanup] 已清理 userId={} username={} deletedAt={} enrollmentRecords={}",
                            user.getId(), user.getUsername(), user.getDeletedAt(), enrollmentCount);
                } catch (Exception e) {
                    log.error("[UserRetentionCleanup] 清理用户失败 userId={} username={}",
                            user.getId(), user.getUsername(), e);
                }
            }
            log.info("[UserRetentionCleanup] 清理完成: 共清理 {} 人", totalDeleted);
        } catch (Exception e) {
            log.error("[UserRetentionCleanup] 清理过程异常", e);
        } finally {
            running = false;
        }
    }
}
