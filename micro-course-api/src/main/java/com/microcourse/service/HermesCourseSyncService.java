package com.microcourse.service;

import com.microcourse.dto.hermes.HermesWebhookRequest;

public interface HermesCourseSyncService {

    HermesSyncResult upsertCourse(HermesWebhookRequest request);

    public static class HermesSyncResult {
        private Long courseId;
        private String status;
        private String action;

        public HermesSyncResult(Long courseId, String status, String action) {
            this.courseId = courseId;
            this.status = status;
            this.action = action;
        }

        public Long getCourseId() { return courseId; }
        public String getStatus() { return status; }
        public String getAction() { return action; }
    }
}
