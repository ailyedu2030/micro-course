package com.microcourse.plugin.interactive.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.plugin.interactive.entity.CourseSlide;
import com.microcourse.plugin.interactive.entity.SlidePage;
import com.microcourse.plugin.interactive.mapper.CourseSlideMapper;
import com.microcourse.plugin.interactive.mapper.SlidePageMapper;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import javax.xml.parsers.DocumentBuilderFactory;
import org.xml.sax.InputSource;
import java.io.StringReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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
import java.util.UUID;

@Service
@ConditionalOnProperty(value = "plugin.interactive.enabled", havingValue = "true", matchIfMissing = true)
public class SlideRenderService {

    private static final Logger log = LoggerFactory.getLogger(SlideRenderService.class);

    private final CourseSlideMapper courseSlideMapper;
    private final SlidePageMapper slidePageMapper;
    private final TransactionTemplate transactionTemplate;

    @Value("${plugin.interactive.slides.storage-path:/data/slides}")
    private String storagePath;

    @Value("${plugin.interactive.slides.page-image-width:1920}")
    private int pageImageWidth;

    @Value("${plugin.interactive.slides.thumbnail-width:320}")
    private int thumbnailWidth;

    public SlideRenderService(CourseSlideMapper courseSlideMapper,
                              SlidePageMapper slidePageMapper,
                              TransactionTemplate transactionTemplate) {
        this.courseSlideMapper = courseSlideMapper;
        this.slidePageMapper = slidePageMapper;
        this.transactionTemplate = transactionTemplate;
    }

    @Async("slideRenderExecutor")
    public void renderAsync(Long slideId, Long chapterId, Long sectionId, byte[] pptxBytes) {
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

            imagesDir = Paths.get(storagePath, String.valueOf(slide.getCourseId()),
                    String.valueOf(slideId), "images");
            thumbnailsDir = Paths.get(storagePath, String.valueOf(slide.getCourseId()),
                    String.valueOf(slideId), "thumbnails");
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
                    String fileUuid = UUID.randomUUID().toString();
                    BufferedImage image = null;
                    BufferedImage thumb = null;
                    Graphics2D g = null;
                    try {
                        image = new BufferedImage(pageImageWidth, scaledHeight, BufferedImage.TYPE_INT_ARGB);
                        g = image.createGraphics();
                        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                        g.setPaint(new Color(10, 10, 15));
                        g.fillRect(0, 0, pageImageWidth, scaledHeight);
                        g.scale(scale, scale);
                        xslfSlide.draw(g);
                        g.dispose();
                        g = null;

                        Path imagePath = imagesDir.resolve(fileUuid + ".png");
                        ImageIO.write(image, "PNG", imagePath.toFile());

                        thumb = resizeImage(image, thumbnailWidth);
                        Path thumbPath = thumbnailsDir.resolve(fileUuid + "_thumbnail.png");
                        ImageIO.write(thumb, "PNG", thumbPath.toFile());
                    } finally {
                        if (g != null) g.dispose();
                        if (image != null) image.flush();
                        if (thumb != null) thumb.flush();
                    }

                    String extractedText = extractSlideText(xslfSlide);
                    SlidePage sp = new SlidePage();
                    sp.setSlideId(slideId);
                    sp.setCourseId(slide.getCourseId());
                    sp.setChapterId(chapterId);
                    if (sectionId != null) { sp.setSectionId(sectionId); }
                    sp.setPageNumber(pageNumber);
                    sp.setFileUuid(fileUuid);
                    sp.setImageUrl("/api/courses/" + slide.getCourseId() + "/slides/pages/" + pageNumber + "/image");
                    sp.setThumbnailUrl("/api/courses/" + slide.getCourseId() + "/slides/pages/" + pageNumber + "/thumbnail");
                    sp.setImageWidth(pageImageWidth);
                    sp.setImageHeight(scaledHeight);
                    sp.setExtractedText(extractedText);
                    sp.setHasAnimation(detectAnimation(xslfSlide));
                    sp.setHasEmbeddedMedia(detectEmbeddedMedia(xslfSlide));
                    sp.setNarrationStatus("PENDING");
                    sp.setCreatedAt(LocalDateTime.now());
                    sp.setUpdatedAt(LocalDateTime.now());
                    batchPages.add(sp);
                }
            }

            // BATCH INSERT with short transaction for data integrity
            transactionTemplate.execute(status -> {
                slidePageMapper.insertBatch(batchPages);
                return null;
            });

            slide.setTotalPages(totalPages);
            slide.setStatus(2);
            slide.setUpdatedAt(LocalDateTime.now());
            courseSlideMapper.updateById(slide);
            log.info("Slide render complete: slideId={}, pages={}", slideId, totalPages);

        } catch (Exception e) {
            log.error("Slide render failed: slideId={}", slideId, e);
            CourseSlide failedSlide = courseSlideMapper.selectById(slideId);
            if (failedSlide != null) {
                failedSlide.setStatus(3);
                failedSlide.setErrorMessage("课件渲染失败，请检查文件内容或联系管理员");
                failedSlide.setUpdatedAt(LocalDateTime.now());
                courseSlideMapper.updateById(failedSlide);
            }
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

    private boolean detectAnimation(XSLFSlide slide) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setNamespaceAware(true);
            String xmlText = slide.getXmlObject().xmlText();
            if (xmlText == null || xmlText.isBlank()) return false;
            Document doc = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xmlText)));
            NodeList animNodes = doc.getElementsByTagNameNS("http://schemas.openxmlformats.org/drawingml/2006/main", "anim");
            if (animNodes != null && animNodes.getLength() > 0) return true;
            NodeList animGrpNodes = doc.getElementsByTagNameNS("http://schemas.openxmlformats.org/presentationml/2006/main", "animGrp");
            if (animGrpNodes != null && animGrpNodes.getLength() > 0) return true;
            NodeList setNodes = doc.getElementsByTagNameNS("http://schemas.openxmlformats.org/drawingml/2006/main", "set");
            if (setNodes != null && setNodes.getLength() > 0) return true;
        } catch (Exception e) {
            log.debug("[SlideRender] 检测动画异常 slideNum={}", slide.getSlideNumber(), e);
        }
        return false;
    }

    private boolean detectEmbeddedMedia(XSLFSlide slide) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setNamespaceAware(true);
            String xmlText = slide.getXmlObject().xmlText();
            if (xmlText == null || xmlText.isBlank()) return false;
            Document doc = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xmlText)));
            NodeList videoNodes = doc.getElementsByTagNameNS("http://schemas.openxmlformats.org/presentationml/2006/main", "video");
            if (videoNodes != null && videoNodes.getLength() > 0) return true;
            NodeList audioNodes = doc.getElementsByTagNameNS("http://schemas.openxmlformats.org/presentationml/2006/main", "audio");
            if (audioNodes != null && audioNodes.getLength() > 0) return true;
            NodeList cMediaNodes = doc.getElementsByTagNameNS("http://schemas.openxmlformats.org/presentationml/2006/main", "cMediaNode");
            if (cMediaNodes != null && cMediaNodes.getLength() > 0) return true;
        } catch (Exception e) {
            log.debug("[SlideRender] 检测嵌入媒体异常 slideNum={}", slide.getSlideNumber(), e);
        }
        return false;
    }
}
