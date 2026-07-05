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
import com.microcourse.repository.CourseRepository;
import com.microcourse.util.SecurityUtil;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
public class SlideServiceImpl implements SlideService {

    private static final Logger log = LoggerFactory.getLogger(SlideServiceImpl.class);

    private static final String PPTX_MIME = "application/vnd.openxmlformats-officedocument.presentationml.presentation";
    private static final byte[] ZIP_MAGIC = new byte[]{0x50, 0x4B, 0x03, 0x04};
    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;
    private static final int MAX_ZIP_ENTRIES = 1000;
    private static final long MAX_UNCOMPRESSED_SIZE = 500L * 1024 * 1024;

    private final CourseSlideMapper courseSlideMapper;
    private final SlidePageMapper slidePageMapper;
    private final CourseRepository courseRepository;
    private final SlideRenderService slideRenderService;

    @Value("${plugin.interactive.slides.storage-path:/data/slides}")
    private String storagePath;

    @Value("${plugin.interactive.slides.page-image-width:1920}")
    private int pageImageWidth;

    @Value("${plugin.interactive.slides.thumbnail-width:320}")
    private int thumbnailWidth;

    public SlideServiceImpl(CourseSlideMapper courseSlideMapper,
                            SlidePageMapper slidePageMapper,
                            CourseRepository courseRepository,
                            SlideRenderService slideRenderService) {
        this.courseSlideMapper = courseSlideMapper;
        this.slidePageMapper = slidePageMapper;
        this.courseRepository = courseRepository;
        this.slideRenderService = slideRenderService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SlideUploadResponse upload(Long courseId, String originalFilename, byte[] fileBytes, Long chapterId) {
        Course course = courseRepository.selectById(courseId);
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        if (!SecurityUtil.isOwnerOrAdmin(course.getTeacherId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
        if (!originalFilename.toLowerCase().endsWith(".pptx")) {
            throw new BusinessException(ErrorCode.PPT_FORMAT_INVALID);
        }
        if (!isZipHeader(fileBytes)) {
            throw new BusinessException(ErrorCode.PPT_FORMAT_INVALID);
        }
        if (fileBytes.length > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.PPT_FORMAT_INVALID);
        }
        if (!validateZipBomb(fileBytes)) {
            throw new BusinessException(ErrorCode.PPT_PARSE_FAILED);
        }

        String fileHash = sha256(fileBytes);

        LambdaQueryWrapper<CourseSlide> existing = new LambdaQueryWrapper<>();
        existing.eq(CourseSlide::getCourseId, courseId);
        CourseSlide oldSlide = courseSlideMapper.selectOne(existing);
        if (oldSlide != null) {
            // P2-6: 覆盖前备份旧版本至 backup/{timestamp}/
            backupSlideFiles(courseId);
            cleanupSlideFiles(courseId);
            courseSlideMapper.deleteById(oldSlide.getId());
            LambdaQueryWrapper<SlidePage> oldPages = new LambdaQueryWrapper<>();
            oldPages.eq(SlidePage::getSlideId, oldSlide.getId());
            slidePageMapper.delete(oldPages);
        }

        CourseSlide slide = new CourseSlide();
        slide.setCourseId(courseId);
        slide.setFileName(originalFilename);
        slide.setFileUrl("pending");
        slide.setStatus(0);
        slide.setFileHash(fileHash);
        if (chapterId != null) {
            slide.setChapterId(chapterId);
        }
        slide.setCreatedAt(LocalDateTime.now());
        slide.setUpdatedAt(LocalDateTime.now());
        courseSlideMapper.insert(slide);

        Path courseDir = Paths.get(storagePath, String.valueOf(courseId));
        try {
            Files.createDirectories(courseDir);
            Path pptxPath = courseDir.resolve("original.pptx");
            slide.setFileUrl(pptxPath.toString());
            Files.write(pptxPath, fileBytes);
            slide.setFileUrl(pptxPath.toString());
            courseSlideMapper.updateById(slide);
        } catch (IOException e) {
            slide.setStatus(3);
            slide.setErrorMessage("文件保存失败");
            log.error("[SlideUpload] 文件保存IO异常 courseId={}", courseId, e);
            slide.setUpdatedAt(LocalDateTime.now());
            courseSlideMapper.updateById(slide);
            throw new BusinessException(ErrorCode.PPT_PARSE_FAILED);
        }

        final Long slideId = slide.getId();
        final Long finalChapterId = chapterId;
        final byte[] bytesForRender = fileBytes;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                slideRenderService.renderAsync(slideId, finalChapterId, bytesForRender);
            }
        });

