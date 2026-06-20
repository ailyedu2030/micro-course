package com.microcourse.plugin.interactive.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.plugin.interactive.entity.CourseSlide;
import com.microcourse.plugin.interactive.entity.SlidePage;
import com.microcourse.plugin.interactive.mapper.CourseSlideMapper;
import com.microcourse.plugin.interactive.mapper.SlidePageMapper;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFSlideShow;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class SlideRenderService {

    private static final Logger log = LoggerFactory.getLogger(SlideRenderService.class);

    private final CourseSlideMapper courseSlideMapper;
    private final SlidePageMapper slidePageMapper;

    @Value("${plugin.interactive.slides.storage-path:/data/slides}")
    private String storagePath;

    @Value("${plugin.interactive.slides.page-image-width:1920}")
    private int pageImageWidth;

    @Value("${plugin.interactive.slides.thumbnail-width:320}")
    private int thumbnailWidth;

    public SlideRenderService(CourseSlideMapper courseSlideMapper, SlidePageMapper slidePageMapper) {
        this.courseSlideMapper = courseSlideMapper;
        this.slidePageMapper = slidePageMapper;
    }

    @Async("slideRenderExecutor")
    @Transactional(rollbackFor = Exception.class)
    public void renderAsync(Long slideId, byte[] pptxBytes) {
        CourseSlide slide = courseSlideMapper.selectById(slideId);
        if (slide == null) return;

        int totalPages = 0;
        int scaledHeight = 0;
        List<SlidePage> batchPages = new ArrayList<>();
        Path imagesDir = null;
        Path thumbnailsDir = null;

        try {
            slide.setStatus(1);
            slide.setUpdatedAt(LocalDateTime.now());
            courseSlideMapper.updateById(slide);

            imagesDir = Paths.get(storagePath, String.valueOf(slide.getCourseId()), "images");
            thumbnailsDir = Paths.get(storagePath, String.valueOf(slide.getCourseId()), "thumbnails");
            Files.createDirectories(imagesDir);
            Files.createDirectories(thumbnailsDir);

            try (org.apache.poi.xslf.usermodel.XMLSlideShow ppt =
                         new org.apache.poi.xslf.usermodel.XMLSlideShow(new ByteArrayInputStream(pptxBytes))) {
                Dimension pageSize = ppt.getPageSize();
                totalPages = ppt.getSlides().size();

                double scale = (double) pageImageWidth / pageSize.width;
                scaledHeight = (int) (pageSize.height * scale);

                for (int i = 0; i < totalPages; i++) {
                    XSLFSlide xslfSlide = ppt.getSlides().get(i);
                    int pageNumber = i + 1;
                    BufferedImage image = null;
                    BufferedImage thumb = null;
                    try {
                        image = new BufferedImage(pageImageWidth, scaledHeight, BufferedImage.TYPE_INT_ARGB);
                        Graphics2D g = image.createGraphics();
                        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                        g.setPaint(Color.WHITE);
                        g.fillRect(0, 0, pageImageWidth, scaledHeight);
                        g.scale(scale, scale);
                        xslfSlide.draw(g);
                        g.dispose();

                        Path imagePath = imagesDir.resolve("page_" + pageNumber + ".png");
                        ImageIO.write(image, "PNG", imagePath.toFile());

                        thumb = resizeImage(image, thumbnailWidth);
                        Path thumbPath = thumbnailsDir.resolve("page_" + pageNumber + ".png");
                        ImageIO.write(thumb, "PNG", thumbPath.toFile());
                    } finally {
                        if (image != null) image.flush();
                        if (thumb != null) thumb.flush();
                    }

                    String extractedText = extractSlideText(xslfSlide);
                    SlidePage sp = new SlidePage();
                    sp.setSlideId(slideId);
                    sp.setCourseId(slide.getCourseId());
                    sp.setPageNumber(pageNumber);
                    sp.setImageUrl("/api/courses/" + slide.getCourseId() + "/slides/pages/" + pageNumber + "/image");
                    sp.setThumbnailUrl("/api/courses/" + slide.getCourseId() + "/slides/pages/" + pageNumber + "/thumbnail");
                    sp.setImageWidth(pageImageWidth);
                    sp.setImageHeight(scaledHeight);
                    sp.setExtractedText(extractedText);
                    sp.setHasAnimation(false);
                    sp.setHasEmbeddedMedia(false);
                    sp.setNarrationStatus("PENDING");
                    sp.setCreatedAt(LocalDateTime.now());
                    sp.setUpdatedAt(LocalDateTime.now());
                    batchPages.add(sp);
                }
            }

            // BATCH INSERT: 批量写入而非逐条插入
            if (!batchPages.isEmpty()) {
                slidePageMapper.insertBatch(batchPages);
            }

            slide.setTotalPages(totalPages);
            slide.setStatus(2);
            slide.setUpdatedAt(LocalDateTime.now());
            courseSlideMapper.updateById(slide);
            log.info("Slide render complete: slideId={}, pages={}", slideId, totalPages);

        } catch (Exception e) {
            log.error("Slide render failed: slideId={}", slideId, e);
            slide.setStatus(3);
            slide.setErrorMessage("课件渲染失败，请检查文件内容或联系管理员");
            slide.setUpdatedAt(LocalDateTime.now());
            courseSlideMapper.updateById(slide);
        }
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
}
