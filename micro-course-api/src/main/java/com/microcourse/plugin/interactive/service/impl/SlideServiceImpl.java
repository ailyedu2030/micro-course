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
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
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
    public SlideUploadResponse upload(Long courseId, String originalFilename, byte[] fileBytes) {
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
        final byte[] bytesForRender = fileBytes;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                slideRenderService.renderAsync(slideId, bytesForRender);
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
    public List<SlidePageVO> getPages(Long courseId) {
        SlideVO slideVO = getByCourseId(courseId);
        if (slideVO == null) {
            return java.util.Collections.emptyList();
        }
        LambdaQueryWrapper<SlidePage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SlidePage::getSlideId, slideVO.getId())
                .orderByAsc(SlidePage::getPageNumber);
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
        SlidePageVO pageVO = getPage(courseId, pageNumber);
        try {
            Path imagePath = Paths.get(storagePath, String.valueOf(courseId),
                    String.valueOf(pageVO.getSlideId()), "images",
                    "page_" + pageNumber + ".png");
            return Files.readAllBytes(imagePath);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SLIDE_PAGE_NOT_FOUND);
        }
    }

    @Override
    public byte[] getPageThumbnail(Long courseId, Integer pageNumber) {
        SlidePageVO pageVO = getPage(courseId, pageNumber);
        try {
            Path thumbPath = Paths.get(storagePath, String.valueOf(courseId),
                    String.valueOf(pageVO.getSlideId()), "thumbnails",
                    "page_" + pageNumber + ".png");
            return Files.readAllBytes(thumbPath);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SLIDE_PAGE_NOT_FOUND);
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
        vo.setCourseId(page.getCourseId());
        vo.setPageNumber(page.getPageNumber());
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
        slidePageMapper.deleteById(page.getId());
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
        for (Map<String, Integer> item : order) {
            Integer oldNum = item.get("pageNumber");
            Integer newNum = item.get("newPageNumber");
            if (oldNum == null || newNum == null || oldNum.equals(newNum)) continue;
            SlidePage page = slidePageMapper.selectOne(
                    new LambdaQueryWrapper<SlidePage>()
                            .eq(SlidePage::getCourseId, courseId)
                            .eq(SlidePage::getPageNumber, oldNum));
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
}
