package com.microcourse;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P0 finding CON-001 回归测试:Enrollment 幂等性 — 同 userId+courseId 二次选课
 * 必须返回同一 record,无 DuplicateKeyException 泄露。
 */
@DisplayName("CON-001 Enrollment 幂等回归")
class EnrollmentP0ConcurrencyTest extends BaseIntegrationTest {

    @Test
    @DisplayName("先后两次 enroll 同一 (userId, courseId) 都返回 200 且 id 相同")
    void sequentialEnrollIdempotent() throws Exception {
        String token = "Bearer " + loginAs("student", "student123");
        String body = "{\"userId\":7,\"courseId\":3,\"sourceChannel\":\"WEB\"}";

        MvcResult res1 = mockMvc.perform(post("/api/enrollments")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andReturn();
        assertEquals(200, res1.getResponse().getStatus(), "第一次选课必须成功");
        Number id1Num = JsonPath.read(res1.getResponse().getContentAsString(), "$.data.id");

        MvcResult res2 = mockMvc.perform(post("/api/enrollments")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andReturn();
        assertEquals(200, res2.getResponse().getStatus(), "第二次选课也须成功(幂等)");
        Number id2Num = JsonPath.read(res2.getResponse().getContentAsString(), "$.data.id");

        assertEquals(id1Num.longValue(), id2Num.longValue(),
            "两次响应必须返回相同 enrollment.id(幂等性证据)");
    }

    @Test
    @DisplayName("BOUNDARY: 3 次 enroll 同一课程,全部 200 且 id 相同")
    void tripleEnrollIdempotent() throws Exception {
        String token = "Bearer " + loginAs("student", "student123");
        String body = "{\"userId\":7,\"courseId\":4,\"sourceChannel\":\"WEB\"}";

        Long firstId = null;
        for (int i = 0; i < 3; i++) {
            MvcResult res = mockMvc.perform(post("/api/enrollments")
                    .header("Authorization", token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andReturn();
            assertEquals(200, res.getResponse().getStatus(),
                "第 " + (i+1) + " 次选课必须返回 200");
            Number idNum = JsonPath.read(res.getResponse().getContentAsString(), "$.data.id");
            if (firstId == null) {
                firstId = idNum.longValue();
            } else {
                assertEquals(firstId.longValue(), idNum.longValue(),
                    "所有响应必须返回相同 enrollment.id");
            }
        }
    }
}
