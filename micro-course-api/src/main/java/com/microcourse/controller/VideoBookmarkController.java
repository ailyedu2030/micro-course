package com.microcourse.controller;

import com.microcourse.dto.R;
import com.microcourse.dto.VideoBookmarkCreateRequest;
import com.microcourse.dto.VideoBookmarkVO;
import com.microcourse.service.VideoBookmarkService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 视频书签 CRUD
 *
 * P0-5 修复：VideoBookmark 有 Entity 无 Service/Controller
 */
@RestController
@RequestMapping("/api/videos/{videoId}/bookmarks")
public class VideoBookmarkController {

    private final VideoBookmarkService bookmarkService;

    public VideoBookmarkController(VideoBookmarkService bookmarkService) {
        this.bookmarkService = bookmarkService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public R<List<VideoBookmarkVO>> list(@PathVariable Long videoId) {
        return R.ok(bookmarkService.listByVideoId(videoId));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public R<VideoBookmarkVO> create(@PathVariable Long videoId,
                                     @Valid @RequestBody VideoBookmarkCreateRequest request) {
        return R.ok(bookmarkService.create(videoId, request));
    }

    // P0-3 对象级授权：@PreAuthorize 仅做认证兜底；owner 校验下沉至
    // VideoBookmarkServiceImpl.delete（!currentUserId.equals(bookmark.userId) → NO_PERMISSION/403）
    @DeleteMapping("/{bookmarkId}")
    @PreAuthorize("isAuthenticated()")
    public R<Void> delete(@PathVariable Long videoId,
                          @PathVariable Long bookmarkId) {
        bookmarkService.delete(videoId, bookmarkId);
        return R.ok();
    }
}