        SlideUploadResponse resp = new SlideUploadResponse();
        resp.setSlideId(slideId);
        resp.setTotalPages(0);
        resp.setStatus(0);
        resp.setMessage("上传成功，正在后台渲染...");
        return resp;
    }

    @Override
    public SlideVO getByCourseId(Long courseId) {
        LambdaQueryWrapper<CourseSlide> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CourseSlide::getCourseId, courseId);
        CourseSlide slide = courseSlideMapper.selectOne(wrapper);
        if (slide == null) {
            return null;
        }
        return toVO(slide);
    }

    @Override
    public List<SlidePageVO> getPages(Long courseId, Long chapterId) {
        SlideVO slideVO = getByCourseId(courseId);
        if (slideVO == null) {
            return java.util.Collections.emptyList();
        }
        LambdaQueryWrapper<SlidePage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SlidePage::getSlideId, slideVO.getId())
                .orderByAsc(SlidePage::getPageNumber);
        if (chapterId != null) {
            wrapper.eq(SlidePage::getChapterId, chapterId);
        }
        return slidePageMapper.selectList(wrapper).stream()
                .map(this::toPageVO)
                .collect(Collectors.toList());
    }

    @Override
    public SlidePageVO getPage(Long courseId, Integer pageNumber) {
        LambdaQueryWrapper<SlidePage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SlidePage::getCourseId, courseId)
                .eq(SlidePage::getPageNumber, pageNumber);
        SlidePage page = slidePageMapper.selectOne(wrapper);
        if (page == null) {
            throw new BusinessException(ErrorCode.SLIDE_PAGE_NOT_FOUND);
        }
        return toPageVO(page);
    }

    @Override
    public byte[] getPageImage(Long courseId, Integer pageNumber) {
        SlidePageVO pageVO;
        try {
            pageVO = getPage(courseId, pageNumber);
        } catch (BusinessException e) {
            log.warn("[Slide] 获取页面信息失败，返回占位图 courseId={} pageNumber={}", courseId, pageNumber);
            return generateFallbackImage(pageImageWidth, "第" + pageNumber + "页");
        }
        if (pageVO.getSlideId() == null) {
            log.warn("[Slide] slideId 为空 courseId={} pageNumber={}", courseId, pageNumber);
            return generateFallbackImage(pageImageWidth, "第" + pageNumber + "页");
        }
        String imageFileName = pageVO.getFileUuid() != null
                ? pageVO.getFileUuid() + ".png"
                : "page_" + pageNumber + ".png";
        Path imagePath = Paths.get(storagePath, String.valueOf(courseId),
                String.valueOf(pageVO.getSlideId()), "images", imageFileName);
        byte[] result = readImageOrFallback(imagePath, pageImageWidth, "第" + pageNumber + "页");
        if (result.length == 0) {
            result = generateFallbackImage(pageImageWidth, "第" + pageNumber + "页");
        }
        return result;
    }

    @Override
    public byte[] getPageThumbnail(Long courseId, Integer pageNumber) {
        SlidePageVO pageVO;
        try {
            pageVO = getPage(courseId, pageNumber);
        } catch (BusinessException e) {
            log.warn("[Slide] 获取页面信息失败，返回占位图 courseId={} pageNumber={}", courseId, pageNumber);
            return generateFallbackImage(thumbnailWidth, "第" + pageNumber + "页");
        }
        if (pageVO.getSlideId() == null) {
            log.warn("[Slide] slideId 为空 courseId={} pageNumber={}", courseId, pageNumber);
            return generateFallbackImage(thumbnailWidth, "第" + pageNumber + "页");
        }
        String thumbFileName = pageVO.getFileUuid() != null
                ? pageVO.getFileUuid() + "_thumbnail.png"
                : "page_" + pageNumber + ".png";
        Path thumbPath = Paths.get(storagePath, String.valueOf(courseId),
                String.valueOf(pageVO.getSlideId()), "thumbnails", thumbFileName);
        byte[] result = readImageOrFallback(thumbPath, thumbnailWidth, "第" + pageNumber + "页");
        if (result.length == 0) {
            result = generateFallbackImage(thumbnailWidth, "第" + pageNumber + "页");
        }
        return result;
    }

    private byte[] readImageOrFallback(Path path, int width, String text) {
        try {
            return Files.readAllBytes(path);
        } catch (NoSuchFileException e) {
            log.warn("[Slide] 课件图片/缩略图文件不存在 path={}", path);
            return generateFallbackImage(width, text);
        } catch (IOException e) {
            log.error("[Slide] 读取课件图片/缩略图失败 path={}", path, e);
            return generateFallbackImage(width, text);
        }
    }

    private byte[] generateFallbackImage(int width, String text) {
        int height = (int) (width * 0.75);
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(245, 245, 245));
            g.fillRect(0, 0, width, height);
            g.setColor(new Color(180, 180, 180));
            g.setFont(new Font("SansSerif", Font.PLAIN, Math.min(width / 10, 18)));
            FontMetrics fm = g.getFontMetrics();
            int x = (width - fm.stringWidth(text)) / 2;
            int y = (height - fm.getHeight()) / 2 + fm.getAscent();
            g.drawString(text, x, y);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "PNG", baos);
            return baos.toByteArray();
        } catch (IOException e) {
            log.error("[Slide] 生成占位图片失败", e);
            return new byte[0];
        } finally {
            g.dispose();
            img.flush();
        }
    }

    private SlideVO toVO(CourseSlide slide) {
        SlideVO vo = new SlideVO();
        vo.setId(slide.getId());
        vo.setCourseId(slide.getCourseId());
        vo.setFileName(slide.getFileName());
        vo.setTotalPages(slide.getTotalPages());
        vo.setStatus(slide.getStatus());
        vo.setStatusText(SlideVO.statusText(slide.getStatus()));
        vo.setErrorMessage(slide.getErrorMessage());
        vo.setCreatedAt(slide.getCreatedAt());
        vo.setUpdatedAt(slide.getUpdatedAt());
        return vo;
    }

    private SlidePageVO toPageVO(SlidePage page) {
        SlidePageVO vo = new SlidePageVO();
        vo.setId(page.getId());
        vo.setSlideId(page.getSlideId());
        vo.setChapterId(page.getChapterId());
        vo.setCourseId(page.getCourseId());
        vo.setPageNumber(page.getPageNumber());
        vo.setFileUuid(page.getFileUuid());
        vo.setImageUrl(page.getImageUrl());
        vo.setThumbnailUrl(page.getThumbnailUrl());
        vo.setImageWidth(page.getImageWidth());
        vo.setImageHeight(page.getImageHeight());
        vo.setExtractedText(page.getExtractedText());
        vo.setHasAnimation(page.getHasAnimation());
        vo.setHasEmbeddedMedia(page.getHasEmbeddedMedia());
        vo.setNarrationScript(page.getNarrationScript());
        vo.setNarrationAudioUrl(page.getNarrationAudioUrl());
        vo.setAudioDuration(page.getAudioDuration());
        vo.setNarrationStatus(page.getNarrationStatus());
        vo.setNarrationStatusText(SlidePageVO.narrationStatusText(page.getNarrationStatus()));
        vo.setCreatedAt(page.getCreatedAt());
        vo.setUpdatedAt(page.getUpdatedAt());
        return vo;
    }

    private BufferedImage resizeImage(BufferedImage original, int targetWidth) {
        double ratio = (double) targetWidth / original.getWidth();
        int targetHeight = (int) (original.getHeight() * ratio);
        BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(original, 0, 0, targetWidth, targetHeight, null);
        g.dispose();
        return resized;
    }

    private boolean isZipHeader(byte[] bytes) {
        if (bytes.length < 4) return false;
        for (int i = 0; i < 4; i++) {
            if (bytes[i] != ZIP_MAGIC[i]) return false;
        }
        return true;
    }

    private boolean validateZipBomb(byte[] bytes) {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            int entryCount = 0;
            long totalSize = 0;
            byte[] buffer = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                entryCount++;
                if (entryCount > MAX_ZIP_ENTRIES) return false;
                int read;
                while ((read = zis.read(buffer)) != -1) {
                    totalSize += read;
                    if (totalSize > MAX_UNCOMPRESSED_SIZE) return false;
                }
            }
            return true;
        } catch (IOException e) {
            log.warn("Zip bomb validation failed for file", e);
            return false;
        }
    }

    private String extractSlideText(XSLFSlide slide) {
        StringBuilder sb = new StringBuilder();
        for (org.apache.poi.xslf.usermodel.XSLFShape shape : slide.getShapes()) {
            if (shape instanceof XSLFTextShape) {
                String text = ((XSLFTextShape) shape).getText();
                if (text != null && !text.isEmpty()) {
                    if (sb.length() > 0) sb.append("\n");
                    sb.append(text);
                }
            }
        }
        return sb.toString().trim();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSlide(Long courseId) {
        // P0-6: OWNER 校验
        verifyCourseOwner(courseId);
        CourseSlide slide = courseSlideMapper.selectOne(
                new LambdaQueryWrapper<CourseSlide>().eq(CourseSlide::getCourseId, courseId));
        if (slide == null) throw new BusinessException(ErrorCode.SLIDE_NOT_FOUND);
        slidePageMapper.delete(new LambdaQueryWrapper<SlidePage>()
                .eq(SlidePage::getSlideId, slide.getId()));
        courseSlideMapper.deleteById(slide.getId());
        // S-01: 事务提交后异步清理磁盘文件（original.pptx + images/ + thumbnails/）
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                cleanupSlideFiles(courseId);
            }
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePage(Long courseId, Integer pageNumber) {
        SlidePage page = slidePageMapper.selectOne(
                new LambdaQueryWrapper<SlidePage>()
                        .eq(SlidePage::getCourseId, courseId)
                        .eq(SlidePage::getPageNumber, pageNumber));
        if (page == null) throw new BusinessException(ErrorCode.SLIDE_PAGE_NOT_FOUND);
        verifyCourseOwner(courseId);
        // S-16: 先删除磁盘文件（按 file_uuid），再删 DB 记录
        if (page.getFileUuid() != null) {
            deletePageDiskFiles(courseId, page.getSlideId(), page.getFileUuid());
        }
        slidePageMapper.deleteById(page.getId());
    }

    /**
     * S-16: 按 file_uuid 删除此页对应的磁盘文件（全尺寸图 + 缩略图）。
     * 静默吞异常——磁盘文件缺失不应阻止 DB 删除。
     */
    private void deletePageDiskFiles(Long courseId, Long slideId, String fileUuid) {
        try {
            Path courseDir = Paths.get(storagePath, String.valueOf(courseId));
            Path slideDir = courseDir.resolve(String.valueOf(slideId));
            try { Files.deleteIfExists(slideDir.resolve("images").resolve(fileUuid + ".png")); } catch (IOException ignored) {}
            try { Files.deleteIfExists(slideDir.resolve("thumbnails").resolve(fileUuid + "_thumbnail.png")); } catch (IOException ignored) {}
        } catch (Exception e) {
            log.warn("[SlideDelete] 清理磁盘文件异常 courseId={} fileUuid={}", courseId, fileUuid, e);
        }
    }

    @Override
    public SlidePageVO updatePage(Long courseId, Integer pageNumber, Map<String, Object> body) {
        SlidePage page = slidePageMapper.selectOne(
                new LambdaQueryWrapper<SlidePage>()
                        .eq(SlidePage::getCourseId, courseId)
                        .eq(SlidePage::getPageNumber, pageNumber));
        if (page == null) throw new BusinessException(ErrorCode.SLIDE_PAGE_NOT_FOUND);
        verifyCourseOwner(courseId);
        // 安全更新：只允许修改 narrationScript 等安全字段
        if (body.containsKey("narrationScript") && body.get("narrationScript") instanceof String) {
            // S-07: 当原状态为 AUDIO_READY（AI 已生成音频）且教师编辑讲述稿时，清除音频残留
            if ("AUDIO_READY".equals(page.getNarrationStatus())) {
                page.setNarrationAudioUrl(null);
                page.setAudioDuration(null);
            }
            page.setNarrationScript((String) body.get("narrationScript"));
            page.setNarrationStatus("TEACHER_EDITED");
        }
        page.setUpdatedAt(LocalDateTime.now());
        slidePageMapper.updateById(page);
        return toPageVO(page);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reorderPages(Long courseId, List<Map<String, Integer>> order) {
        verifyCourseOwner(courseId);
        // Phase 1: move all pages to temp negative numbers to avoid UK conflict
        for (Map<String, Integer> item : order) {
            Integer oldNum = item.get("pageNumber");
            Integer newNum = item.get("newPageNumber");
            if (oldNum == null || newNum == null || oldNum.equals(newNum)) continue;
            SlidePage page = slidePageMapper.selectOne(
                    new LambdaQueryWrapper<SlidePage>()
                            .eq(SlidePage::getCourseId, courseId)
                            .eq(SlidePage::getPageNumber, oldNum));
            if (page != null) {
                page.setPageNumber(-oldNum);
                page.setUpdatedAt(LocalDateTime.now());
                slidePageMapper.updateById(page);
            }
        }
        // Phase 2: set the actual new numbers
        for (Map<String, Integer> item : order) {
            Integer oldNum = item.get("pageNumber");
            Integer newNum = item.get("newPageNumber");
            if (oldNum == null || newNum == null || oldNum.equals(newNum)) continue;
            SlidePage page = slidePageMapper.selectOne(
                    new LambdaQueryWrapper<SlidePage>()
                            .eq(SlidePage::getCourseId, courseId)
                            .eq(SlidePage::getPageNumber, -oldNum));
            if (page != null) {
                page.setPageNumber(newNum);
                page.setUpdatedAt(LocalDateTime.now());
                slidePageMapper.updateById(page);
            }
        }
    }

    private void verifyCourseOwner(Long courseId) {
        Course course = courseRepository.selectById(courseId);
        if (course == null) throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        if (!SecurityUtil.isOwnerOrAdmin(course.getTeacherId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }
    }

    @Override
    public byte[] getOriginalFile(Long courseId) {
        verifyCourseOwner(courseId);
        try {
            Path pptxPath = Paths.get(storagePath, String.valueOf(courseId), "original.pptx");
            return Files.readAllBytes(pptxPath);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SLIDE_NOT_FOUND, "课件原始文件不存在");
        }
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not available on this JVM", e);
            return "";
        }
    }

    private void cleanupSlideFiles(Long courseId) {
        Path courseDir = Paths.get(storagePath, String.valueOf(courseId));
        if (Files.exists(courseDir)) {
            try {
                Files.walk(courseDir)
                        .sorted(java.util.Comparator.reverseOrder())
                        .forEach(p -> {
                            try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                        });
                log.info("[SlideUpload] 已清理旧课件文件 courseId={}", courseId);
            } catch (IOException e) {
                log.warn("[SlideUpload] 清理旧课件文件失败 courseId={}", courseId, e);
            }
        }
    }

    /**
     * P2-6: 覆盖旧课件前简单备份旧版本至 backup/{timestamp}/。
     * 只做文件级复制，不做复杂版本管理。
     */
    private void backupSlideFiles(Long courseId) {
        Path courseDir = Paths.get(storagePath, String.valueOf(courseId));
        if (!Files.exists(courseDir)) return;
        try {
            String timestamp = LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            Path backupDir = courseDir.resolve("backup").resolve(timestamp);
            Files.createDirectories(backupDir);
            try (var stream = Files.walk(courseDir)) {
                stream.filter(Files::isRegularFile)
                        .filter(p -> !p.startsWith(courseDir.resolve("backup")))
                        .forEach(p -> {
                            try {
                                Path relative = courseDir.relativize(p);
                                Path target = backupDir.resolve(relative);
                                Files.createDirectories(target.getParent());
                                Files.copy(p, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            } catch (IOException e) {
                                log.warn("[SlideUpload] 备份文件失败: {}", p, e);
                            }
                        });
            }
            log.info("[SlideUpload] 旧课件已备份至 backup/{} courseId={}", timestamp, courseId);
        } catch (IOException e) {
            log.warn("[SlideUpload] 创建备份目录失败 courseId={}", courseId, e);
        }
    }
}
