package com.microcourse.service.impl;

import com.microcourse.dto.*;
import com.microcourse.entity.*;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.plugin.interactive.mapper.CourseSlideMapper;
import com.microcourse.repository.*;
import com.microcourse.service.SectionService;
import com.microcourse.util.SecurityUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SectionServiceImpl implements SectionService {
    private final CourseSectionRepository sectionRepo;
    private final CourseChapterRepository chapterRepo;
    private final CourseRepository courseRepo;
    private final CourseSlideMapper courseSlideMapper;

    public SectionServiceImpl(CourseSectionRepository sectionRepo,
                              CourseChapterRepository chapterRepo,
                              CourseRepository courseRepo,
                              CourseSlideMapper courseSlideMapper) {
        this.sectionRepo = sectionRepo;
        this.chapterRepo = chapterRepo;
        this.courseRepo = courseRepo;
        this.courseSlideMapper = courseSlideMapper;
    }

    @Override
    public PageResult<SectionDTO> listByChapter(Long chapterId, int page, int size) {
        List<CourseSection> sections = sectionRepo.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CourseSection>()
                .eq(CourseSection::getChapterId, chapterId)
                .orderByAsc(CourseSection::getSortOrder));
        List<SectionDTO> dtos = sections.stream().map(this::toDTO).collect(Collectors.toList());
        PageResult<SectionDTO> result = new PageResult<>();
        result.setItems(dtos);
        result.setPage(page);
        result.setSize(size);
        result.setTotalElements(dtos.size());
        return result;
    }

    @Override
    public SectionDTO getById(Long id) {
        return toDTO(findOrThrow(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SectionDTO create(Long courseId, Long chapterId, SectionCreateRequest req) {
        assertOwner(courseId);
        CourseSection section = new CourseSection();
        section.setChapterId(chapterId);
        section.setCourseId(courseId);
        section.setTitle(req.getTitle());
        section.setSectionType(req.getSectionType());
        section.setSortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0);
        section.setDuration(req.getDuration() != null ? req.getDuration() : 0);
        section.setVisible(req.getVisible() != null ? req.getVisible() : true);
        section.setDescription(req.getDescription());
        section.setVersion(1);
        var now = java.time.LocalDateTime.now();
        section.setCreatedAt(now);
        section.setUpdatedAt(now);

        // P1 Stage 1: 小节级元信息
        if (req.getNo() != null) section.setNo(req.getNo());
        else section.setNo(String.valueOf(req.getSortOrder() != null ? req.getSortOrder() : 0));  // backfill
        if (req.getAnchorScenarioStep() != null) section.setAnchorScenarioStep(req.getAnchorScenarioStep());
        if (req.getCoreCompetency() != null) section.setCoreCompetency(req.getCoreCompetency());
        if (req.getCoursewareType() != null) section.setCoursewareType(req.getCoursewareType());
        if (req.getAudioStrategy() != null) section.setAudioStrategy(req.getAudioStrategy());
        if (req.getLearningObjectives() != null && !req.getLearningObjectives().isEmpty()) {
            try {
                section.setLearningObjectives(new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(req.getLearningObjectives()));
            } catch (Exception e) {
                throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "learningObjectives 序列化失败: " + e.getMessage());
            }
        } else {
            section.setLearningObjectives("[]");
        }

        sectionRepo.insert(section);
        return toDTO(section);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SectionDTO update(Long id, SectionUpdateRequest req) {
        CourseSection section = findOrThrow(id);
        assertOwner(section.getCourseId());
        if (req.getTitle() != null) section.setTitle(req.getTitle());
        if (req.getSectionType() != null) {
            if (!req.getSectionType().matches("VIDEO|INTERACTIVE|OFFLINE|EXERCISE"))
                throw new BusinessException(ErrorCode.SECTION_TYPE_INVALID);
            section.setSectionType(req.getSectionType());
        }
        if (req.getSortOrder() != null) section.setSortOrder(req.getSortOrder());
        if (req.getDuration() != null) section.setDuration(req.getDuration());
        if (req.getVisible() != null) section.setVisible(req.getVisible());
        if (req.getDescription() != null) section.setDescription(req.getDescription());

        // P1 Stage 1: 小节级元信息
        if (req.getNo() != null) section.setNo(req.getNo());
        if (req.getAnchorScenarioStep() != null) section.setAnchorScenarioStep(req.getAnchorScenarioStep());
        if (req.getCoreCompetency() != null) section.setCoreCompetency(req.getCoreCompetency());
        if (req.getCoursewareType() != null) section.setCoursewareType(req.getCoursewareType());
        if (req.getAudioStrategy() != null) section.setAudioStrategy(req.getAudioStrategy());
        if (req.getLearningObjectives() != null && !req.getLearningObjectives().isEmpty()) {
            try {
                section.setLearningObjectives(new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(req.getLearningObjectives()));
            } catch (Exception e) {
                throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "learningObjectives 序列化失败: " + e.getMessage());
            }
        }

        section.setUpdatedAt(java.time.LocalDateTime.now());
        sectionRepo.updateById(section);
        return toDTO(section);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, boolean force) {
        CourseSection section = findOrThrow(id);
        assertOwner(section.getCourseId());
        if (!force) {
            Integer slideCount = slideCount(id);
            if (slideCount > 0) throw new BusinessException(ErrorCode.SECTION_HAS_SLIDES);
        }
        sectionRepo.deleteById(id);
    }

    private CourseSection findOrThrow(Long id) {
        CourseSection s = sectionRepo.selectById(id);
        if (s == null) throw new BusinessException(ErrorCode.SECTION_NOT_FOUND);
        return s;
    }

    private void assertOwner(Long courseId) {
        Course course = courseRepo.selectById(courseId);
        if (course == null) throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        if (!SecurityUtil.isOwnerOrAdmin(course.getTeacherId()))
            throw new BusinessException(ErrorCode.NO_PERMISSION);
    }

    private Integer slideCount(Long sectionId) {
        Long cnt = courseSlideMapper.selectCount(
            new LambdaQueryWrapper<com.microcourse.plugin.interactive.entity.CourseSlide>()
                .eq(com.microcourse.plugin.interactive.entity.CourseSlide::getSectionId, sectionId));
        return cnt != null ? cnt.intValue() : 0;
    }

    private SectionDTO toDTO(CourseSection s) {
        SectionDTO dto = new SectionDTO();
        dto.setId(s.getId());
        dto.setChapterId(s.getChapterId());
        dto.setCourseId(s.getCourseId());
        dto.setTitle(s.getTitle());
        dto.setSectionType(s.getSectionType());
        dto.setSortOrder(s.getSortOrder());
        dto.setDuration(s.getDuration());
        dto.setVisible(s.getVisible());
        dto.setDescription(s.getDescription());
        dto.setScriptContent(s.getScriptContent());
        dto.setContentUrl(s.getContentUrl());
        int sc = slideCount(s.getId());
        dto.setSlideCount(sc);
        dto.setHasSlide(sc > 0);
        dto.setCreatedAt(s.getCreatedAt());
        dto.setUpdatedAt(s.getUpdatedAt());

        // P1 Stage 1: 小节级元信息(交叉审查 P1-1:VO 必须包含新字段)
        dto.setNo(s.getNo());
        dto.setAnchorScenarioStep(s.getAnchorScenarioStep());
        dto.setCoreCompetency(s.getCoreCompetency());
        dto.setCoursewareType(s.getCoursewareType());
        dto.setAudioStrategy(s.getAudioStrategy());
        if (s.getLearningObjectives() != null && !s.getLearningObjectives().isBlank()) {
            try {
                dto.setLearningObjectives(new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(s.getLearningObjectives(), java.util.List.class));
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(SectionServiceImpl.class)
                    .warn("[SectionDTO] learningObjectives 反序列化失败: {}", e.getMessage());
                dto.setLearningObjectives(java.util.Collections.emptyList());
            }
        }
        return dto;
    }
}
