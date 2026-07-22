package com.microcourse.service.impl;

import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import com.microcourse.dto.storage.*;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import org.springframework.stereotype.Component;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;

@Component
public class StorageApplicationWordGenerator {

    public byte[] generate(StorageApplicationVO data) {
        try (XWPFDocument doc = new XWPFDocument()) {

            // H-02: DRAFT 状态添加"草稿"水印 — 在每个段落后添加红色"DRAFT 草稿"标记
            boolean isDraft = "DRAFT".equals(data.getStatus());

            // === 标题 ===
            createCenteredParagraph(doc, "高校开放共享\u201C微专业\u201D资源平台推荐表", true, 18, "宋体");
            // H-02: DRAFT 水印标记
            if (isDraft) {
                createCenteredDraftMark(doc, "DRAFT 草稿");
            }
            createEmptyParagraph(doc);

            // === 模块1：基础信息表（3行4列）===
            XWPFTable infoTable = doc.createTable(3, 4);
            setTableStyle(infoTable);
            setCell(infoTable, 0, 0, "申报高校", true);
            setCell(infoTable, 0, 1, data.getTitle() != null ? data.getTitle() : "");
            setCell(infoTable, 0, 2, "微专业名称", true);
            setCell(infoTable, 0, 3, data.getMicroSpecialtyName() != null ? data.getMicroSpecialtyName() : "");

            setCell(infoTable, 1, 0, "专业负责人", true);
            setCell(infoTable, 1, 1, data.getLeadName() != null ? data.getLeadName() : "");
            setCell(infoTable, 1, 2, "联系电话", true);
            setCell(infoTable, 1, 3, data.getContactPhone() != null ? data.getContactPhone() : "");

            setCell(infoTable, 2, 0, "申请时间", true);
            setCell(infoTable, 2, 1, data.getApplyDate() != null ? data.getApplyDate() : "");
            setCell(infoTable, 2, 2, "", false);
            setCell(infoTable, 2, 3, "", false);

            createEmptyParagraph(doc);

            // === 模块2：一、微专业基本情况 ===
            if (isDraft) {
                createDraftFooter(doc, "DRAFT 草稿");
            }
            createLeftParagraph(doc, "一、微专业基本情况", true, 14, "宋体");

            XWPFTable basicTable = doc.createTable(7, 4);
            setTableStyle(basicTable);
            setPairRow(basicTable, 0, "类型", data.getType(), "面向对象", data.getTargetAudience());
            setPairRow(basicTable, 1, "面向学科及专业", data.getTargetDisciplines(), "总学分", strVal(data.getTotalCredits()));
            setPairRow(basicTable, 2, "课程门数", strVal(data.getCourseCount()), "共建高校", data.getCoBuildUniversities());
            setPairRow(basicTable, 3, "拟共享高校", data.getPlannedShareUniversities(), "招生名额", strVal(data.getEnrollmentQuota()));
            setPairRow(basicTable, 4, "成班人数", strVal(data.getClassSize()), "开课时间", data.getStartDate());
            setPairRow(basicTable, 5, "学制", data.getDuration(), "是否产教融合", boolToCn(data.getIsIndustryAcademic()));
            setPairRow(basicTable, 6, "产教合作单位", data.getIndustryPartners(), "", "");

            createEmptyParagraph(doc);

            // 富文本段落
            addRichTextParagraph(doc, "微专业介绍：", data.getIntroduction());
            addRichTextParagraph(doc, "社会需求及就业前景分析：", data.getMarketDemandAnalysis());
            addRichTextParagraph(doc, "微专业简介：", data.getSpecialtyOverview());
            addRichTextParagraph(doc, "课程体系设置情况：", data.getCurriculumDesign());
            addRichTextParagraph(doc, "建设条件保障：", data.getConstructionGuarantee());

            // 课程体系动态表
            if (data.getCourses() != null && !data.getCourses().isEmpty()) {
                createLeftParagraph(doc, "课程体系：", true, 12, "宋体");
                int totalHours = data.getCourses().stream()
                    .mapToInt(c -> c.getHours() != null ? c.getHours() : 0).sum();
                int rowCount = data.getCourses().size() + 2; // header + 合计行
                XWPFTable courseTable = doc.createTable(rowCount, 5);
                setTableStyle(courseTable);
                setHeaderRow(courseTable, 0, new String[]{"模块", "课程名称", "学时", "学分", "开课学期"});

                int idx = 1;
                for (ProposalCourseItem c : data.getCourses()) {
                    setCell(courseTable, idx, 0, c.getModuleName());
                    setCell(courseTable, idx, 1, c.getCourseName());
                    setCell(courseTable, idx, 2, strVal(c.getHours()));
                    setCell(courseTable, idx, 3, decVal(c.getCredits()));
                    setCell(courseTable, idx, 4, c.getSemester());
                    idx++;
                }
                // 合计行
                setCellBold(courseTable, idx, 0, "总学时");
                setCellBold(courseTable, idx, 1, "");
                setCellBold(courseTable, idx, 2, String.valueOf(totalHours));
                setCellBold(courseTable, idx, 3, "");
                setCellBold(courseTable, idx, 4, "");
            }

            createEmptyParagraph(doc);

            // === 模块3：教学团队 ===
            if (isDraft) {
                createDraftFooter(doc, "DRAFT 草稿");
            }
            createLeftParagraph(doc, "二、微专业教学团队情况", true, 14, "宋体");
            createLeftParagraph(doc, "专业负责人信息：", true, 12, "宋体");

            XWPFTable leadTable = doc.createTable(2, 4);
            setTableStyle(leadTable);
            setPairRow(leadTable, 0, "姓名", data.getLeadName(), "职称", data.getLeadTitle());
            setPairRow(leadTable, 1, "职务", data.getLeadPosition(), "联系电话", data.getLeadPhone());

            addSimpleTextParagraph(doc, "主要研究方向：" + nvl(data.getLeadResearchDirection()));
            addSimpleTextParagraph(doc, "承担主要任务与主讲课程：" + nvl(data.getLeadMainTasks()));

            // 教学团队成员动态表
            if (data.getTeamMembers() != null && !data.getTeamMembers().isEmpty()) {
                createLeftParagraph(doc, "教学团队成员：", true, 12, "宋体");
                XWPFTable teamTable = doc.createTable(data.getTeamMembers().size() + 1, 6);
                setTableStyle(teamTable);
                setHeaderRow(teamTable, 0, new String[]{"姓名", "年龄", "职称", "所在单位", "曾授课程", "拟授课程"});

                for (int i = 0; i < data.getTeamMembers().size(); i++) {
                    ProposalTeamMemberItem m = data.getTeamMembers().get(i);
                    int r = i + 1;
                    setCell(teamTable, r, 0, m.getName());
                    setCell(teamTable, r, 1, strVal(m.getAge()));
                    setCell(teamTable, r, 2, m.getTitle());
                    setCell(teamTable, r, 3, m.getOrganization());
                    setCell(teamTable, r, 4, m.getTaughtCourses());
                    setCell(teamTable, r, 5, m.getPlannedCourses());
                }
            }

            // === 模块4：三级签字 ===
            createLeftParagraph(doc, "三、牵头单位意见", true, 14, "宋体");
            if (data.getSignatures() != null) {
                for (ProposalSignatureItem sig : data.getSignatures()) {
                    if ("SHARED_UNIT".equals(sig.getSignLevel())) {
                        continue; // handled in module 5
                    }
                    createLeftParagraph(doc, getLevelLabel(sig.getSignLevel()) + "：", true, 12, "宋体");
                    addSimpleTextParagraph(doc, "意见：" + nvl(sig.getOpinionText()));

                    if ("TEXT".equals(sig.getSignatureType()) && sig.getSignatureText() != null) {
                        addSimpleTextParagraph(doc, "负责人签字：" + sig.getSignatureText());
                    } else {
                        addSimpleTextParagraph(doc, "负责人签字：____________");
                    }

                    addSimpleTextParagraph(doc, "日期：" + nvl(sig.getSignDate()));
                    addSimpleTextParagraph(doc, "公章：____________");
                    addSimpleTextParagraph(doc, "");
                }
            }

            // === 模块5：共建共享单位 ===
            if (data.getSharedUnits() != null && !data.getSharedUnits().isEmpty()) {
                createLeftParagraph(doc, "四、共建共享单位意见", true, 14, "宋体");

                for (ProposalSharedUnitItem unit : data.getSharedUnits()) {
                    createLeftParagraph(doc,
                        "单位：" + nvl(unit.getUnitName()) + "（" + getUnitTypeLabel(unit.getUnitType()) + "）",
                        true, 11, "宋体");

                    if (data.getSignatures() != null) {
                        for (ProposalSignatureItem sig : data.getSignatures()) {
                            if ("SHARED_UNIT".equals(sig.getSignLevel()) &&
                                unit.getSortOrder() != null && sig.getUnitSeq() != null &&
                                unit.getSortOrder().equals(sig.getUnitSeq())) {
                                addSimpleTextParagraph(doc, "意见：" + nvl(sig.getOpinionText()));
                                addSimpleTextParagraph(doc, "备注：" + nvl(sig.getRemark()));
                            }
                        }
                    }
                    // 默认签字栏
                    addSimpleTextParagraph(doc, "负责人签字：____________");
                    addSimpleTextParagraph(doc, "公章：____________");
                    addSimpleTextParagraph(doc, "日期：____________");
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            return out.toByteArray();

        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SA_WORD_GENERATE_FAILED, "Word 生成失败", e);
        }
    }

    // ====== 辅助方法 ======

    // H-02: 创建居中的红色"DRAFT 草稿"水印标记
    private void createCenteredDraftMark(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun r = p.createRun();
        r.setBold(true);
        r.setFontSize(16);
        r.setFontFamily("宋体");
        r.setColor("FF0000");
        r.setText(text);
    }

    // H-02: 创建页脚水印（红色小字）
    private void createDraftFooter(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.RIGHT);
        XWPFRun r = p.createRun();
        r.setBold(true);
        r.setFontSize(8);
        r.setFontFamily("宋体");
        r.setColor("FF0000");
        r.setText(text);
    }

