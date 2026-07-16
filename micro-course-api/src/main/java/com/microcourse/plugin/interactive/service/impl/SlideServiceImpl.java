package com.microcourse.plugin.interactive.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.entity.Course;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.plugin.interactive.dto.SegmentAudioVO;
import com.microcourse.plugin.interactive.dto.SlidePageVO;
import com.microcourse.plugin.interactive.dto.SlideUploadResponse;
import com.microcourse.plugin.interactive.dto.SlideVO;
import com.microcourse.plugin.interactive.entity.CourseSlide;
import com.microcourse.plugin.interactive.entity.SlidePage;
import com.microcourse.plugin.interactive.mapper.CourseSlideMapper;
import com.microcourse.plugin.interactive.mapper.SlidePageMapper;
import com.microcourse.plugin.interactive.service.SlideService;
import com.microcourse.plugin.interactive.util.HtmlSanitizer;
import com.microcourse.entity.CourseChapter;
import com.microcourse.entity.CourseSection;
import com.microcourse.repository.CourseChapterRepository;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.CourseSectionRepository;
import com.microcourse.util.SecurityUtil;
import com.microcourse.util.XssSanitizer;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
@ConditionalOnProperty(value = "plugin.interactive.enabled", havingValue = "true", matchIfMissing = true)
public class SlideServiceImpl implements SlideService {

    private static final Logger log = LoggerFactory.getLogger(SlideServiceImpl.class);

    private final CourseSlideMapper courseSlideMapper;
    private final SlidePageMapper slidePageMapper;
    private final CourseRepository courseRepository;
    private final CourseChapterRepository courseChapterRepository;
    private final CourseSectionRepository sectionRepo;
    private final SlideRenderService slideRenderService;

    @Value("${plugin.interactive.slides.storage-path:/data/slides}")
    private String storagePath;

    @Value("${plugin.interactive.html-content.max-file-size:5242880}")
    private long maxHtmlSize;

