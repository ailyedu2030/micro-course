package com.microcourse.plugin.interactive.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.entity.Course;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.plugin.interactive.dto.SegmentAudioVO;
import com.microcourse.plugin.interactive.dto.SlidePageVO;
import com.microcourse.plugin.interactive.dto.SlideUploadResponse;
import com.microcourse.plugin.interactive.dto.SlideVO;
import com.microcourse.plugin.interactive.dto.PageAudioVO;
import com.microcourse.plugin.interactive.dto.HtmlSegmentVO;
import com.microcourse.plugin.interactive.dto.PptFlowVO;
import com.microcourse.plugin.interactive.entity.CourseSlide;
import com.microcourse.plugin.interactive.entity.SlidePage;
import com.microcourse.plugin.interactive.entity.SlidePptPage;
import com.microcourse.plugin.interactive.entity.SlidePptPageAudio;
import com.microcourse.plugin.interactive.entity.SlidePptPageScript;
import com.microcourse.plugin.interactive.entity.SlidePptFlow;
import com.microcourse.plugin.interactive.entity.SlideHtmlUnit;
import com.microcourse.plugin.interactive.entity.SlideHtmlSegmentScript;
import com.microcourse.plugin.interactive.entity.SlideHtmlSegmentAudio;
import com.microcourse.plugin.interactive.mapper.CourseSlideMapper;
import com.microcourse.plugin.interactive.mapper.SlidePageMapper;
import com.microcourse.plugin.interactive.mapper.SlidePptPageMapper;
import com.microcourse.plugin.interactive.mapper.SlidePptPageScriptMapper;
import com.microcourse.plugin.interactive.mapper.SlidePptPageAudioMapper;
import com.microcourse.plugin.interactive.mapper.SlidePptFlowMapper;
import com.microcourse.plugin.interactive.mapper.SlideHtmlUnitMapper;
import com.microcourse.plugin.interactive.mapper.SlideHtmlSegmentScriptMapper;
import com.microcourse.plugin.interactive.mapper.SlideHtmlSegmentAudioMapper;
import com.microcourse.plugin.interactive.cache.CoursewarePagesCache;
import com.microcourse.plugin.interactive.service.SlideService;
import com.microcourse.plugin.interactive.service.HtmlSegmentDetector;
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
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
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
    // P0 聚合（v1+v2 双轨，方案 P0-4）
    private final SlidePptPageMapper pptPageMapper;
    private final SlidePptPageScriptMapper pptScriptMapper;
    private final SlidePptPageAudioMapper pptAudioMapper;
    private final SlidePptFlowMapper pptFlowMapper;
    private final SlideHtmlUnitMapper htmlUnitMapper;
    private final SlideHtmlSegmentScriptMapper htmlSegmentScriptMapper;
    private final SlideHtmlSegmentAudioMapper htmlSegmentAudioMapper;

    /** Q-2 (N+1 修复): 课件播放页 Redis 缓存（courseware:pages:{courseId}:... TTL 10min） */
    private final CoursewarePagesCache pagesCache;

    /** P2-1 (F-2026-08-07-HTML-Update): HTML 自动分段检测（上传/替换时同步产出 detected_segments） */
    private final HtmlSegmentDetector htmlSegmentDetector;

    // Micrometer 指标 (HTML 互动课件 - 灰度监控)
    private final io.micrometer.core.instrument.Counter htmlLoadCounter;
    private final io.micrometer.core.instrument.Counter htmlXssBlockedCounter;

    @Value("${plugin.interactive.slides.storage-path:/data/slides}")
    private String storagePath;

    @Value("${plugin.interactive.html-content.max-file-size:5242880}")
    private long maxHtmlSize;

    public SlideServiceImpl(CourseSlideMapper courseSlideMapper,
                            SlidePageMapper slidePageMapper,
                            CourseRepository courseRepository,
                            CourseChapterRepository courseChapterRepository,
                            CourseSectionRepository sectionRepo,
                            SlideRenderService slideRenderService,
                            SlidePptPageMapper pptPageMapper,
                            SlidePptPageScriptMapper pptScriptMapper,
                            SlidePptPageAudioMapper pptAudioMapper,
                            SlidePptFlowMapper pptFlowMapper,
                            SlideHtmlUnitMapper htmlUnitMapper,
                            SlideHtmlSegmentScriptMapper htmlSegmentScriptMapper,
                            SlideHtmlSegmentAudioMapper htmlSegmentAudioMapper,
                            CoursewarePagesCache pagesCache,
                            HtmlSegmentDetector htmlSegmentDetector,
                            io.micrometer.core.instrument.MeterRegistry meterRegistry) {
        this.courseSlideMapper = courseSlideMapper;
        this.slidePageMapper = slidePageMapper;
        this.courseRepository = courseRepository;
        this.courseChapterRepository = courseChapterRepository;
        this.sectionRepo = sectionRepo;
        this.slideRenderService = slideRenderService;
        this.pptPageMapper = pptPageMapper;
        this.pptScriptMapper = pptScriptMapper;
        this.pptAudioMapper = pptAudioMapper;
        this.pptFlowMapper = pptFlowMapper;
        this.htmlUnitMapper = htmlUnitMapper;
        this.htmlSegmentScriptMapper = htmlSegmentScriptMapper;
        this.htmlSegmentAudioMapper = htmlSegmentAudioMapper;
        this.pagesCache = pagesCache;
        this.htmlSegmentDetector = htmlSegmentDetector;
        // HTML 互动课件监控指标 (灰度观察 6.4 用)
        this.htmlLoadCounter = io.micrometer.core.instrument.Counter.builder("interactive_html_load_total")
                .description("HTML 课件成功加载次数（白名单教师上传后学生可访问）")
                .tag("type", "html_courseware")
                .register(meterRegistry);
        this.htmlXssBlockedCounter = io.micrometer.core.instrument.Counter.builder("interactive_html_xss_blocked_total")
                .description("XSS payload 被 sanitize 拦截次数")
                .tag("type", "html_courseware")
                .register(meterRegistry);
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

        // F-2026-08-07-14：课时级上传未带 chapterId 时从 section 派生并回填 slide，
        // 否则 slide_ppt_pages.chapter_id NOT NULL 导致渲染必失败
        if (chapterId == null && sectionId != null && sectionRepo != null) {
            CourseSection sec = sectionRepo.selectById(sectionId);
            if (sec != null && sec.getChapterId() != null) {
                chapterId = sec.getChapterId();
                CourseSlide stored = courseSlideMapper.selectById(sid);
                if (stored != null && stored.getChapterId() == null) {
                    stored.setChapterId(chapterId);
                    stored.setUpdatedAt(LocalDateTime.now());
                    courseSlideMapper.updateById(stored);
                    log.info("[SlideUpload] derived chapterId={} from section={} for slide={}",
                            chapterId, sectionId, sid);
                }
            }
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
            public void afterCommit() {
                slideRenderService.renderAsync(finalSid, fc, fs, fb);
                // Q-2: 课件内容变更 → 事务提交后失效播放页缓存（学生端立即看到新内容）
                pagesCache.invalidateCourse(courseId);
            }
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
        String safeHtml = HtmlSanitizer.sanitizeForCourseware(rawHtml, true);
        if (safeHtml.isEmpty() && !rawHtml.isEmpty()) {
            // XSS payload 全部被 sanitize 移除 → 计入拦截计数
            htmlXssBlockedCounter.increment();
            throw new BusinessException(ErrorCode.HTML_SANITIZE_REMOVED_ALL);
        }
        // 成功 sanitize → 计入加载计数
        htmlLoadCounter.increment();

        String safeFilename = XssSanitizer.sanitizePlainText(
                file.getOriginalFilename() != null ? file.getOriginalFilename() : "slide.html");
        if (safeFilename == null || safeFilename.isBlank()) { safeFilename = "slide.html"; }

        // Q-4 审计: 教师（课程 owner，前面已校验）上传 HTML 课件 → 信任标记 + 审计日志。
        // is_trusted 实际落库在 HtmlCoursewareService.createUnit/updateUnit（v2 单元），
        // 此处记录 v1 路径审计（v1 HTML_DIRECT 页同样来自可信教师）。
        log.info("[TrustAudit] F-2026-08-07-HTML: courseId={}, teacherId={}, uploaderId={}, filename={}, size={}",
                courseId, course.getTeacherId(), SecurityUtil.getCurrentUserIdOpt(), safeFilename, file.getSize());
        // Q-2: 课件内容变更 → 失效播放页缓存（学生端立即看到新内容）
        pagesCache.invalidateCourse(courseId);

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
        // P1-C 修复 (2026-07-19): 改为非破坏性 UPSERT
        // 旧逻辑: delete + insert 会清空 audio 元数据(narrationAudioUrl / segmentCount)
        //        导致后续 GET /slides/pages 丢失 segmentAudios,reload 后的 token URL 全部 403
        // 新逻辑: 按 (slideId, pageNumber=1) 查找,存在则只更新 htmlContent/contentType/imageUrl;
        //        保留 narrationAudioUrl/audioDuration/segmentCount/voice/ttsModel/generatedAt
        SlidePage page = slidePageMapper.selectOne(
                new LambdaQueryWrapper<SlidePage>()
                        .eq(SlidePage::getSlideId, sid)
                        .eq(SlidePage::getPageNumber, 1));
        if (page == null) {
            page = new SlidePage();
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
        } else {
            // 保留 audio 元数据(narrationAudioUrl / audioDuration / segmentCount / voice / ttsModel / generatedAt)
            // 若当前页面尚未配音,保持原 narrationStatus;若已有音频,标记 HTML 重新上传但音频仍可用
            String prevStatus = page.getNarrationStatus();
            page.setContentType("HTML_DIRECT");
            page.setHtmlContent(safeHtml);
            page.setImageUrl("html:no-image");
            page.setUpdatedAt(LocalDateTime.now());
            int affectedHtml = slidePageMapper.updateById(page);
            if (affectedHtml == 0) {
                throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION,
                        "HTML 课件已被修改,请刷新后重试");
            }
            log.info("[SlideUpload-HtmlFile] UPSERT(in-place): slideId={}, courseId={}, "
                    + "preservedAudioStatus={}, preservedSegmentCount={}",
                    sid, courseId, prevStatus, page.getSegmentCount());
        }
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

        // ===== P0-3 (F-2026-08-07-HTML-Update): v2 unit 同步 — 替换 HTML 静默失效的根因修复 =====
        // getPages 为 v2 优先（slide_html_units 存在则学生端永远读 v2），若只更新 v1
        // course_slides/slide_pages 而不触碰 v2 unit，替换后的新内容将成为死数据，
        // 所有端继续显示旧内容（核心操作必现失败）。
        // 此处：v2 unit 存在 → 同步 html_sanitized + is_trusted + 自动段检测 + 缓存已在上方失效。
        SlideHtmlUnit v2Unit = null;
        if (sectionId != null) {
            v2Unit = htmlUnitMapper.findBySection(sectionId);
        } else if (chapterId != null) {
            v2Unit = htmlUnitMapper.findByChapter(chapterId);
        }
        if (v2Unit != null) {
            v2Unit.setHtmlContent(rawHtml);
            v2Unit.setHtmlSanitized(safeHtml);
            // uploadHtmlFile 开头已校验 course owner/ADMIN（isOwnerOrAdmin），
            // 与 HtmlCoursewareServiceImpl.computeIsTrusted 语义一致 → is_trusted=true
            v2Unit.setIsTrusted(true);
            v2Unit.setFileSizeBytes(file.getSize());
            v2Unit.setUpdatedAt(LocalDateTime.now());
            // P2-1 双保险：替换上传时自动分段检测，教师打开课件立即看到 N 个段（无需手动触发）
            int detected = htmlSegmentDetector.detectSegments(safeHtml).size();
            v2Unit.setDetectedSegments(detected);
            int affectedUnit = htmlUnitMapper.updateById(v2Unit);
            if (affectedUnit == 0) {
                throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION,
                        "HTML 单元已被修改，请刷新后重试");
            }
            log.info("[SlideUpload-HtmlFile] P0-3 v2 unit synced: unitId={}, courseId={}, "
                            + "sectionId={}, chapterId={}, detectedSegments={}, isTrusted=true",
                    v2Unit.getId(), courseId, sectionId, chapterId, detected);
            // P0-3 审计：替换操作落账（谁在何时替换了哪个 v2 unit 的 HTML 内容）
            log.info("[TrustAudit] F-2026-08-07-HTML-Update: courseId={}, unitId={}, teacherId={}, "
                            + "uploaderId={}, filename={}, size={}, detectedSegments={}",
                    courseId, v2Unit.getId(), course.getTeacherId(), SecurityUtil.getCurrentUserIdOpt(),
                    safeFilename, file.getSize(), detected);
        } else {
            log.info("[SlideUpload-HtmlFile] no v2 unit for courseId={}, sectionId={}, chapterId={} "
                    + "(keep v1 path; next access falls back to v1 when v2 absent)",
                    courseId, sectionId, chapterId);
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
                for (XSLFTextShape shape : slide.getPlaceholders()) {
                    String text = extractShapeText(shape);
                    if (!text.isEmpty()) {
                        html.append("    <h2>").append(escapeHtml(text)).append("</h2>\n");
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
    public List<SlidePageVO> getPages(Long courseId, Long sectionId, Long chapterId) {
        // P0-4: v2 优先聚合（slide_ppt_pages / slide_html_units），无 v2 则回退 legacy。
        // P1-C-1: section 级与 chapter 级对称走 v2 聚合；v2 数据必须归属 courseId，
        //         否则回退 legacy（legacy 查询带 course_id 过滤，天然防跨课程泄露）。
        if (sectionId != null) {
            List<SlidePptPage> v2PptPages = pptPageMapper.listBySection(sectionId);
            if (!v2PptPages.isEmpty() && allBelongToCourse(v2PptPages, courseId)) {
                return cachedOrBuildV2PptPages(courseId, sectionId, null, v2PptPages);
            }
            SlideHtmlUnit v2HtmlUnit = htmlUnitMapper.findBySection(sectionId);
            if (v2HtmlUnit != null && courseId.equals(v2HtmlUnit.getCourseId())) {
                return cachedOrBuildV2HtmlPage(courseId, sectionId, null, v2HtmlUnit);
            }
        }
        if (chapterId != null) {
            List<SlidePptPage> v2PptPages = pptPageMapper.listByChapter(chapterId);
            if (!v2PptPages.isEmpty() && allBelongToCourse(v2PptPages, courseId)) {
                // 章节级 flow 规则不适用（与 getCoursewareTree.buildPptTree 章节级语义一致），
                // 传入 null sectionId → listBySection 恒空 → flows 为空列表。
                return cachedOrBuildV2PptPages(courseId, null, chapterId, v2PptPages);
            }
            SlideHtmlUnit v2HtmlUnit = htmlUnitMapper.findByChapter(chapterId);
            if (v2HtmlUnit != null && courseId.equals(v2HtmlUnit.getCourseId())) {
                return cachedOrBuildV2HtmlPage(courseId, null, chapterId, v2HtmlUnit);
            }
        }

        LambdaQueryWrapper<SlidePage> qw = new LambdaQueryWrapper<>();
        qw.eq(SlidePage::getCourseId, courseId);
        // P1-C 修复：章节级课件页存于 chapter_id、section_id 为 NULL。
        // 此前控制器把 chapterId 折叠成 sectionId 查询（section_id = chapterId 永假），
        // 渲染成功的页在列表接口中永远查不到 → 学生播放器"图片加载失败"、教师页列表为空。
        if (sectionId != null) {
            qw.eq(SlidePage::getSectionId, sectionId);
        } else if (chapterId != null) {
            qw.eq(SlidePage::getChapterId, chapterId);
        } else {
            qw.isNull(SlidePage::getSectionId);
        }
        qw.orderByAsc(SlidePage::getSlideId).orderByAsc(SlidePage::getPageNumber);
        List<SlidePage> dbPages = slidePageMapper.selectList(qw);
        List<SlidePageVO> vos = dbPages.stream().map(this::toPageVO).collect(Collectors.toList());

        // P0-1: 仅替换 AUDIO_SEG_XX_URL 占位符（供 HTML 模板内原生 <audio> 引用），
        // 不再注入分段音频控制器脚本（buildSegmentControllerJs 语法损坏，见 D1）。
        Map<Integer, String> segUrls = new HashMap<>();
        for (SlidePageVO vo : vos) {
            if (vo.getSegmentAudio() != null) {
                segUrls.put(vo.getPageNumber(), vo.getSegmentAudio().getUrl());
            }
        }
        for (SlidePageVO vo : vos) {
            String html = vo.getHtmlContent();
            if ("HTML_DIRECT".equals(vo.getContentType()) && html != null && html.contains("AUDIO_SEG_")) {
                for (Map.Entry<Integer, String> entry : segUrls.entrySet()) {
                    String placeholder = "AUDIO_SEG_" + String.format("%02d", entry.getKey()) + "_URL";
                    html = html.replace(placeholder, entry.getValue());
                }
                vo.setHtmlContent(html);
            }
        }

        return vos;
    }

    // ==================== P0-4: v2 播放数据聚合 ====================

    /**
     * P1-C-1: v2 页面数据必须全部归属 courseId（防跨课程章节枚举泄露）。
     * 与 getCoursewareTree.validateSectionBelongsToCourse 的污染拒绝语义一致。
     */
    private boolean allBelongToCourse(List<SlidePptPage> pages, Long courseId) {
        for (SlidePptPage p : pages) {
            if (!courseId.equals(p.getCourseId())) {
                log.warn("[getPages] v2 page 跨 course 污染被拦截: path={} actual={}, pageId={}",
                        courseId, p.getCourseId(), p.getId());
                return false;
            }
        }
        return true;
    }

    /**
     * Q-2 (N+1 修复): 播放页构建前先查 Redis 缓存（courseware:pages:{courseId}:{sectionId}:{chapterId}, TTL 10min）。
     * PPT 路径无动态 nonce，可安全缓存。数据变更由 upload/delete/update 触发 invalidateCourse 失效。
     */
    private List<SlidePageVO> cachedOrBuildV2PptPages(Long courseId, Long sectionId, Long chapterId,
                                                      List<SlidePptPage> pages) {
        Optional<List<SlidePageVO>> cached = pagesCache.get(courseId, sectionId, chapterId);
        if (cached.isPresent()) {
            return cached.get();
        }
        List<SlidePageVO> built = buildV2PptPages(courseId, sectionId, pages);
        pagesCache.put(courseId, sectionId, chapterId, built);
        return built;
    }

    /**
     * Q-2: HTML 播放页缓存 — 仅 is_trusted=true（宽松内容）可缓存；
     * is_trusted=false 读时注入动态 CSP nonce（Q-4），nonce 必须每次请求不同 → 不缓存。
     */
    private List<SlidePageVO> cachedOrBuildV2HtmlPage(Long courseId, Long sectionId, Long chapterId,
                                                      SlideHtmlUnit unit) {
        boolean cacheable = Boolean.TRUE.equals(unit.getIsTrusted());
        if (cacheable) {
            Optional<List<SlidePageVO>> cached = pagesCache.get(courseId, sectionId, chapterId);
            if (cached.isPresent()) {
                return cached.get();
            }
        }
        List<SlidePageVO> built = java.util.Collections.singletonList(buildV2HtmlPage(courseId, unit));
        if (cacheable) {
            pagesCache.put(courseId, sectionId, chapterId, built);
        }
        return built;
    }

    private List<SlidePageVO> buildV2PptPages(Long courseId, Long sectionId, List<SlidePptPage> pages) {
        // Q-2 (N+1 修复): 批量查询取代逐页查询（2 SQL 取代 2N SQL）
        //   1) listActiveByPageIds → Map<pageId, activeScript>（1 SQL）
        //   2) listByScriptIds     → Map<scriptId, List<Audio>>（1 SQL，is_default DESC, completed_at DESC 排序）
        List<Long> pageIds = pages.stream().map(SlidePptPage::getId)
                .filter(Objects::nonNull).toList();
        List<SlidePptPageScript> activeScripts = pageIds.isEmpty()
                ? List.of() : pptScriptMapper.listActiveByPageIds(pageIds);
        Map<Long, SlidePptPageScript> scriptByPage = activeScripts.stream()
                .filter(s -> s.getPptPageId() != null)
                .collect(Collectors.toMap(SlidePptPageScript::getPptPageId, s -> s, (a, b) -> a));
        List<Long> scriptIds = activeScripts.stream().map(SlidePptPageScript::getId)
                .filter(Objects::nonNull).toList();
        Map<Long, List<SlidePptPageAudio>> audiosByScript = scriptIds.isEmpty()
                ? Map.of()
                : pptAudioMapper.listByScriptIds(scriptIds).stream()
                        .filter(a -> a.getScriptId() != null)
                        .collect(Collectors.groupingBy(SlidePptPageAudio::getScriptId));

        List<SlidePageVO> vos = new java.util.ArrayList<>(pages.size());
        for (SlidePptPage p : pages) {
            SlidePageVO vo = new SlidePageVO();
            vo.setId(p.getId());
            vo.setSlideId(p.getSlideId());
            vo.setChapterId(p.getChapterId());
            vo.setSectionId(p.getSectionId());
            vo.setCourseId(p.getCourseId());
            vo.setPageNumber(p.getPageNumber());
            vo.setContentType("PPT_RENDERED");
            vo.setImageUrl(p.getImageUrl());
            vo.setThumbnailUrl(p.getThumbnailUrl());
            vo.setImageWidth(p.getImageWidth());
            vo.setImageHeight(p.getImageHeight());
            vo.setExtractedText(p.getExtractedText());
            vo.setHasAnimation(p.getHasAnimation());
            vo.setHasEmbeddedMedia(p.getHasEmbeddedMedia());
            vo.setCreatedAt(p.getCreatedAt());
            vo.setUpdatedAt(p.getUpdatedAt());

            SlidePptPageScript active = scriptByPage.get(p.getId());
            if (active != null) {
                vo.setNarrationScript(active.getScriptText());
                // U-5: SQL 已按 is_default DESC, completed_at DESC 排序 → 取首个 READY 即"默认音色 → 最新完成"
                SlidePptPageAudio ready = pickReadyAudio(audiosByScript.getOrDefault(active.getId(), List.of()));
                if (ready != null) {
                    vo.setAudio(toPageAudioVO(courseId, ready));
                    vo.setNarrationAudioUrl(ready.getAudioUrl());
                    vo.setAudioDuration(ready.getAudioDurationMs() != null
                            ? Math.max(1, ready.getAudioDurationMs() / 1000) : null);
                    vo.setNarrationStatus("AUDIO_READY");
                } else {
                    vo.setNarrationStatus("AUDIO_GENERATING");
                }
                vo.setVoice(active.getVoice());
                vo.setTtsModel(active.getTtsModel());
            } else {
                vo.setNarrationStatus("PENDING");
            }
            vo.setNarrationStatusText(SlidePageVO.narrationStatusText(vo.getNarrationStatus()));
            vos.add(vo);
        }
        // section 级 flow 规则（挂在每页节点，前端按 fromPageId 建索引）
        List<PptFlowVO> flows = pptFlowMapper.listBySection(sectionId).stream()
                .map(this::toPptFlowVO).collect(Collectors.toList());
        for (SlidePageVO vo : vos) {
            vo.setFlows(flows);
        }
        return vos;
    }

    private SlidePageVO buildV2HtmlPage(Long courseId, SlideHtmlUnit unit) {
        SlidePageVO vo = new SlidePageVO();
        vo.setId(unit.getId());
        vo.setSlideId(unit.getSlideId());
        vo.setChapterId(unit.getChapterId());
        vo.setSectionId(unit.getSectionId());
        vo.setCourseId(unit.getCourseId());
        vo.setPageNumber(1);
        vo.setContentType("HTML_DIRECT");
        vo.setHtmlContent(unit.getHtmlSanitized() != null ? unit.getHtmlSanitized() : unit.getHtmlContent());
        vo.setCreatedAt(unit.getCreatedAt());
        vo.setUpdatedAt(unit.getUpdatedAt());

        List<SlideHtmlSegmentScript> segs = htmlSegmentScriptMapper.listActiveByUnit(unit.getId());
        // Q-2 (N+1 修复): 一次批量取所有段的音频（1 SQL 取代 N 次 listByScript）
        List<Long> segScriptIds = segs.stream().map(SlideHtmlSegmentScript::getId)
                .filter(Objects::nonNull).toList();
        Map<Long, List<SlideHtmlSegmentAudio>> audiosByScript = segScriptIds.isEmpty()
                ? Map.of()
                : htmlSegmentAudioMapper.listByScriptIds(segScriptIds).stream()
                        .filter(a -> a.getSegmentScriptId() != null)
                        .collect(Collectors.groupingBy(SlideHtmlSegmentAudio::getSegmentScriptId));
        List<HtmlSegmentVO> segmentVos = new java.util.ArrayList<>();
        int readyCount = 0;
        boolean generating = false;
        for (SlideHtmlSegmentScript s : segs) {
            HtmlSegmentVO segVo = new HtmlSegmentVO();
            segVo.setIndex(s.getSegmentIndex());
            segVo.setMarker(s.getSegmentMarker());
            segVo.setText(s.getSegmentText());
            segVo.setScriptText(s.getScriptText());
            // U-5: SQL 已按 is_default DESC, completed_at DESC 排序 → 取首个 READY 即默认/最新音色
            SlideHtmlSegmentAudio ready = pickReadyHtmlAudio(
                    audiosByScript.getOrDefault(s.getId(), List.of()));
            if (ready != null) {
                segVo.setAudio(toPageAudioVO(courseId, ready));
                readyCount++;
            } else if (audiosByScript.getOrDefault(s.getId(), List.of())
                    .stream().anyMatch(a -> "GENERATING".equals(a.getStatus()))) {
                generating = true;
            }
            segmentVos.add(segVo);
        }
        vo.setSegments(segmentVos);
        vo.setNarrationStatus(readyCount == segs.size() && !segs.isEmpty()
                ? "AUDIO_READY"
                : (readyCount > 0 || generating) ? "AUDIO_GENERATING" : "PENDING");
        vo.setNarrationStatusText(SlidePageVO.narrationStatusText(vo.getNarrationStatus()));
        // P2-2：读时增强 —— marker 注入 data-segment + 高亮 CSS + bridge.js（不落库）
        // Q-4: is_trusted=false（未标记可信教师）→ 注入动态 CSP nonce（每次请求不同，故不缓存）
        String html = vo.getHtmlContent();
        if (html != null && !segmentVos.isEmpty()) {
            String cspNonce = Boolean.TRUE.equals(unit.getIsTrusted())
                    ? null : UUID.randomUUID().toString().replace("-", "");
            vo.setHtmlContent(enhanceHtmlSegments(html, segmentVos, cspNonce));
        }
        return vo;
    }

    /**
     * U-5: PPT 音色确定性选择。SQL 已按 is_default DESC, completed_at DESC 排序，
     * 取首个 READY 即"教师默认音色 → 最新完成"（多 READY 时不随机、不取旧音色）。
     */
    private SlidePptPageAudio pickReadyAudio(List<SlidePptPageAudio> audios) {
        if (audios == null || audios.isEmpty()) return null;
        return audios.stream()
                .filter(a -> "READY".equals(a.getStatus()))
                .findFirst().orElse(null);
    }

    /**
     * U-5: HTML 段音色确定性选择（同上）。
     */
    private SlideHtmlSegmentAudio pickReadyHtmlAudio(List<SlideHtmlSegmentAudio> audios) {
        if (audios == null || audios.isEmpty()) return null;
        return audios.stream()
                .filter(a -> "READY".equals(a.getStatus()))
                .findFirst().orElse(null);
    }

    /**
     * P2-2（方案 §5.2/§8.2）：为 HTML 课件注入分段标记与平台桥接脚本。
     * - 有 segment_marker（如 "seg-1"）：给对应 id 元素补 data-segment="N"
     * - 无 marker：按顺序给前 N 个标题/段落元素补 data-segment
     * - 注入 .active 高亮 CSS 与 bridge.js（点击段→segment-active；接收 segment-activated 高亮）
     * 只读增强（入播放器时组装），不写库，不经过 sanitize 白名单（教师内容不被改）。
     *
     * Q-4: cspNonce 非空时（is_trusted=false 严格模式）：
     * - bridge.js script 标签带 nonce="{cspNonce}"（CSP 仅放行该脚本）
     * - <head> 注入 <meta http-equiv="Content-Security-Policy" content="script-src 'nonce-{cspNonce}'">
     *   （纵深防御：即使内容被注入内联脚本也被 CSP 拦截）
     * is_trusted=true（可信教师课件，含自有 script）→ cspNonce=null，不注入 CSP（保持现有功能）。
     */
    private String enhanceHtmlSegments(String html, List<HtmlSegmentVO> segments, String cspNonce) {
        String out = html;
        int autoCursor = 0;
        for (HtmlSegmentVO seg : segments) {
            int idx = seg.getIndex();
            String marker = seg.getMarker();
            if (marker != null && !marker.isBlank()) {
                String idAttr = "id=\"" + marker + "\"";
                String idAttrSingle = "id='" + marker + "'";
                if (out.contains(idAttr)) {
                    out = out.replace(idAttr, idAttr + " data-segment=\"" + idx + "\"");
                    continue;
                }
                if (out.contains(idAttrSingle)) {
                    out = out.replace(idAttrSingle, idAttrSingle + " data-segment=\"" + idx + "\"");
                    continue;
                }
            }
            // 无 marker（或 marker 未命中）：按顺序给 h1-h3/section/p 元素注入
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("(?is)(<(h[1-3]|section|p)\\b[^>]*?)>")
                    .matcher(out);
            int target = 0;
            while (m.find()) {
                if (target == autoCursor) {
                    String tag = m.group(1);
                    String replacement = (tag.contains("data-segment")
                            ? tag : tag + " data-segment=\"" + idx + "\"") + ">";
                    out = out.substring(0, m.start()) + replacement + out.substring(m.end());
                    autoCursor++;
                    break;
                }
                target++;
            }
            autoCursor++;
        }

        String css = "<style>"
                + "[data-segment]{scroll-margin-top:12px;transition:box-shadow .25s ease,background-color .25s ease}"
                + "[data-segment].active{box-shadow:0 0 0 3px #6366f1;background:rgba(99,102,241,.10)}"
                + "</style>";
        StringBuilder js = new StringBuilder();
        String nonceAttr = cspNonce != null ? " nonce=\"" + cspNonce + "\"" : "";
        js.append("<script").append(nonceAttr).append(">(function(){")
                .append("var segs=").append(toJsonSegments(segments)).append(";")
                .append("function post(m){parent.postMessage(m,'*')}")
                .append("function ready(){post({type:'slide-audio-v2',version:2,action:'ready',segments:segs})}")
                .append("function onReady(){if(document.readyState==='loading'){document.addEventListener('DOMContentLoaded',ready)}else{ready()}}")
                .append("document.addEventListener('click',function(e){var el=e.target&&e.target.closest?e.target.closest('[data-segment]'):null;if(el){post({type:'slide-audio-v2',version:2,action:'segment-active',index:Number(el.getAttribute('data-segment'))})}});")
                .append("window.addEventListener('message',function(e){var m=e.data;if(!m||m.type!=='slide-audio-state-v2')return;if(m.state==='segment-activated'&&m.index!=null){document.querySelectorAll('[data-segment]').forEach(function(n){n.classList.toggle('active',Number(n.getAttribute('data-segment'))===m.index)})}});")
                .append("onReady();})();</script>");
        String bridge = css + js;
        // Q-4: CSP meta 必须位于 <head> 内且先于内容，才被浏览器采纳（script-src 'nonce-...'）
        if (cspNonce != null) {
            String cspMeta = "<meta http-equiv=\"Content-Security-Policy\" "
                    + "content=\"script-src 'nonce-" + cspNonce + "'\">";
            int headIdx = out.indexOf("<head>");
            if (headIdx >= 0) {
                out = out.substring(0, headIdx + 6) + cspMeta + out.substring(headIdx + 6);
            } else {
                out = cspMeta + out;
            }
        }
        int idx = out.lastIndexOf("</body>");
        if (idx < 0) return out + bridge;
        return out.substring(0, idx) + bridge + out.substring(idx);
    }

    private String toJsonSegments(List<HtmlSegmentVO> segments) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < segments.size(); i++) {
            HtmlSegmentVO s = segments.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"index\":").append(s.getIndex())
                    .append(",\"marker\":").append(s.getMarker() != null ? "\"" + escapeJson(s.getMarker()) + "\"" : "null")
                    .append("}");
        }
        return sb.append("]").toString();
    }

    /**
     * Q-3 (XSS 加固): JSON 字符串转义。除反斜杠与双引号外，补转义尖括号与 &（对齐 JSON 标准 unicode 转义）：
     * - '&lt;' 转义为反斜杠u003c：segment_marker 含 "&lt;/script&gt;" 时无法提前闭合 bridge script（防注入桥破坏）
     * - '&gt;' 转义为反斜杠u003e：对称性，避免 "]]&gt;" 提前结束 CDATA 类场景
     * - '&amp;' 转义为反斜杠u0026：与 JSON 标准一致
     */
    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("<", "\\u003c").replace(">", "\\u003e").replace("&", "\\u0026");
    }

    private PageAudioVO toPageAudioVO(Long courseId, SlidePptPageAudio a) {
        PageAudioVO vo = new PageAudioVO();
        vo.setToken(a.getAudioToken());
        vo.setUrl("/api/courses/" + courseId + "/courseware/audio/" + a.getAudioToken());
        vo.setDurationMs(a.getAudioDurationMs());
        vo.setStatus(a.getStatus());
        vo.setVoiceUsed(a.getVoiceUsed());
        vo.setModelUsed(a.getModelUsed());
        vo.setScriptId(a.getScriptId());
        return vo;
    }

    private PageAudioVO toPageAudioVO(Long courseId, SlideHtmlSegmentAudio a) {
        PageAudioVO vo = new PageAudioVO();
        vo.setToken(a.getAudioToken());
        vo.setUrl("/api/courses/" + courseId + "/courseware/audio/" + a.getAudioToken());
        vo.setDurationMs(a.getAudioDurationMs());
        vo.setStatus(a.getStatus());
        vo.setVoiceUsed(a.getVoiceUsed());
        vo.setModelUsed(a.getModelUsed());
        vo.setScriptId(a.getSegmentScriptId());
        return vo;
    }

    private PptFlowVO toPptFlowVO(SlidePptFlow e) {
        PptFlowVO vo = new PptFlowVO();
        vo.setFromPageId(e.getFromPageId());
        vo.setToPageId(e.getToPageId());
        vo.setFlowType(e.getFlowType());
        vo.setPriority(e.getPriority());
        vo.setDependsOnQuizId(e.getDependsOnQuizId());
        vo.setConditionExpression(e.getConditionExpression());
        vo.setDescription(e.getDescription());
        return vo;
    }

    @Override
    public List<SegmentAudioVO> getSegmentAudios(Long courseId, Long sectionId) {
        List<SlidePageVO> pages = getPages(courseId, sectionId, null);
        List<SegmentAudioVO> result = new java.util.ArrayList<>();
        for (SlidePageVO p : pages) {
            if (p.getSegmentAudios() != null) {
                result.addAll(p.getSegmentAudios());
            } else if (p.getSegmentAudio() != null) {
                result.add(p.getSegmentAudio());
            }
        }
        return result;
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
        // P1-C 修复：图片/缩略图是学生播放器核心资源，控制器已做 verifyAccess（选课校验）；
        // 此处不得再用 getPage → verifyOwner（仅教师/管理员），否则学生 403 全部占位。
        SlidePageVO p = findPageForAccess(courseId, pageNumber);
        String fn = p.getFileUuid() != null ? p.getFileUuid() + ".png" : "page_" + pageNumber + ".png";
        Path imgPath = Paths.get(storagePath, String.valueOf(courseId), String.valueOf(p.getSlideId()), "images", fn);
        byte[] d = readImage(imgPath);
        return d.length > 0 ? d : generateFallback("第" + pageNumber + "页");
    }

    @Override
    public byte[] getPageThumbnail(Long courseId, Integer pageNumber) {
        SlidePageVO p = findPageForAccess(courseId, pageNumber);
        String fn = p.getFileUuid() != null ? p.getFileUuid() + "_thumbnail.png" : "page_" + pageNumber + ".png";
        Path thumbPath = Paths.get(storagePath, String.valueOf(courseId), String.valueOf(p.getSlideId()), "thumbnails", fn);
        byte[] d = readImage(thumbPath);
        return d.length > 0 ? d : generateFallback("第" + pageNumber + "页");
    }

    private SlidePageVO findPageForAccess(Long courseId, Integer pageNumber) {
        LambdaQueryWrapper<SlidePage> qw = new LambdaQueryWrapper<SlidePage>()
                .eq(SlidePage::getCourseId, courseId).eq(SlidePage::getPageNumber, pageNumber);
        List<SlidePage> list = slidePageMapper.selectList(qw);
        if (list.isEmpty()) { throw new BusinessException(ErrorCode.SLIDE_PAGE_NOT_FOUND); }
        return toPageVO(list.get(0));
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

        Integer segCount = p.getSegmentCount();
        if (p.getNarrationAudioUrl() != null && !p.getNarrationAudioUrl().isBlank()) {
            String segUrl = buildSegmentUrl(p);
            SegmentAudioVO seg = new SegmentAudioVO();
            seg.setPageNumber(p.getPageNumber());
            seg.setUrl(segUrl);
            seg.setDuration(p.getAudioDuration());
            vo.setSegmentAudio(seg);
        }
        if ("HTML_DIRECT".equals(p.getContentType()) && segCount != null && segCount > 1
                && p.getNarrationAudioUrl() != null && !p.getNarrationAudioUrl().isBlank()) {
            int count = segCount;
            if (extractTokenFromUrl(p.getNarrationAudioUrl()) != null) {
                java.util.ArrayList<SegmentAudioVO> segList = new java.util.ArrayList<>(count);
                for (int i = 1; i <= count; i++) {
                    SegmentAudioVO s = new SegmentAudioVO();
                    s.setPageNumber(i);
                    s.setUrl(replacePageNumberInUrl(p.getNarrationAudioUrl(), i));
                    s.setDuration(i == p.getPageNumber() ? p.getAudioDuration() : 0);
                    segList.add(s);
                }
                vo.setSegmentAudios(segList);
            }
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
        Integer segCount = p.getSegmentCount();
        if (segCount == null || segCount <= 0) segCount = 15;
        String baseUrl = p.getNarrationAudioUrl();
        if (baseUrl == null || baseUrl.isBlank()) return htmlContent;
        int segIdx = baseUrl.indexOf("/pages/");
        if (segIdx < 0) return htmlContent;
        String urlPrefix = baseUrl.substring(0, segIdx + 7);
        String urlSuffix = baseUrl.substring(baseUrl.indexOf("?", segIdx));
        for (int i = 1; i <= segCount; i++) {
            String placeholder = "AUDIO_SEG_" + String.format("%02d", i) + "_URL";
            String segUrl = urlPrefix + i + urlSuffix;
            htmlContent = htmlContent.replace(placeholder, segUrl);
        }
        return htmlContent;
    }

    private String extractTokenFromUrl(String url) {
        if (url == null) return null;
        int idx = url.indexOf("token=");
        if (idx < 0) return null;
        String t = url.substring(idx + 6);
        int a = t.indexOf('&');
        if (a > 0) t = t.substring(0, a);
        return t.isEmpty() ? null : t;
    }

    private String replacePageNumberInUrl(String url, int newPageNum) {
        return url.replaceFirst("/pages/\\d+/audio", "/pages/" + newPageNum + "/audio");
    }

    private boolean isZipHeader(byte[] b) {
        if (b.length < 4) return false;
        return b[0] == 0x50 && b[1] == 0x4B && b[2] == 0x03 && b[3] == 0x04;
    }

    private boolean validateZipBomb(byte[] bytes) {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            int c = 0; long t = 0; byte[] buf = new byte[8192];
            while (zis.getNextEntry() != null) {
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
        // Q-2: 数据变更 → 失效播放页缓存
        pagesCache.invalidateCourse(courseId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSlideById(Long courseId, Long slideId) {
        verifyOwner(courseId);
        CourseSlide slide = courseSlideMapper.selectById(slideId);
        if (slide == null || !slide.getCourseId().equals(courseId)) {
            throw new BusinessException(ErrorCode.SLIDE_NOT_FOUND, "课件不存在或已被删除");
        }
        slidePageMapper.delete(new LambdaQueryWrapper<SlidePage>().eq(SlidePage::getSlideId, slideId));
        courseSlideMapper.deleteById(slideId);
        registerSlideCleanup(courseId, slideId);
        if (slide.getSectionId() != null) {
            cleanupAudioFiles(courseId, slide.getSectionId());
        }
        // Q-2: 数据变更 → 失效播放页缓存
        pagesCache.invalidateCourse(courseId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCourseware(Long courseId, Long sectionId, Long chapterId) {
        verifyOwner(courseId);
        if (sectionId == null && chapterId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "sectionId 或 chapterId 必填");
        }
        log.info("[Slide] deleteCourseware courseId={} sectionId={} chapterId={}", courseId, sectionId, chapterId);

        // 1. v1：course_slides + slide_pages
        LambdaQueryWrapper<CourseSlide> slideQw = new LambdaQueryWrapper<CourseSlide>()
                .eq(CourseSlide::getCourseId, courseId);
        if (sectionId != null) {
            slideQw.eq(CourseSlide::getSectionId, sectionId);
        } else {
            slideQw.eq(CourseSlide::getChapterId, chapterId).isNull(CourseSlide::getSectionId);
        }
        List<CourseSlide> slides = courseSlideMapper.selectList(slideQw);
        for (CourseSlide s : slides) {
            slidePageMapper.delete(new LambdaQueryWrapper<SlidePage>().eq(SlidePage::getSlideId, s.getId()));
            courseSlideMapper.deleteById(s.getId());
            registerSlideCleanup(courseId, s.getId());
        }
        if (sectionId != null) {
            cleanupAudioFiles(courseId, sectionId);
        }

        // 2. v2 PPT：pages + scripts（含历史）+ audios + flow
        List<SlidePptPage> pptPages = sectionId != null
                ? pptPageMapper.listBySection(sectionId)
                : pptPageMapper.listByChapter(chapterId);
        // P1-I-17: 收集 v2 音频 storage_path，DB 删除前快照，供文件清理
        List<String> v2AudioStoragePaths = new ArrayList<>();
        if (!pptPages.isEmpty()) {
            List<Long> pageIds = pptPages.stream().map(SlidePptPage::getId).toList();
            List<SlidePptPageScript> scripts = pptScriptMapper.selectList(
                    new LambdaQueryWrapper<SlidePptPageScript>().in(SlidePptPageScript::getPptPageId, pageIds));
            List<Long> scriptIds = scripts.stream().map(SlidePptPageScript::getId).toList();
            if (!scriptIds.isEmpty()) {
                pptAudioMapper.selectList(
                                new LambdaQueryWrapper<SlidePptPageAudio>()
                                        .in(SlidePptPageAudio::getScriptId, scriptIds))
                        .stream().map(SlidePptPageAudio::getStoragePath)
                        .filter(Objects::nonNull).forEach(v2AudioStoragePaths::add);
                pptAudioMapper.delete(new LambdaQueryWrapper<SlidePptPageAudio>()
                        .in(SlidePptPageAudio::getScriptId, scriptIds));
                pptScriptMapper.deleteBatchIds(scriptIds);
            }
            pptPageMapper.deleteBatchIds(pageIds);
        }
        if (sectionId != null) {
            pptFlowMapper.delete(new LambdaQueryWrapper<SlidePptFlow>()
                    .eq(SlidePptFlow::getSectionId, sectionId));
        }

        // 3. v2 HTML：unit + segment scripts + audios
        SlideHtmlUnit unit = sectionId != null
                ? htmlUnitMapper.findBySection(sectionId)
                : htmlUnitMapper.findByChapter(chapterId);
        if (unit != null) {
            List<SlideHtmlSegmentScript> segScripts = htmlSegmentScriptMapper.selectList(
                    new LambdaQueryWrapper<SlideHtmlSegmentScript>()
                            .eq(SlideHtmlSegmentScript::getHtmlUnitId, unit.getId()));
            List<Long> segScriptIds = segScripts.stream().map(SlideHtmlSegmentScript::getId).toList();
            if (!segScriptIds.isEmpty()) {
                // P1-I-17: 快照 HTML 段音频 storage_path 后再删行
                htmlSegmentAudioMapper.selectList(
                                new LambdaQueryWrapper<SlideHtmlSegmentAudio>()
                                        .in(SlideHtmlSegmentAudio::getSegmentScriptId, segScriptIds))
                        .stream().map(SlideHtmlSegmentAudio::getStoragePath)
                        .filter(Objects::nonNull).forEach(v2AudioStoragePaths::add);
                htmlSegmentAudioMapper.delete(new LambdaQueryWrapper<SlideHtmlSegmentAudio>()
                        .in(SlideHtmlSegmentAudio::getSegmentScriptId, segScriptIds));
                htmlSegmentScriptMapper.deleteBatchIds(segScriptIds);
            }
            htmlUnitMapper.deleteById(unit.getId());
        }

        // 4. 清理 section.content_url
        if (sectionId != null) {
            CourseSection sec = sectionRepo.selectById(sectionId);
            if (sec != null && courseId.equals(sec.getCourseId())) {
                sec.setContentUrl(null);
                sec.setUpdatedAt(LocalDateTime.now());
                sectionRepo.updateById(sec);
            }
        }

        // 5. P1-I-17: 清理 v2 音频文件（{storage-root}/{courseId}/audio/{token}.mp3）
        // Q-5 (事务时序修复): 文件清理必须等事务提交后执行（afterCommit）——
        //   若在事务内、提交前删文件，事务回滚时文件已删而 DB 行保留 → 脏引用（学生端 404）。
        //   删除失败仅 warn（best-effort，不阻塞 DB 删除）；遗留文件由定期清理脚本兜底
        //   （建议 cron：扫描 {storage-root}/{courseId}/audio/ 下无 slide_ppt_page_audios /
        //    slide_html_segment_audios 引用且 mtime > 24h 的 mp3 并删除）。
        // Q-2: 课件删除 → 事务提交后失效播放页缓存（学生端立即感知删除）。
        List<String> audioPaths = v2AudioStoragePaths;
        Long finalCourseId = courseId;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cleanupV2AudioFiles(audioPaths);
                    pagesCache.invalidateCourse(finalCourseId);
                }
            });
        } else {
            cleanupV2AudioFiles(audioPaths);
            pagesCache.invalidateCourse(finalCourseId);
        }
    }

    /**
     * P1-I-17: 按 DB 记录的 storage_path 精确删除 v2 音频文件（TtsWorker 写入的
     * {audio-storage-root}/{courseId}/audio/{token}.mp3）。删除失败仅 warn，不阻塞 DB 删除。
     */
    private void cleanupV2AudioFiles(List<String> storagePaths) {
        for (String path : storagePaths) {
            try {
                Files.deleteIfExists(Paths.get(path));
                log.info("[Slide] 已清理 v2 音频文件: {}", path);
            } catch (Exception e) {
                log.warn("[Slide] 清理 v2 音频文件失败 path={}: {}", path, e.getMessage());
            }
        }
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
            } catch (Exception e) {
                log.warn("[Slide] 删除页面文件失败 courseId={}, pageNumber={}: {}", courseId, pageNumber, e.getMessage());
            }
        }
        if (p.getSectionId() != null) {
            cleanupPageAudioFile(courseId, p.getSectionId(), p.getPageNumber());
        }
        slidePageMapper.deleteById(p.getId());
        // Q-2: 数据变更 → 失效播放页缓存
        pagesCache.invalidateCourse(courseId);
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
        // Q-2: 数据变更 → 失效播放页缓存
        pagesCache.invalidateCourse(courseId);
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
        // Q-2: 数据变更 → 失效播放页缓存
        pagesCache.invalidateCourse(courseId);
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
                    try { Files.deleteIfExists(p); } catch (IOException e) {
                        log.debug("[Slide] 清理文件时忽略异常: {}", e.getMessage());
                    }
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
                        try { Files.deleteIfExists(p); } catch (IOException e) {
                            log.debug("[Slide] 清理文件时忽略异常: {}", e.getMessage());
                        }
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
                            try { Files.deleteIfExists(p); } catch (IOException e) {
                                log.debug("[Slide] 清理音频文件时忽略异常: {}", e.getMessage());
                            }
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

    @Override
    public void validateFileMagic(MultipartFile file) {
        byte[] magic = new byte[4];
        try (java.io.InputStream is = file.getInputStream()) {
            int read = is.read(magic);
            if (read < 4 || !isZipHeader(magic)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "文件不是 PPTX 格式(ZIP 魔数校验失败)");
            }
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "文件读取失败: " + e.getMessage());
        }
    }

}
