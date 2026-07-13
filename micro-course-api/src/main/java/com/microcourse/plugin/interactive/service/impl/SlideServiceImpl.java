package com.microcourse.plugin.interactive.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.entity.Course;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
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
    public SlideUploadResponse upload(Long courseId, String originalFilename, byte[] fileBytes, Long chapterId) {
        Course course = courseRepository.selectById(courseId);
        if (course == null) { throw new BusinessException(ErrorCode.COURSE_NOT_FOUND); }
        if (!SecurityUtil.isOwnerOrAdmin(course.getTeacherId())) { throw new BusinessException(ErrorCode.NO_PERMISSION); }
        if (!originalFilename.toLowerCase().endsWith(".pptx")) { throw new BusinessException(ErrorCode.PPT_FORMAT_INVALID); }
        if (!isZipHeader(fileBytes)) { throw new BusinessException(ErrorCode.PPT_FORMAT_INVALID); }
        if (fileBytes.length > maxHtmlSize * 10) { throw new BusinessException(ErrorCode.PPT_FORMAT_INVALID); }
        if (!validateZipBomb(fileBytes)) { throw new BusinessException(ErrorCode.PPT_PARSE_FAILED); }

        String fileHash = sha256(fileBytes);
        LambdaQueryWrapper<CourseSlide> qw = new LambdaQueryWrapper<>();
        qw.eq(CourseSlide::getCourseId, courseId);
        CourseSlide old = courseSlideMapper.selectOne(qw);
        if (old != null) {
            backupSlideFiles(courseId);
            cleanupSlideFiles(courseId);
            courseSlideMapper.deleteById(old.getId());
            slidePageMapper.delete(new LambdaQueryWrapper<SlidePage>().eq(SlidePage::getSlideId, old.getId()));
        }
        CourseSlide slide = new CourseSlide();
        slide.setCourseId(courseId); slide.setFileName(originalFilename); slide.setFileUrl("pending");
        slide.setStatus(0); slide.setFileHash(fileHash);
        if (chapterId != null) { slide.setChapterId(chapterId); }
        slide.setCreatedAt(LocalDateTime.now()); slide.setUpdatedAt(LocalDateTime.now());
        courseSlideMapper.insert(slide);

        Path courseDir = Paths.get(storagePath, String.valueOf(courseId));
        try {
            Files.createDirectories(courseDir);
            Path pptxPath = courseDir.resolve("original.pptx");
            Files.write(pptxPath, fileBytes);
            slide.setFileUrl(pptxPath.toString());
            courseSlideMapper.updateById(slide);
        } catch (IOException e) {
            slide.setStatus(3); slide.setErrorMessage("文件保存失败");
            log.error("[SlideUpload] IO异常 courseId={}", courseId, e);
            slide.setUpdatedAt(LocalDateTime.now());
            courseSlideMapper.updateById(slide);
            throw new BusinessException(ErrorCode.PPT_PARSE_FAILED);
        }
        Long sid = slide.getId();
        Long fc = chapterId;
        byte[] fb = fileBytes;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() { slideRenderService.renderAsync(sid, fc, fb); }
        });
        SlideUploadResponse r = new SlideUploadResponse();
        r.setSlideId(sid); r.setTotalPages(0); r.setStatus(0); r.setMessage("上传成功，正在后台渲染...");
        return r;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SlideUploadResponse uploadHtmlFile(Long courseId, MultipartFile file, Long chapterId) {
        Course course = courseRepository.selectById(courseId);
        if (course == null) { throw new BusinessException(ErrorCode.COURSE_NOT_FOUND); }
        if (!SecurityUtil.isOwnerOrAdmin(course.getTeacherId())) { throw new BusinessException(ErrorCode.NO_PERMISSION); }
        if (file.getSize() > maxHtmlSize) { throw new BusinessException(ErrorCode.HTML_TOO_LARGE); }
        String rawHtml;
        try {
            rawHtml = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.HTML_INVALID, "HTML 文件读取失败");
        }
        String safeHtml = HtmlSanitizer.sanitizeForCourseware(rawHtml);
        if (safeHtml.isEmpty() && !rawHtml.isEmpty()) { throw new BusinessException(ErrorCode.HTML_SANITIZE_REMOVED_ALL); }
        CourseSlide slide = new CourseSlide();
        slide.setCourseId(courseId);
        slide.setFileName(file.getOriginalFilename() != null ? file.getOriginalFilename() : "slide.html");
        slide.setFileUrl("html:inline");  // file_url NOT NULL, HTML 内容在 slide_pages.html_content 中
        slide.setTotalPages(1);
        slide.setStatus(2);
        try { slide.setFileHash(sha256(file.getBytes())); }
        catch (IOException e) { slide.setFileHash(""); }
        if (chapterId != null) { slide.setChapterId(chapterId); }
        slide.setCreatedAt(LocalDateTime.now());
        slide.setUpdatedAt(LocalDateTime.now());
        courseSlideMapper.insert(slide);
        SlidePage page = new SlidePage();
        page.setSlideId(slide.getId());
        page.setCourseId(courseId);
        page.setChapterId(chapterId);
        page.setPageNumber(1);
        page.setContentType("HTML_DIRECT");
        page.setHtmlContent(safeHtml);
        // slide_pages.image_url NOT NULL — HTML 没有渲染图片，设置空占位
        page.setImageUrl("html:no-image");
        page.setNarrationStatus("PENDING");
        page.setCreatedAt(LocalDateTime.now());
        page.setUpdatedAt(LocalDateTime.now());
        slidePageMapper.insert(page);
        log.info("[SlideUpload-HtmlFile] courseId={}, slideId={}, size={}", courseId, slide.getId(), safeHtml.length());
        SlideUploadResponse resp = new SlideUploadResponse();
        resp.setSlideId(slide.getId());
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
                slidePageMapper.updateById(firstPage);
                log.info("[PPTtoHTML] convert success slideId={}, firstPageId={}, htmlSize={}",
                        slideId, firstPage.getId(), safeHtml.length());
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
            char slideLetter = 'A';
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
        return courseSlideMapper.selectList(
                new LambdaQueryWrapper<CourseSlide>().eq(CourseSlide::getCourseId, courseId))
                .stream().map(this::toVO).collect(Collectors.toList());
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
    public List<SlidePageVO> getPages(Long courseId, Long lessonId) {
        LambdaQueryWrapper<SlidePage> qw = new LambdaQueryWrapper<>();
        qw.eq(SlidePage::getCourseId, courseId);
        if (lessonId != null) { qw.eq(SlidePage::getLessonId, lessonId); }
        qw.orderByAsc(SlidePage::getSlideId).orderByAsc(SlidePage::getPageNumber);
        return slidePageMapper.selectList(qw).stream().map(this::toPageVO).collect(Collectors.toList());
    }

    @Override
    public SlidePageVO getPage(Long courseId, Integer pageNumber) {
        LambdaQueryWrapper<SlidePage> qw = new LambdaQueryWrapper<>();
        qw.eq(SlidePage::getCourseId, courseId).eq(SlidePage::getPageNumber, pageNumber);
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
        SlideVO vo = new SlideVO();
        vo.setId(s.getId()); vo.setCourseId(s.getCourseId()); vo.setFileName(s.getFileName());
        vo.setTotalPages(s.getTotalPages()); vo.setStatus(s.getStatus());
        vo.setStatusText(SlideVO.statusText(s.getStatus()));
        vo.setErrorMessage(s.getErrorMessage());
        vo.setCreatedAt(s.getCreatedAt()); vo.setUpdatedAt(s.getUpdatedAt());
        vo.setChapterId(s.getChapterId());
        vo.setLessonId(s.getSectionId());
        if (s.getChapterId() != null) {
            CourseChapter chapter = courseChapterRepository.selectById(s.getChapterId());
            if (chapter != null) vo.setChapterTitle(chapter.getTitle());
        }
        if (s.getSectionId() != null) {
            CourseSection sec = sectionRepo.selectById(s.getSectionId());
            if (sec != null) vo.setLessonTitle(sec.getTitle());
        }
        return vo;
    }

    private SlidePageVO toPageVO(SlidePage p) {
        SlidePageVO vo = new SlidePageVO();
        vo.setId(p.getId()); vo.setSlideId(p.getSlideId()); vo.setChapterId(p.getChapterId());
        vo.setLessonId(p.getSectionId());
        vo.setCourseId(p.getCourseId()); vo.setPageNumber(p.getPageNumber());
        vo.setFileUuid(p.getFileUuid()); vo.setContentType(p.getContentType());
        vo.setHtmlContent(p.getHtmlContent());
        vo.setImageUrl(p.getImageUrl()); vo.setThumbnailUrl(p.getThumbnailUrl());
        vo.setImageWidth(p.getImageWidth()); vo.setImageHeight(p.getImageHeight());
        vo.setExtractedText(p.getExtractedText());
        vo.setHasAnimation(p.getHasAnimation()); vo.setHasEmbeddedMedia(p.getHasEmbeddedMedia());
        vo.setNarrationScript(p.getNarrationScript()); vo.setNarrationAudioUrl(p.getNarrationAudioUrl());
        vo.setAudioDuration(p.getAudioDuration()); vo.setNarrationStatus(p.getNarrationStatus());
        vo.setNarrationStatusText(SlidePageVO.narrationStatusText(p.getNarrationStatus()));
        vo.setCreatedAt(p.getCreatedAt()); vo.setUpdatedAt(p.getUpdatedAt());
        return vo;
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
        LambdaQueryWrapper<CourseSlide> wrapper = new LambdaQueryWrapper<CourseSlide>()
                .eq(CourseSlide::getCourseId, courseId);
        if (lessonId != null) {
            wrapper.eq(CourseSlide::getSectionId, lessonId);
        }
        List<CourseSlide> slides = courseSlideMapper.selectList(wrapper);
        if (slides.isEmpty()) throw new BusinessException(ErrorCode.SLIDE_NOT_FOUND);
        for (CourseSlide s : slides) {
            slidePageMapper.delete(new LambdaQueryWrapper<SlidePage>().eq(SlidePage::getSlideId, s.getId()));
            courseSlideMapper.deleteById(s.getId());
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() { cleanupSlideFiles(courseId); }
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePage(Long courseId, Integer pageNumber) {
        List<SlidePage> list = slidePageMapper.selectList(new LambdaQueryWrapper<SlidePage>()
                .eq(SlidePage::getCourseId, courseId).eq(SlidePage::getPageNumber, pageNumber));
        if (list.isEmpty()) throw new BusinessException(ErrorCode.SLIDE_PAGE_NOT_FOUND);
        SlidePage p = list.get(0);
        verifyOwner(courseId);
        if (p.getFileUuid() != null) {
            try {
                Path courseDir = Paths.get(storagePath, String.valueOf(courseId));
                Path slideDir = courseDir.resolve(String.valueOf(p.getSlideId()));
                Files.deleteIfExists(slideDir.resolve("images").resolve(p.getFileUuid() + ".png"));
                Files.deleteIfExists(slideDir.resolve("thumbnails").resolve(p.getFileUuid() + "_thumbnail.png"));
            } catch (Exception ignored) {}
        }
        slidePageMapper.deleteById(p.getId());
    }

    @Override
    public SlidePageVO updatePage(Long courseId, Integer pageNumber, Map<String, Object> body) {
        LambdaQueryWrapper<SlidePage> qw = new LambdaQueryWrapper<SlidePage>()
                .eq(SlidePage::getCourseId, courseId).eq(SlidePage::getPageNumber, pageNumber);
        Object lIdObj = body != null ? body.get("_lessonId") : null;
        if (lIdObj instanceof Number) {
            qw.eq(SlidePage::getLessonId, ((Number) lIdObj).longValue());
        } else {
            Object chIdObj = body != null ? body.get("_chapterId") : null;
            if (chIdObj instanceof Number) {
                qw.eq(SlidePage::getChapterId, ((Number) chIdObj).longValue());
            }
        }
        SlidePage p = slidePageMapper.selectOne(qw);
        if (p == null) throw new BusinessException(ErrorCode.SLIDE_PAGE_NOT_FOUND);
        verifyOwner(courseId);
        if (body.containsKey("narrationScript") && body.get("narrationScript") instanceof String) {
            if ("AUDIO_READY".equals(p.getNarrationStatus())) { p.setNarrationAudioUrl(null); p.setAudioDuration(null); }
            p.setNarrationScript((String) body.get("narrationScript"));
            p.setNarrationStatus("TEACHER_EDITED");
        }
        p.setUpdatedAt(LocalDateTime.now());
        slidePageMapper.updateById(p);
        return toPageVO(p);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reorderPages(Long courseId, List<Map<String, Integer>> order) {
        verifyOwner(courseId);
        for (Map<String, Integer> item : order) {
            Integer old = item.get("pageNumber"); Integer nw = item.get("newPageNumber");
            if (old == null || nw == null || old.equals(nw)) continue;
            List<SlidePage> list = slidePageMapper.selectList(new LambdaQueryWrapper<SlidePage>()
                    .eq(SlidePage::getCourseId, courseId).eq(SlidePage::getPageNumber, old));
            if (!list.isEmpty()) { SlidePage p = list.get(0); p.setPageNumber(-old); slidePageMapper.updateById(p); }
        }
        for (Map<String, Integer> item : order) {
            Integer old = item.get("pageNumber"); Integer nw = item.get("newPageNumber");
            if (old == null || nw == null || old.equals(nw)) continue;
            List<SlidePage> list = slidePageMapper.selectList(new LambdaQueryWrapper<SlidePage>()
                    .eq(SlidePage::getCourseId, courseId).eq(SlidePage::getPageNumber, -old));
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
