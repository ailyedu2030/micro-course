package com.microcourse.scheduled;
import com.microcourse.service.MicroSpecialtyInviteService;
import org.slf4j.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component
public class MicroSpecialtyInviteExpiryJob {
    private static final Logger log = LoggerFactory.getLogger(MicroSpecialtyInviteExpiryJob.class);
    private final MicroSpecialtyInviteService inviteService;
    public MicroSpecialtyInviteExpiryJob(MicroSpecialtyInviteService inviteService) { this.inviteService = inviteService; }
    @Scheduled(cron = "0 0 * * * ?")
    public void scanExpired() {
        log.debug("[MS Expiry] scanning");
        try { inviteService.scanExpired(); } catch (Exception e) { log.error("[MS Expiry] error", e); }
    }
}