    public SlideServiceImpl(CourseSlideMapper courseSlideMapper,
                            SlidePageMapper slidePageMapper,
                            CourseRepository courseRepository,
                            CourseChapterRepository courseChapterRepository,
                            CourseSectionRepository sectionRepo,
                            SlideRenderService slideRenderService) {
        this.courseSlideMapper = courseSlideMapper;
        this.slidePageMapper = slidePageMapper;
        this.courseRepository = courseRepository;
        this.courseChapterRepository = courseChapterRepository;
        this.sectionRepo = sectionRepo;
        this.slideRenderService = slideRenderService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SlideUploadResponse upload(Long courseId, String originalFilename, byte[] fileBytes, Long chapterId, Long sectionId) {
        Course course = courseRepository.selectById(courseId);
        if (course == null) { throw new BusinessException(ErrorCode.COURSE_NOT_FOUND); }
        if (!SecurityUtil.isOwnerOrAdmin(course.getTeacherId())) { throw new BusinessException(ErrorCode.NO_PERMISSION); }
        if (chapterId != null) {
            CourseChapter ch = courseChapterRepository.selectById(chapterId);
            if (ch == null || !ch.getCourseId().equals(courseId)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "章节 ID 不属于该课程");
            }
        }
        if (sectionId != null) {
            CourseSection sec = sectionRepo.selectById(sectionId);
            if (sec == null || !sec.getCourseId().equals(courseId)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "课时 ID 不属于该课程");
            }
        }
        originalFilename = XssSanitizer.sanitizePlainText(originalFilename);
        if (originalFilename == null || originalFilename.isBlank()) { throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "文件名不能为空"); }
        if (!originalFilename.toLowerCase().endsWith(".pptx")) { throw new BusinessException(ErrorCode.PPT_FORMAT_INVALID); }
        if (!isZipHeader(fileBytes)) { throw new BusinessException(ErrorCode.PPT_FORMAT_INVALID); }
        if (fileBytes.length > maxHtmlSize * 10) { throw new BusinessException(ErrorCode.PPT_FORMAT_INVALID); }
        if (!validateZipBomb(fileBytes)) { throw new BusinessException(ErrorCode.PPT_PARSE_FAILED); }

        String fileHash = sha256(fileBytes);
        // UPSERT：按 (courseId, chapterId, sectionId) 查询现有 slide，命中则更新内容
        LambdaQueryWrapper<CourseSlide> qw = new LambdaQueryWrapper<>();
        qw.eq(CourseSlide::getCourseId, courseId);
        if (chapterId != null) {
            qw.eq(CourseSlide::getChapterId, chapterId);
        } else {
            qw.isNull(CourseSlide::getChapterId);
        }
        if (sectionId != null) {
            qw.eq(CourseSlide::getSectionId, sectionId);
        } else {
            qw.isNull(CourseSlide::getSectionId);
        }
        CourseSlide old = courseSlideMapper.selectOne(qw);
        Long sid;
        if (old != null) {
            // 已有同 chapter 的 slide：复用 ID，UPSERT 内容
            sid = old.getId();
            old.setFileName(originalFilename);
            old.setFileUrl("pending");
            old.setStatus(0);
            old.setErrorMessage(null);
            old.setFileHash(fileHash);
            old.setUpdatedAt(LocalDateTime.now());
            int affectedSlide = courseSlideMapper.updateById(old);
            if (affectedSlide == 0) {
                throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION, "课件已被修改，请刷新后重试");
            }
            // 清掉旧 slide_pages — 防止重新上传 PPTX 时新旧页面混在一起
            int oldPageCount = slidePageMapper.delete(
                new LambdaQueryWrapper<SlidePage>().eq(SlidePage::getSlideId, sid));
            log.info("[SlideUpload] UPSERT existing slide: id={}, courseId={}, chapterId={}, oldPagesDeleted={}",
                    sid, courseId, chapterId, oldPageCount);
        } else {
            CourseSlide slide = new CourseSlide();
            slide.setCourseId(courseId); slide.setFileName(originalFilename); slide.setFileUrl("pending");
            slide.setStatus(0); slide.setFileHash(fileHash);
            if (chapterId != null) { slide.setChapterId(chapterId); }
            if (sectionId != null) { slide.setSectionId(sectionId); }
            slide.setCreatedAt(LocalDateTime.now()); slide.setUpdatedAt(LocalDateTime.now());
            courseSlideMapper.insert(slide);
            sid = slide.getId();
            log.info("[SlideUpload] NEW slide: id={}, courseId={}, chapterId={}, sectionId={}",
                    sid, courseId, chapterId, sectionId);
        }

        Path courseDir = Paths.get(storagePath, String.valueOf(courseId));
        try {
            Files.createDirectories(courseDir);
            Path pptxPath = courseDir.resolve("original.pptx");
            Files.write(pptxPath, fileBytes);
            CourseSlide toUpdate = courseSlideMapper.selectById(sid);
            toUpdate.setFileUrl(pptxPath.toString());
            int affectedFileUrl = courseSlideMapper.updateById(toUpdate);
            if (affectedFileUrl == 0) {
                throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION, "课件文件路径更新失败，请刷新后重试");
            }
        } catch (IOException e) {
            CourseSlide toUpdate = courseSlideMapper.selectById(sid);
            toUpdate.setStatus(3); toUpdate.setErrorMessage("文件保存失败");
            log.error("[SlideUpload] IO异常 courseId={}", courseId, e);
            toUpdate.setUpdatedAt(LocalDateTime.now());
            courseSlideMapper.updateById(toUpdate);
            throw new BusinessException(ErrorCode.PPT_PARSE_FAILED);
        }
        // 回写 section.content_url — 与上传同事务，避免 @Version 冲突
        if (sectionId != null && sectionRepo != null) {
            CourseSection sec = sectionRepo.selectById(sectionId);
            if (sec != null) {
                log.info("[SlideUpload] Writing content_url for section={}, course={}", sectionId, courseId);
                sec.setContentUrl("/api/courses/" + courseId + "/sections/" + sectionId + "/slide");
                sec.setUpdatedAt(LocalDateTime.now());
                int affected = sectionRepo.updateById(sec);
                if (affected == 0) {
                    throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION,
                            "content_url 写入失败（版本冲突）: sectionId=" + sectionId);
                }
                log.info("[SlideUpload] content_url affectedRows={}, section={}, version={}",
                        affected, sectionId, sec.getVersion());
            } else {
                log.warn("[SlideUpload] Section not found for content_url: sectionId={}", sectionId);
            }
        }
        Long fc = chapterId;
        Long fs = sectionId;
        byte[] fb = fileBytes;
        Long finalSid = sid;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() { slideRenderService.renderAsync(finalSid, fc, fs, fb); }
        });
        SlideUploadResponse r = new SlideUploadResponse();
        r.setSlideId(sid); r.setTotalPages(0); r.setStatus(0); r.setMessage("上传成功，正在后台渲染...");
        return r;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SlideUploadResponse uploadHtmlFile(Long courseId, MultipartFile file, Long chapterId, Long sectionId) {
        Course course = courseRepository.selectById(courseId);
        if (course == null) { throw new BusinessException(ErrorCode.COURSE_NOT_FOUND); }
        if (!SecurityUtil.isOwnerOrAdmin(course.getTeacherId())) { throw new BusinessException(ErrorCode.NO_PERMISSION); }
        if (chapterId != null) {
            CourseChapter ch = courseChapterRepository.selectById(chapterId);
            if (ch == null || !ch.getCourseId().equals(courseId)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "章节 ID 不属于该课程");
            }
        }
        if (sectionId != null) {
            CourseSection sec = sectionRepo.selectById(sectionId);
            if (sec == null || !sec.getCourseId().equals(courseId)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "课时 ID 不属于该课程");
            }
        }
        if (file.getSize() > maxHtmlSize) { throw new BusinessException(ErrorCode.HTML_TOO_LARGE); }
        String rawHtml;
        try {
            rawHtml = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.HTML_INVALID, "HTML 文件读取失败");
        }
        String safeHtml = HtmlSanitizer.sanitizeForCourseware(rawHtml);
        if (safeHtml.isEmpty() && !rawHtml.isEmpty()) { throw new BusinessException(ErrorCode.HTML_SANITIZE_REMOVED_ALL); }

        String safeFilename = XssSanitizer.sanitizePlainText(
                file.getOriginalFilename() != null ? file.getOriginalFilename() : "slide.html");
        if (safeFilename == null || safeFilename.isBlank()) { safeFilename = "slide.html"; }

        // UPSERT：按 (courseId, chapterId, sectionId) 复用 slide_id
        LambdaQueryWrapper<CourseSlide> qw = new LambdaQueryWrapper<>();
        qw.eq(CourseSlide::getCourseId, courseId);
        if (chapterId != null) {
            qw.eq(CourseSlide::getChapterId, chapterId);
        } else {
            qw.isNull(CourseSlide::getChapterId);
        }
        if (sectionId != null) {
            qw.eq(CourseSlide::getSectionId, sectionId);
        } else {
            qw.isNull(CourseSlide::getSectionId);
        }
        CourseSlide existing = courseSlideMapper.selectOne(qw);
        Long sid;
        if (existing != null) {
            sid = existing.getId();
            existing.setFileName(safeFilename);
            existing.setFileUrl("html:inline");
            existing.setStatus(2);
            try { existing.setFileHash(sha256(file.getBytes())); }
            catch (IOException e) { existing.setFileHash(""); }
            existing.setUpdatedAt(LocalDateTime.now());
            int affectedHtml = courseSlideMapper.updateById(existing);
            if (affectedHtml == 0) {
                throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION, "HTML 课件已被修改，请刷新后重试");
            }
            log.info("[SlideUpload-HtmlFile] UPSERT: slideId={}, courseId={}, chapterId={}", sid, courseId, chapterId);
        } else {
            CourseSlide slide = new CourseSlide();
            slide.setCourseId(courseId);
            slide.setFileName(safeFilename);
            slide.setFileUrl("html:inline");
            slide.setTotalPages(1);
            slide.setStatus(2);
            try { slide.setFileHash(sha256(file.getBytes())); }
            catch (IOException e) { slide.setFileHash(""); }
            if (chapterId != null) { slide.setChapterId(chapterId); }
            if (sectionId != null) { slide.setSectionId(sectionId); }
            slide.setCreatedAt(LocalDateTime.now());
            slide.setUpdatedAt(LocalDateTime.now());
            courseSlideMapper.insert(slide);
            sid = slide.getId();
            log.info("[SlideUpload-HtmlFile] NEW: slideId={}, courseId={}, chapterId={}, sectionId={}",
                    sid, courseId, chapterId, sectionId);
        }
        // 删除该 slide 旧的 pages（一对一覆盖）
        slidePageMapper.delete(new LambdaQueryWrapper<SlidePage>().eq(SlidePage::getSlideId, sid));
        SlidePage page = new SlidePage();
        page.setSlideId(sid);
        page.setCourseId(courseId);
        page.setChapterId(chapterId);
        if (sectionId != null) { page.setSectionId(sectionId); }
        page.setPageNumber(1);
        page.setContentType("HTML_DIRECT");
        page.setHtmlContent(safeHtml);
        page.setImageUrl("html:no-image");
        page.setNarrationStatus("PENDING");
        page.setCreatedAt(LocalDateTime.now());
        page.setUpdatedAt(LocalDateTime.now());
        slidePageMapper.insert(page);
        // 回写 section.content_url — 与上传同事务
        if (sectionId != null && sectionRepo != null) {
            CourseSection sec = sectionRepo.selectById(sectionId);
            if (sec != null) {
                log.info("[SlideUpload-HtmlFile] Writing content_url for section={}, course={}", sectionId, courseId);
                sec.setContentUrl("/api/courses/" + courseId + "/sections/" + sectionId + "/slide");
                sec.setUpdatedAt(LocalDateTime.now());
                int affected = sectionRepo.updateById(sec);
                if (affected == 0) {
                    throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION,
                            "content_url 写入失败（版本冲突）: sectionId=" + sectionId);
                }
                log.info("[SlideUpload-HtmlFile] content_url affectedRows={}, section={}",
                        affected, sectionId);
            } else {
                log.warn("[SlideUpload-HtmlFile] Section not found for content_url: sectionId={}", sectionId);
            }
        }
        SlideUploadResponse resp = new SlideUploadResponse();
        resp.setSlideId(sid);
        resp.setTotalPages(1);
        resp.setStatus(2);
        resp.setMessage("HTML file upload success");
        return resp;
    }

    @Override
    public void tryConvertPptxToHtml(Long slideId, byte[] pptxBytes) {
        log.info("[PPTtoHTML] convert request slideId={}, size={}", slideId, pptxBytes.length);
        try {
            String html = convertPptxToHtmlString(pptxBytes);
            String safeHtml = HtmlSanitizer.sanitize(html);
            if (safeHtml.isEmpty()) {
                log.warn("[PPTtoHTML] sanitize removed all content slideId={}", slideId);
                return;
            }
            // 更新第一页（索引 0）的 htmlContent（如果存在）
            LambdaQueryWrapper<SlidePage> qw = new LambdaQueryWrapper<>();
            qw.eq(SlidePage::getSlideId, slideId).orderByAsc(SlidePage::getPageNumber).last("LIMIT 1");
            SlidePage firstPage = slidePageMapper.selectOne(qw);
            if (firstPage != null) {
                firstPage.setContentType("HTML_DIRECT");
                firstPage.setHtmlContent(safeHtml);
                int affected = slidePageMapper.updateById(firstPage);
                if (affected > 0) {
                    log.info("[PPTtoHTML] convert success slideId={}, firstPageId={}, htmlSize={}",
                            slideId, firstPage.getId(), safeHtml.length());
                } else {
                    log.warn("[PPTtoHTML] update 0 rows for slideId={}", slideId);
                }
            } else {
                log.warn("[PPTtoHTML] no pages found for slideId={}", slideId);
            }
        } catch (Exception e) {
            // PPT→HTML 是尽力而为的非关键路径，失败不抛异常
            log.warn("[PPTtoHTML] convert failed slideId={}, error={}", slideId, e.getMessage());
        }
    }

    /**
     * 将 PPTX 字节数组转为语义 HTML 字符串。
     * 提取每张幻灯片的文本内容（标题+正文），封装为结构化 HTML。
     * 失败时返回空字符串（非关键路径，不抛异常）。
     */
    private String convertPptxToHtmlString(byte[] pptxBytes) {
        try (org.apache.poi.xslf.usermodel.XMLSlideShow ppt = new org.apache.poi.xslf.usermodel.XMLSlideShow(
                new ByteArrayInputStream(pptxBytes))) {
            List<XSLFSlide> slides = ppt.getSlides();
            if (slides.isEmpty()) { return ""; }
            StringBuilder html = new StringBuilder();
            html.append("<div class=\"pptx-html-converted\">\n");
            for (int i = 0; i < slides.size(); i++) {
                XSLFSlide slide = slides.get(i);
                html.append("  <div class=\"slide page-").append(i + 1).append("\">\n");
                // 提取标题（第一个有字体的形状作为标题）
                boolean hasTitle = false;
                for (XSLFTextShape shape : slide.getPlaceholders()) {
                    String text = extractShapeText(shape);
                    if (!text.isEmpty()) {
                        html.append("    <h2>").append(escapeHtml(text)).append("</h2>\n");
                        hasTitle = true;
                        break;
                    }
                }
                // 提取正文文本（非标题形状）
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape && !isTitlePlaceholder((XSLFTextShape) shape)) {
                        String text = extractShapeText((XSLFTextShape) shape);
                        if (!text.isEmpty()) {
                            html.append("    <p>").append(escapeHtml(text)).append("</p>\n");
                        }
                    }
                }
                html.append("  </div>\n");
            }
            html.append("</div>\n");
            return html.toString();
        } catch (Exception e) {
            log.warn("[PPTtoHTML] parse failed: {}", e.getMessage());
            return "";
        }
    }

    private String extractShapeText(XSLFTextShape shape) {
        StringBuilder sb = new StringBuilder();
        for (XSLFTextParagraph para : shape.getTextParagraphs()) {
            for (XSLFTextRun run : para.getTextRuns()) {
                sb.append(run.getRawText());
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    private boolean isTitlePlaceholder(XSLFTextShape shape) {
        try {
            String name = shape.getShapeName();
            return name != null && (name.toLowerCase().contains("title") || name.contains("标题"));
        } catch (Exception e) {
            return false;
        }
    }

    private String escapeHtml(String raw) {
        return raw.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }


    @Override
    public List<SlideVO> listByCourseId(Long courseId) {
        List<CourseSlide> slides = courseSlideMapper.selectList(
                new LambdaQueryWrapper<CourseSlide>()
                        .eq(CourseSlide::getCourseId, courseId)
                        .orderByAsc(CourseSlide::getSectionId));
        if (slides.isEmpty()) return java.util.Collections.emptyList();
        java.util.Set<Long> chapterIds = slides.stream()
                .map(CourseSlide::getChapterId).filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        java.util.Set<Long> sectionIds = slides.stream()
                .map(CourseSlide::getSectionId).filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        java.util.Map<Long, CourseChapter> chapterCache = chapterIds.isEmpty()
                ? java.util.Collections.emptyMap()
                : courseChapterRepository.selectBatchIds(chapterIds).stream()
                        .collect(java.util.stream.Collectors.toMap(CourseChapter::getId, c -> c));
        java.util.Map<Long, CourseSection> sectionCache = sectionIds.isEmpty()
                ? java.util.Collections.emptyMap()
                : sectionRepo.selectBatchIds(sectionIds).stream()
                        .collect(java.util.stream.Collectors.toMap(CourseSection::getId, s -> s));
        return slides.stream()
                .map(s -> toVO(s, chapterCache, sectionCache))
                .collect(Collectors.toList());
    }

    @Override
    public SlideVO getByCourseId(Long courseId) {
        LambdaQueryWrapper<CourseSlide> qw = new LambdaQueryWrapper<>();
        qw.eq(CourseSlide::getCourseId, courseId);
        List<CourseSlide> slides = courseSlideMapper.selectList(qw);
        if (slides.isEmpty()) return null;
        if (slides.size() == 1) return toVO(slides.get(0));
        // 多 slide 场景：返回第一个（兼容旧逻辑），scripts 等业务应使用 listByCourseId
        return toVO(slides.get(0));
    }

    @Override
    public List<SlidePageVO> getPages(Long courseId, Long sectionId) {
        LambdaQueryWrapper<SlidePage> qw = new LambdaQueryWrapper<>();
        qw.eq(SlidePage::getCourseId, courseId);
        if (sectionId != null) { qw.eq(SlidePage::getSectionId, sectionId); }
        qw.orderByAsc(SlidePage::getSlideId).orderByAsc(SlidePage::getPageNumber);
        List<SlidePage> dbPages = slidePageMapper.selectList(qw);
        List<SlidePageVO> vos = dbPages.stream().map(this::toPageVO).collect(Collectors.toList());

        Map<Integer, String> segUrls = new HashMap<>();
        for (SlidePageVO vo : vos) {
            if (vo.getSegmentAudio() != null) {
                segUrls.put(vo.getPageNumber(), vo.getSegmentAudio().getUrl());
            }
        }
        if (!segUrls.isEmpty()) {
            String segmentControllerJs = buildSegmentControllerJs();
            for (SlidePageVO vo : vos) {
                String html = vo.getHtmlContent();
                if ("HTML_DIRECT".equals(vo.getContentType()) && html != null && html.contains("AUDIO_SEG_")) {
                    for (Map.Entry<Integer, String> entry : segUrls.entrySet()) {
                        String placeholder = "AUDIO_SEG_" + String.format("%02d", entry.getKey()) + "_URL";
                        html = html.replace(placeholder, entry.getValue());
                    }
                    html = injectBeforeBodyEnd(html, segmentControllerJs);
                    vo.setHtmlContent(html);
                }
            }
        }

        return vos;
    }

    @Override
    public List<SegmentAudioVO> getSegmentAudios(Long courseId, Long sectionId) {
        List<SlidePageVO> pages = getPages(courseId, sectionId);
        return pages.stream()
                .filter(p -> p.getSegmentAudio() != null)
                .map(p -> p.getSegmentAudio())
                .collect(Collectors.toList());
    }

    @Override
    public SlidePageVO getPage(Long courseId, Integer pageNumber) {
        verifyOwner(courseId);
        LambdaQueryWrapper<SlidePage> qw = new LambdaQueryWrapper<SlidePage>()
                .eq(SlidePage::getCourseId, courseId).eq(SlidePage::getPageNumber, pageNumber);
        List<SlidePage> list = slidePageMapper.selectList(qw);
        if (list.isEmpty()) { throw new BusinessException(ErrorCode.SLIDE_PAGE_NOT_FOUND); }
        return toPageVO(list.get(0));
    }

    @Override
    public byte[] getPageImage(Long courseId, Integer pageNumber) {
        SlidePageVO p = getPage(courseId, pageNumber);
        String fn = p.getFileUuid() != null ? p.getFileUuid() + ".png" : "page_" + pageNumber + ".png";
        Path imgPath = Paths.get(storagePath, String.valueOf(courseId), String.valueOf(p.getSlideId()), "images", fn);
        byte[] d = readImage(imgPath);
        return d.length > 0 ? d : generateFallback("第" + pageNumber + "页");
    }

    @Override
    public byte[] getPageThumbnail(Long courseId, Integer pageNumber) {
        SlidePageVO p = getPage(courseId, pageNumber);
        String fn = p.getFileUuid() != null ? p.getFileUuid() + "_thumbnail.png" : "page_" + pageNumber + ".png";
        Path thumbPath = Paths.get(storagePath, String.valueOf(courseId), String.valueOf(p.getSlideId()), "thumbnails", fn);
        byte[] d = readImage(thumbPath);
        return d.length > 0 ? d : generateFallback("第" + pageNumber + "页");
    }

    private byte[] readImage(Path path) {
        try { return Files.readAllBytes(path); }
        catch (NoSuchFileException e) { return new byte[0]; }
        catch (IOException e) { log.error("[Slide] 读取图片失败", e); return new byte[0]; }
    }

    private byte[] generateFallback(String text) {
        int w = 640; int h = 480;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(245, 245, 245)); g.fillRect(0, 0, w, h);
            g.setColor(new Color(180, 180, 180));
            g.setFont(new Font("SansSerif", Font.PLAIN, 18));
            FontMetrics fm = g.getFontMetrics();
            g.drawString(text, (w - fm.stringWidth(text)) / 2, (h - fm.getHeight()) / 2 + fm.getAscent());
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "PNG", baos);
            return baos.toByteArray();
        } catch (IOException e) { return new byte[0]; }
        finally { g.dispose(); img.flush(); }
    }

    private SlideVO toVO(CourseSlide s) {
        return toVO(s, null, null);
    }

    private SlideVO toVO(CourseSlide s,
                         java.util.Map<Long, CourseChapter> chapterCache,
                         java.util.Map<Long, CourseSection> sectionCache) {
        SlideVO vo = new SlideVO();
        vo.setId(s.getId()); vo.setCourseId(s.getCourseId()); vo.setFileName(s.getFileName());
        vo.setTotalPages(s.getTotalPages()); vo.setStatus(s.getStatus());
        vo.setStatusText(SlideVO.statusText(s.getStatus()));
        vo.setErrorMessage(s.getErrorMessage());
        vo.setCreatedAt(s.getCreatedAt()); vo.setUpdatedAt(s.getUpdatedAt());
        vo.setChapterId(s.getChapterId());
        vo.setSectionId(s.getSectionId());
        if (chapterCache != null && s.getChapterId() != null) {
            CourseChapter chapter = chapterCache.get(s.getChapterId());
            if (chapter != null) vo.setChapterTitle(chapter.getTitle());
        } else if (s.getChapterId() != null) {
            CourseChapter chapter = courseChapterRepository.selectById(s.getChapterId());
            if (chapter != null) vo.setChapterTitle(chapter.getTitle());
        }
        if (sectionCache != null && s.getSectionId() != null) {
            CourseSection sec = sectionCache.get(s.getSectionId());
            if (sec != null) vo.setLessonTitle(sec.getTitle());
        } else if (s.getSectionId() != null) {
            CourseSection sec = sectionRepo.selectById(s.getSectionId());
            if (sec != null) vo.setLessonTitle(sec.getTitle());
        }
        return vo;
    }

    private SlidePageVO toPageVO(SlidePage p) {
        SlidePageVO vo = new SlidePageVO();
        vo.setId(p.getId()); vo.setSlideId(p.getSlideId()); vo.setChapterId(p.getChapterId());
        vo.setSectionId(p.getSectionId());
        vo.setCourseId(p.getCourseId()); vo.setPageNumber(p.getPageNumber());
        vo.setFileUuid(p.getFileUuid()); vo.setContentType(p.getContentType());
        vo.setNarrationScript(p.getNarrationScript()); vo.setNarrationAudioUrl(p.getNarrationAudioUrl());
        vo.setAudioDuration(p.getAudioDuration()); vo.setNarrationStatus(p.getNarrationStatus());
        vo.setNarrationStatusText(SlidePageVO.narrationStatusText(p.getNarrationStatus()));
        vo.setSegmentCount(p.getSegmentCount()); vo.setVoice(p.getVoice()); vo.setTtsModel(p.getTtsModel());
        vo.setGeneratedAt(p.getGeneratedAt());
        vo.setImageUrl(p.getImageUrl()); vo.setThumbnailUrl(p.getThumbnailUrl());
        vo.setImageWidth(p.getImageWidth()); vo.setImageHeight(p.getImageHeight());
        vo.setExtractedText(p.getExtractedText());
        vo.setHasAnimation(p.getHasAnimation()); vo.setHasEmbeddedMedia(p.getHasEmbeddedMedia());

        String htmlContent = p.getHtmlContent();
        if ("HTML_DIRECT".equals(p.getContentType()) && htmlContent != null && htmlContent.contains("AUDIO_SEG_")) {
            htmlContent = replaceAudioSegmentPlaceholders(htmlContent, p);
        }
        vo.setHtmlContent(htmlContent);

        if (p.getNarrationAudioUrl() != null && !p.getNarrationAudioUrl().isBlank()) {
            String segUrl = buildSegmentUrl(p);
            SegmentAudioVO seg = new SegmentAudioVO();
            seg.setPageNumber(p.getPageNumber());
            seg.setUrl(segUrl);
            seg.setDuration(p.getAudioDuration());
            vo.setSegmentAudio(seg);
        }
        vo.setCreatedAt(p.getCreatedAt()); vo.setUpdatedAt(p.getUpdatedAt());
        return vo;
    }

    private String buildSegmentUrl(SlidePage p) {
        String narrationUrl = p.getNarrationAudioUrl();
        if (narrationUrl == null || narrationUrl.isBlank()) {
            return null;
        }
        if (narrationUrl.contains("merged=true")) {
            int pageNum = p.getPageNumber();
            return narrationUrl.replaceFirst("/pages/\\d+/audio", "/pages/" + pageNum + "/audio");
        }
        if (narrationUrl.contains("/pages/1/audio") && narrationUrl.contains("token=")) {
            return narrationUrl;
        }
        if (!narrationUrl.contains("token=")) {
            return narrationUrl;
        }
        int pageNum = p.getPageNumber();
        return narrationUrl.replaceFirst("/pages/\\d+/audio", "/pages/" + pageNum + "/audio");
    }

    private String replaceAudioSegmentPlaceholders(String htmlContent, SlidePage p) {
        if (htmlContent == null || !htmlContent.contains("AUDIO_SEG_")) {
            return htmlContent;
        }
        String segmentUrl = buildSegmentUrl(p);
        if (segmentUrl == null || segmentUrl.isBlank()) {
            return htmlContent;
        }
        String placeholder = "AUDIO_SEG_" + String.format("%02d", p.getPageNumber()) + "_URL";
        return htmlContent.replace(placeholder, segmentUrl);
    }

    private static String buildSegmentControllerJs() {
        return "<script>" +
        "(function(){var a={},c=0,d=!1;" +
        "for(var i=1;i<=15;i++){var e=document.getElementById('segAudio_'+(i<10?'0'+i:i));if(e)a[i]=e}" +
        "function p(){var e=a[c];if(!e)return;d=!0;e.play().then(function(){parent.postMessage({type:'slide-audio-state',state:'playing'},'*')}).catch(function(){})}" +
        "function q(){d=!1;for(var k in a)a[k].pause();parent.postMessage({type:'slide-audio-state',state:'paused'},'*')}" +
        "window.addEventListener('message',function(e){if(!e.data||e.data.type!=='slide-audio')return;" +
        "switch(e.data.action){" +
        "case'play':if(!d&&c>0)p();else if(c===0){c=1;p()}break;" +
        "case'pause':q();break;" +
        "case'seek':if(e.data.page){c=e.data.page;q();p()}break;" +
        "case'get-state':parent.postMessage({type:'slide-audio-state',state:d?'playing':'paused'});break;" +
        "case'get-segments':var r={};for(var i=1;i<=15;i++){var e=a[i];if(e&&e.src)r[i]=e.src}" +
        "parent.postMessage({type:'slide-audio-segments',segments:r},'*');break;" +
        "case'speed':if(e.data.rate){for(var k in a)if(a[k])a[k].playbackRate=e.data.rate}break;" +
        "for(var k in a){!function(el){el.addEventListener('ended',function(){d=!1;" +
        "parent.postMessage({type:'slide-audio-state',state:'ended'},'*')});" +
        "el.addEventListener('loadedmetadata',function(){parent.postMessage({type:'slide-audio-state',state:'loaded',duration:el.duration},'*')});" +
        "el.addEventListener('timeupdate',function(){parent.postMessage({type:'slide-audio-state',state:'time-update',time:el.currentTime,duration:el.duration},'*')})}" +
        "(a[k])}})();</script>";
    }

    private static String injectBeforeBodyEnd(String html, String js) {
        int idx = html.lastIndexOf("</body>");
        if (idx < 0) return html + js;
        return html.substring(0, idx) + js + html.substring(idx);
    }

    private boolean isZipHeader(byte[] b) {
        if (b.length < 4) return false;
        return b[0] == 0x50 && b[1] == 0x4B && b[2] == 0x03 && b[3] == 0x04;
    }

    private boolean validateZipBomb(byte[] bytes) {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry e; int c = 0; long t = 0; byte[] buf = new byte[8192];
            while ((e = zis.getNextEntry()) != null) {
                if (++c > 1000) return false;
                int r; while ((r = zis.read(buf)) != -1) { t += r; if (t > 500L * 1024 * 1024) return false; }
            }
            return true;
        } catch (IOException ex) { return false; }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSlide(Long courseId, Long lessonId) {
        verifyOwner(courseId);
        if (lessonId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "lessonId 不能为空");
        }
        LambdaQueryWrapper<CourseSlide> wrapper = new LambdaQueryWrapper<CourseSlide>()
                .eq(CourseSlide::getCourseId, courseId)
                .eq(CourseSlide::getSectionId, lessonId);
        List<CourseSlide> slides = courseSlideMapper.selectList(wrapper);
        if (slides.isEmpty()) {
            throw new BusinessException(ErrorCode.SLIDE_NOT_FOUND, "未找到该课时的课件");
        }
        for (CourseSlide s : slides) {
            slidePageMapper.delete(new LambdaQueryWrapper<SlidePage>().eq(SlidePage::getSlideId, s.getId()));
            courseSlideMapper.deleteById(s.getId());
            registerSlideCleanup(courseId, s.getId());
        }
        cleanupAudioFiles(courseId, lessonId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePage(Long courseId, Integer pageNumber, Long sectionId) {
        verifyOwner(courseId);
        LambdaQueryWrapper<SlidePage> qw = new LambdaQueryWrapper<SlidePage>()
                .eq(SlidePage::getCourseId, courseId)
                .eq(SlidePage::getPageNumber, pageNumber);
        if (sectionId != null) {
            qw.eq(SlidePage::getSectionId, sectionId);
        }
        List<SlidePage> list = slidePageMapper.selectList(qw);
        if (list.isEmpty()) throw new BusinessException(ErrorCode.SLIDE_PAGE_NOT_FOUND);
        SlidePage p = list.get(0);
        if (p.getFileUuid() != null) {
            try {
                Path courseDir = Paths.get(storagePath, String.valueOf(courseId));
                Path slideDir = courseDir.resolve(String.valueOf(p.getSlideId()));
                Files.deleteIfExists(slideDir.resolve("images").resolve(p.getFileUuid() + ".png"));
                Files.deleteIfExists(slideDir.resolve("thumbnails").resolve(p.getFileUuid() + "_thumbnail.png"));
            } catch (Exception ignored) {}
        }
        if (p.getSectionId() != null) {
            cleanupPageAudioFile(courseId, p.getSectionId(), p.getPageNumber());
        }
        slidePageMapper.deleteById(p.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SlidePageVO updatePage(Long courseId, Integer pageNumber, Map<String, Object> body) {
        verifyOwner(courseId);
        LambdaQueryWrapper<SlidePage> qw = new LambdaQueryWrapper<SlidePage>()
                .eq(SlidePage::getCourseId, courseId).eq(SlidePage::getPageNumber, pageNumber);
        Object lIdObj = body != null ? body.get("_lessonId") : null;
        if (lIdObj instanceof Number) {
            qw.eq(SlidePage::getSectionId, ((Number) lIdObj).longValue());
        } else {
            Object chIdObj = body != null ? body.get("_chapterId") : null;
            if (chIdObj instanceof Number) {
                qw.eq(SlidePage::getChapterId, ((Number) chIdObj).longValue());
            }
        }
        SlidePage p = slidePageMapper.selectOne(qw);
        if (p == null) throw new BusinessException(ErrorCode.SLIDE_PAGE_NOT_FOUND);
        if (body.containsKey("narrationScript") && body.get("narrationScript") instanceof String) {
            if ("AUDIO_READY".equals(p.getNarrationStatus())) {
                p.setNarrationAudioUrl(null);
                p.setAudioDuration(null);
                cleanupPageAudioFile(courseId, p.getSectionId(), p.getPageNumber());
            }
            p.setNarrationScript((String) body.get("narrationScript"));
            p.setNarrationStatus("TEACHER_EDITED");
        }
        p.setUpdatedAt(LocalDateTime.now());
        int affected = slidePageMapper.updateById(p);
        if (affected == 0) {
            throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION, "页面已被其他人修改，请刷新后重试");
        }
        return toPageVO(p);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reorderPages(Long courseId, List<Map<String, Integer>> order) {
        verifyOwner(courseId);
        int TEMP_OFFSET = 50000;
        for (Map<String, Integer> item : order) {
            Integer old = item.get("pageNumber"); Integer nw = item.get("newPageNumber");
            if (old == null || nw == null || old.equals(nw)) continue;
            List<SlidePage> list = slidePageMapper.selectList(new LambdaQueryWrapper<SlidePage>()
                    .eq(SlidePage::getCourseId, courseId).eq(SlidePage::getPageNumber, old));
            if (!list.isEmpty()) { SlidePage p = list.get(0); p.setPageNumber(TEMP_OFFSET + old); slidePageMapper.updateById(p); }
        }
        for (Map<String, Integer> item : order) {
            Integer old = item.get("pageNumber"); Integer nw = item.get("newPageNumber");
            if (old == null || nw == null || old.equals(nw)) continue;
            List<SlidePage> list = slidePageMapper.selectList(new LambdaQueryWrapper<SlidePage>()
                    .eq(SlidePage::getCourseId, courseId).eq(SlidePage::getPageNumber, TEMP_OFFSET + old));
            if (!list.isEmpty()) { SlidePage p = list.get(0); p.setPageNumber(nw); slidePageMapper.updateById(p); }
        }
    }

    private void verifyOwner(Long courseId) {
        Course c = courseRepository.selectById(courseId);
        if (c == null) throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        if (!SecurityUtil.isOwnerOrAdmin(c.getTeacherId())) { throw new BusinessException(ErrorCode.NO_PERMISSION); }
    }

    @Override
    public byte[] getOriginalFile(Long courseId) {
        verifyOwner(courseId);
        try { return Files.readAllBytes(Paths.get(storagePath, String.valueOf(courseId), "original.pptx")); }
        catch (IOException e) { throw new BusinessException(ErrorCode.SLIDE_NOT_FOUND, "课件原始文件不存在"); }
    }

    private String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException e) { log.error("SHA-256 not available", e); return ""; }
    }

    private void registerSlideCleanup(Long courseId, Long slideId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cleanupSlideFiles(courseId, slideId);
                }
            });
        } else {
            cleanupSlideFiles(courseId, slideId);
        }
    }

    private void cleanupSlideFiles(Long courseId) {
        Path dir = Paths.get(storagePath, String.valueOf(courseId));
        if (Files.exists(dir)) {
            try {
                Files.walk(dir).sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                });
            } catch (IOException e) { log.warn("[Slide] 清理文件失败 courseId={}", courseId, e); }
        }
    }

    @Override
    public void cleanupSlideFiles(Long courseId, Long slideId) {
        if (slideId != null) {
            Path slideDir = Paths.get(storagePath, String.valueOf(courseId), String.valueOf(slideId));
            if (Files.exists(slideDir)) {
                try {
                    Files.walk(slideDir).sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                    });
                } catch (IOException e) { log.warn("[Slide] 清理幻灯片文件失败 courseId={}, slideId={}", courseId, slideId, e); }
            }
        } else {
            cleanupSlideFiles(courseId);
        }
    }

    private void cleanupAudioFiles(Long courseId, Long sectionId) {
        try {
            Path audioDir = Paths.get(storagePath, String.valueOf(courseId), "audio");
            if (!Files.exists(audioDir)) return;
            Files.deleteIfExists(audioDir.resolve("section_" + sectionId + "_merged.mp3"));
            try (var stream = Files.list(audioDir)) {
                stream.filter(p -> p.getFileName().toString().startsWith("section_" + sectionId + "_page_"))
                        .forEach(p -> {
                            try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                        });
            }
            log.info("[Slide] 已清理音频文件 courseId={}, sectionId={}", courseId, sectionId);
        } catch (IOException e) {
            log.warn("[Slide] 清理音频文件失败 courseId={}, sectionId={}: {}", courseId, sectionId, e.getMessage());
        }
    }

    private void cleanupPageAudioFile(Long courseId, Long sectionId, Integer pageNumber) {
        try {
            Path audioFile = Paths.get(storagePath, String.valueOf(courseId), "audio",
                    "section_" + sectionId + "_page_" + pageNumber + ".mp3");
            Files.deleteIfExists(audioFile);
            log.info("[Slide] 已清理页面音频文件 courseId={}, sectionId={}, page={}", courseId, sectionId, pageNumber);
        } catch (IOException e) {
            log.warn("[Slide] 清理页面音频文件失败 courseId={}, sectionId={}, page={}: {}", courseId, sectionId, pageNumber, e.getMessage());
        }
    }

    private void backupSlideFiles(Long courseId) {
        Path dir = Paths.get(storagePath, String.valueOf(courseId));
        if (!Files.exists(dir)) return;
        try {
            String ts = LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            Path bk = dir.resolve("backup").resolve(ts);
            Files.createDirectories(bk);
            Files.walk(dir).filter(Files::isRegularFile).filter(p -> !p.startsWith(dir.resolve("backup"))).forEach(p -> {
                try {
                    Path t = bk.resolve(dir.relativize(p)); Files.createDirectories(t.getParent());
                    Files.copy(p, t, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) { log.warn("[Slide] 备份失败: {}", p, e); }
            });
        } catch (IOException e) { log.warn("[Slide] 创建备份目录失败 courseId={}", courseId, e); }
    }
}
