package com.microcourse.scheduled;
import com.microcourse.service.MicroSpecialtyEnrollmentService;
import org.slf4j.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component
public class MicroSpecialtyProgressAggregator {
    private static final Logger log = LoggerFactory.getLogger(MicroSpecialtyProgressAggregator.class);
    private final MicroSpecialtyEnrollmentService enrollmentService;
    public MicroSpecialtyProgressAggregator(MicroSpecialtyEnrollmentService enrollmentService) { this.enrollmentService = enrollmentService; }
    @Scheduled(cron = "0 0 2 * * ?")
    public void aggregateAll() {
        log.info("[MS Aggregator] start");
        try { enrollmentService.aggregateProgress(); log.info("[MS Aggregator] done"); }
        catch (Exception e) { log.error("[MS Aggregator] error", e); }
    }
}
