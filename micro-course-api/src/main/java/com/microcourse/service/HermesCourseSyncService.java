package com.microcourse.service;

import com.microcourse.dto.hermes.HermesWebhookRequest;

public interface HermesCourseSyncService {

    /**
     * 同步/创建课程。
     *
     * @param request       Webhook 请求体
     * @param callerTeacherId 调用方教师 ID（从 API Key 反查得到，必填；body 中的 teacherId 若有必须一致）
     */
    HermesSyncResult upsertCourse(HermesWebhookRequest request, Long callerTeacherId);

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
