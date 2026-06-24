package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.TagCreateRequest;
import com.microcourse.dto.TagUpdateRequest;
import com.microcourse.dto.TagVO;
import com.microcourse.entity.Course;
import com.microcourse.entity.CourseTagRelation;
import com.microcourse.entity.Tag;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.CourseRepository;
import com.microcourse.repository.CourseTagRelationRepository;
import com.microcourse.repository.TagRepository;
import com.microcourse.service.TagService;
import com.microcourse.util.SecurityUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TagServiceImpl implements TagService {

    private final TagRepository tagRepository;
    private final CourseRepository courseRepository;
    private final CourseTagRelationRepository courseTagRelationRepository;

    public TagServiceImpl(TagRepository tagRepository,
                          CourseRepository courseRepository,
                          CourseTagRelationRepository courseTagRelationRepository) {
        this.tagRepository = tagRepository;
        this.courseRepository = courseRepository;
        this.courseTagRelationRepository = courseTagRelationRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<TagVO> page(int page, int size) {
        IPage<Tag> ipage = tagRepository.selectPage(
                new Page<>(page + 1, size),
                new LambdaQueryWrapper<Tag>()
                        .orderByDesc(Tag::getCreatedAt)
        );
        List<TagVO> vos = ipage.getRecords().stream()
                .map(this::convertToVO).collect(Collectors.toList());
        PageResult<TagVO> result = new PageResult<>();
        result.setItems(vos);
        result.setPage((int) ipage.getCurrent() - 1);
        result.setSize((int) ipage.getSize());
        result.setTotalElements(ipage.getTotal());
        result.setTotalPages(ipage.getPages());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TagVO create(TagCreateRequest request) {
        Tag tag = new Tag();
        tag.setName(request.getName());
        tag.setCreatedAt(LocalDateTime.now());
        tagRepository.insert(tag);
        return convertToVO(tag);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TagVO> getCourseTags(Long courseId) {
        List<CourseTagRelation> relations = courseTagRelationRepository.selectList(
                new LambdaQueryWrapper<CourseTagRelation>().eq(CourseTagRelation::getCourseId, courseId));
        if (relations.isEmpty()) {
            return new java.util.ArrayList<>();
        }
        List<Long> tagIds = relations.stream()
                .map(CourseTagRelation::getTagId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (tagIds.isEmpty()) {
            return new java.util.ArrayList<>();
        }
        List<Tag> tags = tagRepository.selectBatchIds(tagIds);
        return tags.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addCourseTag(Long courseId, Long tagId) {
        assertCourseTagPermission(courseId);
        Tag tag = tagRepository.selectById(tagId);
        if (tag == null) {
            throw new BusinessException(ErrorCode.TAG_NOT_FOUND);
        }
        // 幂等：已绑定则直接返回，避免重复关系
        Long existing = courseTagRelationRepository.selectCount(
                new LambdaQueryWrapper<CourseTagRelation>()
                        .eq(CourseTagRelation::getCourseId, courseId)
                        .eq(CourseTagRelation::getTagId, tagId));
        if (existing != null && existing > 0) {
            return;
        }
        CourseTagRelation relation = new CourseTagRelation();
        relation.setCourseId(courseId);
        relation.setTagId(tagId);
        courseTagRelationRepository.insert(relation);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeCourseTag(Long courseId, Long tagId) {
        assertCourseTagPermission(courseId);
        courseTagRelationRepository.delete(
                new LambdaQueryWrapper<CourseTagRelation>()
                        .eq(CourseTagRelation::getCourseId, courseId)
                        .eq(CourseTagRelation::getTagId, tagId));
    }

    /**
     * 课程标签写操作 owner 校验：课程不存在 → 404；TEACHER（非 ADMIN）必须为课程创建者，否则 403。
     * ADMIN 跳过 owner 校验。与 EnrollmentService.assertCourseOwnership 语义一致。
     */
    private void assertCourseTagPermission(Long courseId) {
        Course course = courseRepository.selectById(courseId);
        if (course == null) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        if (SecurityUtil.hasRole("TEACHER") && !SecurityUtil.isAdmin()) {
            if (course.getTeacherId() == null
                    || !course.getTeacherId().equals(SecurityUtil.getCurrentUserId())) {
                throw new BusinessException(ErrorCode.NO_PERMISSION, "无权操作非本人课程的标签");
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TagVO updateTag(Long id, TagUpdateRequest request) {
        Tag tag = tagRepository.selectById(id);
        if (tag == null) {
            throw new BusinessException(ErrorCode.TAG_NOT_FOUND);
        }
        tag.setName(request.getName());
        tagRepository.updateById(tag);
        return convertToVO(tag);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTag(Long id) {
        Tag tag = tagRepository.selectById(id);
        if (tag == null) {
            throw new BusinessException(ErrorCode.TAG_NOT_FOUND);
        }
        long refCount = courseTagRelationRepository.selectCount(
                new LambdaQueryWrapper<CourseTagRelation>()
                        .eq(CourseTagRelation::getTagId, id));
        if (refCount > 0) {
            throw new BusinessException(ErrorCode.TAG_IN_USE);
        }
        tagRepository.deleteById(id);
    }

    private TagVO convertToVO(Tag tag) {
        TagVO vo = new TagVO();
        vo.setId(tag.getId());
        vo.setName(tag.getName());
        vo.setCreatedAt(tag.getCreatedAt());
        return vo;
    }
}