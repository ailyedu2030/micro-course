package com.microcourse.service.impl;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.microcourse.dto.storage.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.List;

@Component
public class StorageApplicationPdfGenerator {

    private static final Logger log = LoggerFactory.getLogger(StorageApplicationPdfGenerator.class);

    private Font titleFont;
    private Font headerFont;
    private Font bodyFont;
    private Font smallFont;

    public StorageApplicationPdfGenerator() {
        initFonts();
    }

    private void initFonts() {
        try {
            String[] fontPaths = {
                "fonts/Songti.ttc",
                "/System/Library/Fonts/Supplemental/Songti.ttc",
                "/System/Library/Fonts/PingFang.ttc"
            };

            BaseFont bf = null;
            for (String path : fontPaths) {
                try {
                    if (path.startsWith("fonts/")) {
                        InputStream is = getClass().getClassLoader().getResourceAsStream(path);
                        if (is != null) {
                            bf = BaseFont.createFont(path, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                            break;
                        }
                    } else {
                        bf = BaseFont.createFont(path, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                        break;
                    }
                } catch (Exception ignored) {
                    // try next path
                }
            }

            if (bf == null) {
                bf = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
            }

            titleFont = new Font(bf, 18, Font.BOLD);
            headerFont = new Font(bf, 14, Font.BOLD);
            bodyFont = new Font(bf, 11, Font.NORMAL);
            smallFont = new Font(bf, 9, Font.NORMAL);

        } catch (Exception e) {
            log.warn("中文字体加载失败，回退到 HELVETICA: {}", e.getMessage());
            titleFont = new Font(Font.HELVETICA, 18, Font.BOLD);
            headerFont = new Font(Font.HELVETICA, 14, Font.BOLD);
            bodyFont = new Font(Font.HELVETICA, 11, Font.NORMAL);
            smallFont = new Font(Font.HELVETICA, 9, Font.NORMAL);
        }
    }

    public byte[] generate(StorageApplicationVO data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 50, 50, 70, 50);

        try {
            PdfWriter writer = PdfWriter.getInstance(doc, out);

            // === 页眉/页脚 ===
            writer.setPageEvent(new PdfPageEventHelper() {
                @Override
                public void onEndPage(PdfWriter writer, Document document) {
                    PdfContentByte cb = writer.getDirectContent();
                    Phrase footer = new Phrase(
                        "教育部高校学生司（高校毕业生就业服务司）制    文件编制时间：2026年3月    -" + writer.getPageNumber() + "-",
                        smallFont);
                    ColumnText.showTextAligned(cb, Element.ALIGN_CENTER,
                        footer,
                        (document.left() + document.right()) / 2,
                        20, 0);
                }
            });

            doc.open();

            // === 主标题 ===
            Paragraph title = new Paragraph("高校开放共享\u201C微专业\u201D资源平台推荐表", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(10);
            doc.add(title);

            // === 模块1：表头信息表（3行4列）===
            PdfPTable headerTable = new PdfPTable(4);
            headerTable.setWidthPercentage(100);
            headerTable.setWidths(new float[]{20, 30, 20, 30});

            addHeaderCell(headerTable, "申报高校", 20);
            addCell(headerTable, notNull(data.getTitle()));
            addHeaderCell(headerTable, "微专业名称", 20);
            addCell(headerTable, notNull(data.getMicroSpecialtyName()));

            addHeaderCell(headerTable, "专业负责人", 20);
            addCell(headerTable, notNull(data.getLeadName()));
            addHeaderCell(headerTable, "联系电话", 20);
            addCell(headerTable, notNull(data.getContactPhone()));

            addHeaderCell(headerTable, "申请时间", 20);
            addCell(headerTable, notNull(data.getApplyDate()));
            addHeaderCell(headerTable, "", 20);
            addCell(headerTable, "");

            doc.add(headerTable);
            doc.add(new Paragraph("\n"));

            // === 模块2：一、微专业基本情况 ===
            Paragraph h2 = new Paragraph("一、微专业基本情况", headerFont);
            h2.setSpacingAfter(6);
            doc.add(h2);

            PdfPTable infoTable = new PdfPTable(4);
            infoTable.setWidthPercentage(100);
            infoTable.setWidths(new float[]{20, 30, 20, 30});

            addPair(infoTable, "类型", notNull(data.getType()), "面向对象", notNull(data.getTargetAudience()));
            addPair(infoTable, "面向学科及专业", notNull(data.getTargetDisciplines()), "总学分", strOrEmpty(data.getTotalCredits()));
            addPair(infoTable, "课程门数", strOrEmpty(data.getCourseCount()), "共建高校", notNull(data.getCoBuildUniversities()));
            addPair(infoTable, "拟共享高校", notNull(data.getPlannedShareUniversities()), "招生名额", strOrEmpty(data.getEnrollmentQuota()));
            addPair(infoTable, "成班人数", strOrEmpty(data.getClassSize()), "开课时间", notNull(data.getStartDate()));
            addPair(infoTable, "学制", notNull(data.getDuration()), "是否产教融合", boolToStr(data.getIsIndustryAcademic()));
            addPair(infoTable, "产教合作单位", notNull(data.getIndustryPartners()), "", "");
            doc.add(infoTable);

            // 富文本区域
            doc.add(new Paragraph("\n微专业介绍：", bodyFont));
            doc.add(new Paragraph(notNull(data.getIntroduction()), bodyFont));
            doc.add(new Paragraph("\n社会需求及就业前景分析：", bodyFont));
            doc.add(new Paragraph(notNull(data.getMarketDemandAnalysis()), bodyFont));
            doc.add(new Paragraph("\n微专业简介（专业定位、培养目标...）：", bodyFont));
            doc.add(new Paragraph(notNull(data.getSpecialtyOverview()), bodyFont));
            doc.add(new Paragraph("\n课程体系设置情况：", bodyFont));
            doc.add(new Paragraph(notNull(data.getCurriculumDesign()), bodyFont));
            doc.add(new Paragraph("\n建设条件保障：", bodyFont));
            doc.add(new Paragraph(notNull(data.getConstructionGuarantee()), bodyFont));

            // === 课程体系动态表格 ===
            if (data.getCourses() != null && !data.getCourses().isEmpty()) {
                doc.add(new Paragraph("\n课程体系：", headerFont));
                PdfPTable ct = new PdfPTable(5);
                ct.setWidthPercentage(100);
                String[] headers = {"模块", "课程名称", "学时", "学分", "开课学期"};
                for (String h : headers) addHeaderCell(ct, h, 10);

                int totalHours = 0;
                for (ProposalCourseItem c : data.getCourses()) {
                    addCell(ct, notNull(c.getModuleName()));
                    addCell(ct, notNull(c.getCourseName()));
                    addCell(ct, strOrEmpty(c.getHours()));
                    addCell(ct, decOrEmpty(c.getCredits()));
                    addCell(ct, notNull(c.getSemester()));
                    totalHours += c.getHours() != null ? c.getHours() : 0;
                }
                // 合计行
                PdfPTable totalRow = new PdfPTable(5);
                totalRow.setWidthPercentage(100);
                addHeaderCell(totalRow, "总学时", 10);
                addHeaderCell(totalRow, String.valueOf(totalHours), 10);
                addHeaderCell(totalRow, "", 10);
                addHeaderCell(totalRow, "", 10);
                addHeaderCell(totalRow, "", 10);
                doc.add(totalRow);
            }

            // === 模块3：教学团队 ===
            doc.add(new Paragraph("二、微专业教学团队情况", headerFont));
            doc.add(new Paragraph("\n专业负责人信息：", bodyFont));

            PdfPTable leadTable = new PdfPTable(4);
            leadTable.setWidthPercentage(100);
            leadTable.setWidths(new float[]{20, 30, 20, 30});
            addPair(leadTable, "姓名", notNull(data.getLeadName()), "职称", notNull(data.getLeadTitle()));
            addPair(leadTable, "职务", notNull(data.getLeadPosition()), "联系电话", notNull(data.getLeadPhone()));
            doc.add(leadTable);

            doc.add(new Paragraph("主要研究方向：" + notNull(data.getLeadResearchDirection()), bodyFont));
            doc.add(new Paragraph("承担主要任务与主讲课程：" + notNull(data.getLeadMainTasks()), bodyFont));

            // 近三年主讲课程（最多5条）
            if (data.getLeadCourses() != null && !data.getLeadCourses().isEmpty()) {
                doc.add(new Paragraph("\n近三年主讲课程：", bodyFont));
                PdfPTable lt = new PdfPTable(3);
                lt.setWidthPercentage(100);
                addHeaderCell(lt, "课程名称", 10);
                addHeaderCell(lt, "学分", 10);
                addHeaderCell(lt, "学时", 10);
                for (ProposalLeadCourseItem lc : data.getLeadCourses()) {
                    addCell(lt, notNull(lc.getCourseName()));
                    addCell(lt, decOrEmpty(lc.getCredits()));
                    addCell(lt, strOrEmpty(lc.getHours()));
                }
                doc.add(lt);
            }

            // 教学团队成员动态表
            if (data.getTeamMembers() != null && !data.getTeamMembers().isEmpty()) {
                doc.add(new Paragraph("\n教学团队成员：", bodyFont));
                PdfPTable mt = new PdfPTable(6);
                mt.setWidthPercentage(100);
                String[] mh = {"姓名", "年龄", "职称", "所在单位", "曾授课程", "拟授课程"};
                for (String h : mh) addHeaderCell(mt, h, 8);
                for (ProposalTeamMemberItem m : data.getTeamMembers()) {
                    addCell(mt, notNull(m.getName()));
                    addCell(mt, strOrEmpty(m.getAge()));
                    addCell(mt, notNull(m.getTitle()));
                    addCell(mt, notNull(m.getOrganization()));
                    addCell(mt, notNull(m.getTaughtCourses()));
                    addCell(mt, notNull(m.getPlannedCourses()));
                }
                doc.add(mt);
            }

            // === 模块4：三级签字 ===
            doc.add(new Paragraph("三、牵头单位意见", headerFont));
            if (data.getSignatures() != null) {
                for (ProposalSignatureItem sig : data.getSignatures()) {
                    if ("SHARED_UNIT".equals(sig.getSignLevel())) {
                        continue; // shared unit signatures handled in module 5
                    }
                    doc.add(new Paragraph("\n" + getSignLevelLabel(sig.getSignLevel()) + "：", bodyFont));
                    doc.add(new Paragraph("意见：" + notNull(sig.getOpinionText()), bodyFont));

                    if ("TEXT".equals(sig.getSignatureType()) && sig.getSignatureText() != null) {
                        doc.add(new Paragraph("负责人签字：" + sig.getSignatureText(), bodyFont));
                    } else if ("IMAGE".equals(sig.getSignatureType()) && sig.getSignatureImageUrl() != null) {
                        try {
                            URL imgUrl = new URL(sig.getSignatureImageUrl());
                            // SSRF protection: block private/internal IP ranges
                            String host = imgUrl.getHost();
                            if (host == null || host.equals("localhost") || host.equals("127.0.0.1") || host.startsWith("10.") || host.startsWith("172.16.") || host.startsWith("192.168.") || host.equals("[::1]") || host.endsWith(".local")) {
                                log.warn("SSRF blocked: rejecting private URL host={}", host);
                                doc.add(new Paragraph("[签名图片-地址无效]", bodyFont));
                            } else {
                                Image img = Image.getInstance(imgUrl);
                                img.scaleToFit(100, 40);
                                doc.add(img);
                            }
                        } catch (Exception e) {
                            log.warn("签名图片加载失败: {}", e.getMessage());
                            doc.add(new Paragraph("[签名图片]", bodyFont));
                        }
                    }

                    // 公章图片
                    if (sig.getSealImageUrl() != null && !sig.getSealImageUrl().isEmpty()) {
                        try {
                            Image seal = Image.getInstance(new URL(sig.getSealImageUrl()));
                            seal.scaleToFit(80, 80);
                            doc.add(seal);
                        } catch (Exception e) {
                            doc.add(new Paragraph("[公章图片]", bodyFont));
                        }
                    }

                    // 如无图片，留空白框
                    if (sig.getSealImageUrl() == null || sig.getSealImageUrl().isEmpty()) {
                        PdfPTable emptyBox = new PdfPTable(1);
                        emptyBox.setWidthPercentage(30);
                        PdfPCell emptyCell = new PdfPCell(new Phrase("（盖章处）", smallFont));
                        emptyCell.setFixedHeight(50);
                        emptyCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                        emptyCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                        emptyBox.addCell(emptyCell);
                        doc.add(emptyBox);
                    }

                    doc.add(new Paragraph("日期：" + notNull(sig.getSignDate()), bodyFont));
                }
            }

            // === 模块5：共建共享单位 ===
            if (data.getSharedUnits() != null && !data.getSharedUnits().isEmpty()) {
                doc.add(new Paragraph("四、共建共享单位意见", headerFont));

                for (ProposalSharedUnitItem unit : data.getSharedUnits()) {
                    doc.add(new Paragraph("\n单位：" + notNull(unit.getUnitName()) + "（" + getUnitTypeLabel(unit.getUnitType()) + "）", bodyFont));

                    // 查找该单位的签字
                    if (data.getSignatures() != null) {
                        for (ProposalSignatureItem sig : data.getSignatures()) {
                            if ("SHARED_UNIT".equals(sig.getSignLevel()) &&
                                unit.getSortOrder() != null && sig.getUnitSeq() != null &&
                                unit.getSortOrder().equals(sig.getUnitSeq())) {
                                doc.add(new Paragraph("意见：" + notNull(sig.getOpinionText()), bodyFont));
                                doc.add(new Paragraph("备注：" + notNull(sig.getRemark()), bodyFont));
                            }
                        }
                    }
                }
            }

        } catch (Exception e) {
            throw new RuntimeException("PDF 生成失败", e);
        } finally {
            doc.close();
        }
        return out.toByteArray();
    }

    // ====== 辅助方法 ======

    private void addCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, smallFont));
        cell.setPadding(4);
        table.addCell(cell);
    }

    private void addHeaderCell(PdfPTable table, String text, float fontSize) {
        PdfPCell cell = new PdfPCell(new Phrase(text, smallFont));
        cell.setPadding(4);
        cell.setBackgroundColor(new Color(240, 240, 240));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(cell);
    }

    private void addPair(PdfPTable table, String label1, String value1, String label2, String value2) {
        addCell(table, label1);
        addCell(table, value1);
        addCell(table, label2);
        addCell(table, value2);
    }

    private String notNull(String s) { return s != null ? s : ""; }
    private String strOrEmpty(Integer i) { return i != null ? i.toString() : ""; }
    private String decOrEmpty(java.math.BigDecimal d) { return d != null ? d.toString() : ""; }
    private String boolToStr(Boolean b) { return b != null && b ? "是" : "否"; }

    private String getSignLevelLabel(String level) {
        if (level == null) return "";
        switch (level) {
            case "LEAD": return "微专业负责人意见";
            case "DEPT": return "学院意见";
            case "SCHOOL": return "学校意见";
            case "SHARED_UNIT": return "共建共享单位意见";
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
