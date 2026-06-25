package com.microcourse.controller;

import com.microcourse.dto.BannerVO;
import com.microcourse.dto.R;
import com.microcourse.service.BannerService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 公开 Banner API - 学生端首页轮播图
 *
 * P0 闭环修复 Round 4: Banner 端到端断裂
 * - 原 AdminBannerController 仅 admin 可用，学生端无任何代码调用
 * - 本 Controller 暴露 GET /api/banners 给所有用户（含未登录）
 * - 数据来源：bannerService.list() 已实现 enabled 过滤 + 缓存
 *
 * 安全：仅返回 enabled=true 的 banner，按 sortOrder 升序
 */
@RestController
@RequestMapping("/api/banners")
@PreAuthorize("permitAll()")
public class BannerPublicController {

    private final BannerService bannerService;

    public BannerPublicController(BannerService bannerService) {
        this.bannerService = bannerService;
    }

    @GetMapping
    public R<List<BannerVO>> listActive() {
        return R.ok(bannerService.list());
    }
}
