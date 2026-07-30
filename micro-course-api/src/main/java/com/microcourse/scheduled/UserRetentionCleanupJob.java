package com.microcourse.scheduled;

import com.microcourse.entity.User;
import com.microcourse.repository.EnrollmentRepository;
import com.microcourse.repository.OrderRepository;
import com.microcourse.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户留存数据清理定时任务。
 * 每日 02:00 执行，物理删除软删除超过 90 天的用户记录及其全部关联数据。
 *
 * <h3>级联清理顺序（关键 — 不可乱）</h3>
 * <p>数据库外键状态（核对自 P0-ORPHAN-001 修复）：</p>
 * <ul>
 *   <li>orders.user_id → users.id : <b>RESTRICT</b>（必须先删 orders 才能删 users）</li>
 *   <li>payments.order_id → orders.id : CASCADE（删 orders 时自动删 payments）</li>
 *   <li>enrollments.user_id → users.id : CASCADE（自动清）</li>
 *   <li>learning_progress.user_id → users.id : CASCADE（自动清）</li>
 *   <li>wrong_questions.user_id → users.id : CASCADE（自动清）</li>
 *   <li>certificates.user_id → users.id : CASCADE（自动清）</li>
 *   <li>cart_items.user_id → users.id : CASCADE（自动清）</li>
 * </ul>
 * <p>因此本 Job 只需显式删 <b>orders</b>（含 payments CASCADE），其它 CASCADE 表由 DB 自动清理。
 * enrollments 显式调用 {@code EnrollmentRepository.physicalDeleteByUserId} 仅用于审计日志记录
 * 删除数量（DB 也会 CASCADE 删）。</p>
 *
 * <h3>重试与防重叠</h3>
 * <ul>
 *   <li>{@code running} volatile 防止同一进程内重叠执行</li>
 *   <li>单用户清理失败仅记日志、不中断整体（外层 try-catch）</li>
 *   <li>{@code @Transactional} 仅包裹单用户清理（避免长事务）</li>
 * </ul>
 *
 * <p>引用来源：{@link com.microcourse.enums.UserStatus} §INACTIVE 文档注释：
 * "物理清理由 UserRetentionCleanupJob @Scheduled 处理"</p>
 */
@Component
public class UserRetentionCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(UserRetentionCleanupJob.class);

    private volatile boolean running = false;

    private final UserRepository userRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final OrderRepository orderRepository;

    public UserRetentionCleanupJob(UserRepository userRepository,
                                   EnrollmentRepository enrollmentRepository,
                                   OrderRepository orderRepository) {
        this.userRepository = userRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.orderRepository = orderRepository;
    }

    /**
     * 每日 02:00 执行物理清理。
     * 防止重叠执行。
     */
    @Scheduled(cron = "0 0 2 * * ?")
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
                if (cleanupOneUser(user)) {
                    totalDeleted++;
                }
            }
            log.info("[UserRetentionCleanup] 清理完成: 共清理 {} 人", totalDeleted);
        } catch (Exception e) {
            log.error("[UserRetentionCleanup] 清理过程异常", e);
        } finally {
            running = false;
        }
    }

    /**
     * 清理单用户：先删 orders（含 payments CASCADE），再删 enrollments（显式 CASCADE），
     * 最后删 user。learning_progress/wrong_questions/certificates/cart_items 由 DB CASCADE 自动清理。
     * 单用户独立事务，单用户失败不影响其他人。
     *
     * @return true=成功清理；false=清理失败（不计入 totalDeleted）
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean cleanupOneUser(User user) {
        Long userId = user.getId();
        try {
            // 步骤 1: 显式清 orders（RESTRICT，必须先删；payments CASCADE 跟随）
            int orderCount = orderRepository.physicalDeleteByUserId(userId);
            // 步骤 2: 显式清 enrollments（仅用于审计，DB 也会 CASCADE 删）
            int enrollmentCount = enrollmentRepository.physicalDeleteByUserId(userId);
            // 步骤 3: 物理删 user（CASCADE 自动清 learning_progress/wrong_questions/certificates/cart_items）
            int userAffected = userRepository.physicalDeleteById(userId);
            if (userAffected == 0) {
                log.warn("[UserRetentionCleanup] 物理删 user 失败 userId={} username={}",
                        userId, user.getUsername());
                return false;
            }
            log.info("[UserRetentionCleanup] 已清理 userId={} username={} deletedAt={} orders={} enrollments={}",
                    userId, user.getUsername(), user.getDeletedAt(), orderCount, enrollmentCount);
            return true;
        } catch (Exception e) {
            log.error("[UserRetentionCleanup] 清理用户失败 userId={} username={}",
                    userId, user.getUsername(), e);
            return false;
        }
    }
}
