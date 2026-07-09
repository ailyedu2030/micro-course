package com.microcourse.util;

import com.microcourse.dto.storage.ProposalCourseItem;
import com.microcourse.dto.storage.ProposalSignatureItem;
import com.microcourse.dto.storage.ProposalTeamMemberItem;
import com.microcourse.dto.storage.StorageApplicationSaveRequest;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import java.util.ArrayList;
import java.util.List;

/**
 * 申请表校验器 — 提交时用
 */
public final class StorageValidator {

    private StorageValidator() {}

    /**
     * 提交前全量校验，返回错误列表。空列表表示校验通过。
     */
    public static List<String> validateForSubmit(StorageApplicationSaveRequest req) {
        List<String> errors = new ArrayList<>();

        // 1. 检查核心必填项
        if (req.getTitle() == null || req.getTitle().isBlank()) {
            errors.add("申报高校不能为空");
        }
        if (req.getLeadName() == null || req.getLeadName().isBlank()) {
            errors.add("专业负责人不能为空");
        }

        // 2. 检查手机号格式
        if (req.getContactPhone() != null && !req.getContactPhone().isBlank()) {
            if (!req.getContactPhone().matches("^1[3-9]\\d{9}$")) {
                errors.add("请输入正确的手机号");
            }
        }

        // 3. 检查日期格式（可为空）
        // applyDate 格式 yyyy.M 或 yyyy.M.D，与 parseDate 格式支持保持一致
        if (req.getApplyDate() != null && !req.getApplyDate().isBlank()) {
            if (!req.getApplyDate().matches("^\\d{4}\\.\\d{1,2}(\\.\\d{1,2})?$")) {
                errors.add("申请时间格式错误，正确格式：yyyy.M（如 2026.7）或 yyyy.M.D（如 2026.7.1）");
            }
        }

        // 4. 检查数字字段 > 0
        if (req.getTotalCredits() != null && req.getTotalCredits() <= 0) {
            errors.add("总学分必须大于 0");
        }
        if (req.getCourseCount() != null && req.getCourseCount() <= 0) {
            errors.add("课程门数必须大于 0");
        }
        if (req.getEnrollmentQuota() != null && req.getEnrollmentQuota() <= 0) {
            errors.add("招生名额必须大于 0");
        }
        if (req.getClassSize() != null && req.getClassSize() <= 0) {
            errors.add("成班人数必须大于 0");
        }

        // 5. 检查富文本字数（建议上限 500 字，预警 1000 字）
        checkRichTextWordCount(errors, req.getIntroduction(), "简介", 500);
        checkRichTextWordCount(errors, req.getMarketDemandAnalysis(), "市场需求分析", 1000);
        checkRichTextWordCount(errors, req.getSpecialtyOverview(), "专业概述", 1000);
        checkRichTextWordCount(errors, req.getCurriculumDesign(), "课程设计", 2000);
        checkRichTextWordCount(errors, req.getConstructionGuarantee(), "建设保障", 1000);

        // 6. 检查课程表至少 1 行
        List<ProposalCourseItem> courses = req.getCourses();
        if (courses == null || courses.isEmpty()) {
            errors.add("课程表至少需要 1 门课程");
        } else {
            for (int i = 0; i < courses.size(); i++) {
                ProposalCourseItem c = courses.get(i);
                if (c.getCourseName() == null || c.getCourseName().isBlank()) {
                    errors.add("课程表第" + (i + 1) + "行：课程名称不能为空");
                }
            }
        }

        // 7. 检查团队至少 1 行（负责人）
        List<ProposalTeamMemberItem> teamMembers = req.getTeamMembers();
        if (teamMembers == null || teamMembers.isEmpty()) {
            errors.add("教学团队至少需要 1 名成员");
        }

        // 8. 三级签字至少 1 个
        List<ProposalSignatureItem> signatures = req.getSignatures();
        if (signatures == null || signatures.isEmpty()) {
            errors.add("至少需要 1 个签字记录");
        }

        return errors;
    }

    /**
     * DRAFT 状态下导出用 — 仅校验格式（手机号/日期），不检查内容完整性。
     */
    public static List<String> validateFormatOnly(StorageApplicationSaveRequest req) {
        List<String> errors = new ArrayList<>();
        // 仅检查手机号格式
        if (req.getContactPhone() != null && !req.getContactPhone().isBlank()) {
            if (!req.getContactPhone().matches("^1[3-9]\\d{9}$")) {
                errors.add("请输入正确的手机号");
            }
        }
        // 仅检查日期格式
        if (req.getApplyDate() != null && !req.getApplyDate().isBlank()) {
            if (!req.getApplyDate().matches("^\\d{4}\\.\\d{1,2}\\.\\d{1,2}$")
                && !req.getApplyDate().matches("^\\d{4}-\\d{2}-\\d{2}$")) {
                errors.add("申请时间格式错误，正确格式：yyyy.M.d（如 2026.7.1）或 yyyy-MM-dd");
            }
        }
        return errors;
    }

    /**
     * 提交前校验并在有错误时直接抛出异常（确保异常路径不会被遗漏）。
     * <p>与 {@link #validateForSubmit} 使用相同的校验逻辑。</p>
     */
    public static void validateAndThrow(StorageApplicationSaveRequest req) {
        List<String> errors = validateForSubmit(req);
        if (!errors.isEmpty()) {
            // I-01: 使用 \\n 替代字面换行符，避免 JSON 序列化后出现未转义的 control character (RFC 8259 §7)
            throw new BusinessException(ErrorCode.SA_FORM_INCOMPLETE,
                    "请补全以下必填项： " + String.join("; ", errors));
        }
    }

    private static void checkRichTextWordCount(List<String> errors, String html,
                                                String fieldName, int warning) {
        if (html == null || html.isBlank()) {
            // I-08: 富文本为空时也需要校验 — 空 HTML(如 <p></p>) 经 stripHtmlAndCount 后字数为 0
            if (warning > 0) {
                errors.add(fieldName + "不能为空");
            }
            return;
        }
        int count = WordCountUtil.stripHtmlAndCount(html);
        // I-08: 添加最小字数校验 — 空标签（如 <p></p>）字数为 0 应被拦截
        if (count < 1 && warning > 0) {
            errors.add(fieldName + "不能为空");
            return;
        }
        if (count > warning * 2) {
            errors.add(fieldName + "字数严重超限（当前 " + count + " 字，建议 " + warning + " 字以内）");
        } else if (count > warning) {
            errors.add(fieldName + "字数超过建议上限（当前 " + count + " 字，建议 " + warning + " 字以内）");
        }
    }
}
