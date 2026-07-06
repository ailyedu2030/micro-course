package com.microcourse.service;

import com.microcourse.dto.storage.ProposalCourseItem;
import com.microcourse.dto.storage.ProposalTeamMemberItem;
import com.microcourse.dto.storage.ProposalSignatureItem;
import com.microcourse.dto.storage.StorageApplicationSaveRequest;
import com.microcourse.exception.BusinessException;
import com.microcourse.util.StorageValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 15: 申请表校验器单元测试（无需Spring上下文，纯逻辑测试）。
 */
class StorageValidatorTest {

    private StorageApplicationSaveRequest validRequest() {
        StorageApplicationSaveRequest req = new StorageApplicationSaveRequest();
        req.setTitle("测试大学");
        req.setMicroSpecialtyName("整理收纳");
        req.setLeadName("李教授");
        req.setContactPhone("13800138000");
        req.setApplyDate("2026.9.1");
        req.setType("急需紧缺型");
        req.setTargetAudience("本科");
        req.setTotalCredits(12);
        req.setCourseCount(4);
        req.setEnrollmentQuota(50);
        req.setClassSize(25);
        req.setIntroduction("<p>介绍</p>");
        req.setMarketDemandAnalysis("<p>市场需求分析内容</p>");
        req.setSpecialtyOverview("<p>专业概述内容</p>");
        req.setCurriculumDesign("<p>课程设计内容</p>");
        req.setConstructionGuarantee("<p>建设保障内容</p>");

        ProposalCourseItem course = new ProposalCourseItem();
        course.setCourseName("整理学");
        course.setHours(32);
        course.setCredits(new BigDecimal("2"));
        req.setCourses(List.of(course));

        ProposalTeamMemberItem member = new ProposalTeamMemberItem();
        member.setName("李教授");
        member.setAge(45);
        member.setTitle("教授");
        req.setTeamMembers(List.of(member));

        ProposalSignatureItem sig = new ProposalSignatureItem();
        sig.setSignLevel("LEAD");
        sig.setOpinionText("同意");
        req.setSignatures(List.of(sig));

        return req;
    }

    @Test
    @DisplayName("完整表单校验通过")
    void testValidFormPasses() {
        List<String> errors = StorageValidator.validateForSubmit(validRequest());
        assertTrue(errors.isEmpty(), "完整表单应无校验错误: " + errors);
    }

    @Test
    @DisplayName("缺少标题应报错")
    void testMissingTitle() {
        StorageApplicationSaveRequest req = validRequest();
        req.setTitle(null);
        List<String> errors = StorageValidator.validateForSubmit(req);
        assertTrue(errors.stream().anyMatch(e -> e.contains("申报高校")), "应提示缺少申报高校");
    }

    @Test
    @DisplayName("手机号格式错误应报错")
    void testInvalidPhone() {
        StorageApplicationSaveRequest req = validRequest();
        req.setContactPhone("12345");
        List<String> errors = StorageValidator.validateForSubmit(req);
        assertTrue(errors.stream().anyMatch(e -> e.contains("手机号")), "应提示手机号错误");
    }

    @Test
    @DisplayName("空课程表应报错")
    void testEmptyCourses() {
        StorageApplicationSaveRequest req = validRequest();
        req.setCourses(null);
        List<String> errors = StorageValidator.validateForSubmit(req);
        assertTrue(errors.stream().anyMatch(e -> e.contains("课程")), "应提示课程表为空");
    }

    @Test
    @DisplayName("applyDate格式错误应报错")
    void testInvalidDate() {
        StorageApplicationSaveRequest req = validRequest();
        req.setApplyDate("2026/09");
        List<String> errors = StorageValidator.validateForSubmit(req);
        assertTrue(errors.stream().anyMatch(e -> e.contains("申请时间")), "应提示日期格式错误");
    }

    @Test
    @DisplayName("validateAndThrow应有错误时抛BusinessException")
    void testValidateAndThrow() {
        StorageApplicationSaveRequest req = validRequest();
        req.setTitle("");
        assertThrows(BusinessException.class,
            () -> StorageValidator.validateAndThrow(req));
    }
}
