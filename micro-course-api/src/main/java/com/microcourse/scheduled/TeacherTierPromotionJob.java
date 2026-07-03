package com.microcourse.scheduled;

import com.microcourse.service.TeacherRatingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 教师等级升降定时任务。
 * 每日 03:00 触发，重新计算所有教师的综合评分并更新等级。
 * 升级/降级记录写入 teacher_tier_log。
 */
@Component
public class TeacherTierPromotionJob {

    private static final Logger log = LoggerFactory.getLogger(TeacherTierPromotionJob.class);

    private volatile boolean running = false;

    private final TeacherRatingService ratingService;

    public TeacherTierPromotionJob(TeacherRatingService ratingService) {
        this.ratingService = ratingService;
    }

    /**
     * 每日凌晨 3:00 执行全部教师评级计算。
     * 防止重叠执行。
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void promoteTiers() {
        if (running) {
            log.warn("[TeacherTier] 上一轮评级仍在执行，跳过本轮");
            return;
        }
        running = true;
        try {
            log.info("[TeacherTier] 开始教师等级升降计算");
            int total = ratingService.recalculateAll();
            log.info("[TeacherTier] 教师等级升降完成: processed={}", total);
        } catch (Exception e) {
            log.error("[TeacherTier] 教师等级升降执行异常", e);
        } finally {
            running = false;
        }
    }
}