    private void createCenteredParagraph(XWPFDocument doc, String text, boolean bold, int fontSize, String fontFamily) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun r = p.createRun();
        r.setBold(bold);
        r.setFontSize(fontSize);
        r.setFontFamily(fontFamily);
        r.setText(text);
    }

    private void createLeftParagraph(XWPFDocument doc, String text, boolean bold, int fontSize, String fontFamily) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.LEFT);
        XWPFRun r = p.createRun();
        r.setBold(bold);
        r.setFontSize(fontSize);
        r.setFontFamily(fontFamily);
        r.setText(text);
    }

    private void createEmptyParagraph(XWPFDocument doc) {
        doc.createParagraph().createRun().setText("");
    }

    private void addSimpleTextParagraph(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.LEFT);
        XWPFRun r = p.createRun();
        r.setFontSize(10);
        r.setFontFamily("宋体");
        r.setText(text != null ? text : "");
    }

    private void addRichTextParagraph(XWPFDocument doc, String label, String content) {
        if (content == null || content.isEmpty()) return;
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.LEFT);

        XWPFRun labelRun = p.createRun();
        labelRun.setBold(true);
        labelRun.setFontSize(11);
        labelRun.setFontFamily("宋体");
        labelRun.setText(label);

        XWPFRun contentRun = p.createRun();
        contentRun.setFontSize(11);
        contentRun.setFontFamily("宋体");
        contentRun.setText(content != null ? content : "");
    }

    private void setTableStyle(XWPFTable table) {
        table.setWidth("100%");
        XWPFTable.XWPFBorderType borderType = XWPFTable.XWPFBorderType.SINGLE;
        table.setInsideHBorder(borderType, 4, 0, "000000");
        table.setInsideVBorder(borderType, 4, 0, "000000");
        table.setTopBorder(borderType, 4, 0, "000000");
        table.setBottomBorder(borderType, 4, 0, "000000");
        table.setLeftBorder(borderType, 4, 0, "000000");
        table.setRightBorder(borderType, 4, 0, "000000");
    }

    private void setCell(XWPFTable table, int row, int col, String text) {
        XWPFTableCell cell = table.getRow(row).getCell(col);
        cell.setText(text != null ? text : "");
        CTTcPr tcPr = cell.getCTTc().addNewTcPr();
        tcPr.addNewTcW().setW(BigInteger.valueOf(2000));
    }

    private void setCell(XWPFTable table, int row, int col, String text, boolean bold) {
        XWPFTableCell cell = table.getRow(row).getCell(col);
        cell.setText(text != null ? text : "");
        CTTcPr tcPr = cell.getCTTc().addNewTcPr();
        tcPr.addNewTcW().setW(BigInteger.valueOf(2000));
        if (bold) {
            XWPFParagraph p = cell.getParagraphs().get(0);
            p.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun r = p.getRuns().get(0);
            if (r != null) {
                r.setBold(true);
                r.setFontFamily("宋体");
                r.setFontSize(10);
            }
        }
    }

    private void setCellBold(XWPFTable table, int row, int col, String text) {
        setCell(table, row, col, text, true);
    }

    private void setHeaderRow(XWPFTable table, int row, String[] texts) {
        for (int i = 0; i < texts.length; i++) {
            setCell(table, row, i, texts[i], true);
        }
    }

    private void setPairRow(XWPFTable table, int row, String l1, String v1, String l2, String v2) {
        setCell(table, row, 0, l1, true);
        setCell(table, row, 1, v1 != null ? v1 : "");
        setCell(table, row, 2, l2, true);
        setCell(table, row, 3, v2 != null ? v2 : "");
    }

    private String nvl(String s) { return s != null ? s : ""; }
    private String strVal(Integer i) { return i != null ? i.toString() : ""; }
    private String decVal(java.math.BigDecimal d) { return d != null ? d.toString() : ""; }
    private String boolToCn(Boolean b) { return b != null && b ? "是" : "否"; }

    private String getLevelLabel(String level) {
        if (level == null) return "";
        switch (level) {
            case "LEAD": return "微专业负责人意见";
            case "DEPT": return "学院意见";
            case "SCHOOL": return "学校意见";
            default: return level;
        }
    }

    private String getUnitTypeLabel(String type) {
        if (type == null) return "";
        switch (type) {
            case "CO_BUILD_UNIV": return "共建高校";
            case "ENTERPRISE": return "合作企业";
            case "SHARE_UNIV": return "拟共享高校";  // P1-C-5 修复：与 UnitType 枚举一致
            default: return type;
        }
    }
}
