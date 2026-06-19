package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.TagCreateRequest;
import com.microcourse.dto.TagVO;
import com.microcourse.entity.Tag;
import com.microcourse.repository.TagRepository;
import com.microcourse.service.TagService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TagServiceImpl implements TagService {

    private final TagRepository tagRepository;

    public TagServiceImpl(TagRepository tagRepository) {
        this.tagRepository = tagRepository;
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
    @Transactional
    public TagVO create(TagCreateRequest request) {
        Tag tag = new Tag();
        tag.setName(request.getName());
        tag.setCreatedAt(LocalDateTime.now());
        tagRepository.insert(tag);
        return convertToVO(tag);
    }

    private TagVO convertToVO(Tag tag) {
        TagVO vo = new TagVO();
        vo.setId(tag.getId());
        vo.setName(tag.getName());
        vo.setCreatedAt(tag.getCreatedAt());
        return vo;
    }
}