package com.microcourse.scheduled;

import com.microcourse.service.MicroSpecialtyInviteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 微专业邀请过期扫描定时任务。
 * 每小时扫描 INVITED 过期记录 → DECLINED + 通知；
 * 同时告警 PENDING_ACADEMIC 超 14 天未处理的记录。
 * <p>实际扫描逻辑委托给 {@link MicroSpecialtyInviteService#scanExpired()} 执行。
 */
@Component
public class MicroSpecialtyInviteExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(MicroSpecialtyInviteExpiryJob.class);

    private final MicroSpecialtyInviteService inviteService;

    public MicroSpecialtyInviteExpiryJob(MicroSpecialtyInviteService inviteService) {
        this.inviteService = inviteService;
    }

    /**
     * 每小时整点扫描过期邀请。
     * 委托 Service.scanExpired() 执行实际逻辑（含分批处理 + 乐观锁）。
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void scanExpired() {
        try {
            log.debug("[MS-InviteExpiry] 开始扫描过期邀请");
            int totalExpired = inviteService.scanExpired();
            log.info("[MS-InviteExpiry] 扫描完成: expired={}", totalExpired);
        } catch (Exception e) {
            log.error("[MS-InviteExpiry] unexpected error during scan: {}", e.getMessage(), e);
        }
    }
}
