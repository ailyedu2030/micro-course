package com.microcourse.service.impl;

import com.microcourse.dto.BannerVO;
import com.microcourse.entity.Banner;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.BannerRepository;
import com.microcourse.service.BannerService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class BannerServiceImpl implements BannerService {

    private final BannerRepository bannerRepository;

    public BannerServiceImpl(BannerRepository bannerRepository) {
        this.bannerRepository = bannerRepository;
    }

    @Override
    @Cacheable(value = "banners", key = "'list'", sync = true)
    @Transactional(readOnly = true)
    public List<BannerVO> list() {
        List<Banner> banners = bannerRepository.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Banner>()
                        .eq(Banner::getEnabled, true)
                        .orderByAsc(Banner::getSortOrder)
                        .last("LIMIT 50")
        );
        return banners.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<BannerVO> listAll() {
        // R1-P1 修复:Admin 后台需要看全部 Banner(含禁用),不走缓存、不加 enabled 过滤
        List<Banner> banners = bannerRepository.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Banner>()
                        .orderByAsc(Banner::getSortOrder)
                        .last("LIMIT 200")
        );
        return banners.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    @CacheEvict(value = "banners", allEntries = true)
    @Transactional(rollbackFor = Exception.class)
    public BannerVO create(String imageUrl, String linkUrl, Integer sortOrder, Boolean enabled) {
        Banner banner = new Banner();
        banner.setImageUrl(imageUrl);
        banner.setLinkUrl(linkUrl);
        banner.setSortOrder(sortOrder);
        banner.setEnabled(enabled);
        banner.setCreatedAt(LocalDateTime.now());
        banner.setUpdatedAt(LocalDateTime.now());
        bannerRepository.insert(banner);
        return convertToVO(banner);
    }

    @Override
    @CacheEvict(value = "banners", allEntries = true)
    @Transactional(rollbackFor = Exception.class)
    public BannerVO update(Long id, String imageUrl, String linkUrl, Integer sortOrder, Boolean enabled) {
        Banner banner = bannerRepository.selectById(id);
        if (banner == null) {
            throw new BusinessException(ErrorCode.BANNER_NOT_FOUND);
        }
        if (imageUrl != null) {
            banner.setImageUrl(imageUrl);
        }
        if (linkUrl != null) {
            banner.setLinkUrl(linkUrl);
        }
        if (sortOrder != null) {
            banner.setSortOrder(sortOrder);
        }
        if (enabled != null) {
            banner.setEnabled(enabled);
        }
        banner.setUpdatedAt(LocalDateTime.now());
        bannerRepository.updateById(banner);
        return convertToVO(banner);
    }

    @Override
    @CacheEvict(value = "banners", allEntries = true)
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Banner banner = bannerRepository.selectById(id);
        if (banner == null) {
            throw new BusinessException(ErrorCode.BANNER_NOT_FOUND);
        }
        bannerRepository.deleteById(id);
    }

    @Override
    @CacheEvict(value = "banners", allEntries = true)
    @Transactional(rollbackFor = Exception.class)
    public void toggleStatus(Long id, Boolean enabled) {
        Banner banner = bannerRepository.selectById(id);
        if (banner == null) {
            throw new BusinessException(ErrorCode.BANNER_NOT_FOUND);
        }
        banner.setEnabled(enabled);
        banner.setUpdatedAt(LocalDateTime.now());
        bannerRepository.updateById(banner);
    }

    private BannerVO convertToVO(Banner banner) {
        BannerVO vo = new BannerVO();
        vo.setId(banner.getId());
        vo.setImageUrl(banner.getImageUrl());
        vo.setLinkUrl(banner.getLinkUrl());
        vo.setSortOrder(banner.getSortOrder());
        vo.setEnabled(banner.getEnabled());
        vo.setCreatedAt(banner.getCreatedAt());
        vo.setUpdatedAt(banner.getUpdatedAt());
        return vo;
    }
}