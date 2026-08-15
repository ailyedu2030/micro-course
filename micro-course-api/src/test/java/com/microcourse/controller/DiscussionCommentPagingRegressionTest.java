package com.microcourse.controller;

import com.microcourse.BaseIntegrationTest;
import com.microcourse.dto.DiscussionCommentVO;
import com.microcourse.dto.PageResult;
import com.microcourse.service.DiscussionCommentService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * P1-C-2026-08-15（R1 审查修复验证）：
 * DiscussionCommentServiceImpl.pagePaged 分页偏移修复 —— 0-based page 需 +1 转 MP 1-based。
 *
 * <p>覆盖：第 2 页应返回第 11-20 条（非重复第一页）。
 */
public class DiscussionCommentPagingRegressionTest extends BaseIntegrationTest {

    @Autowired
    private DiscussionCommentService commentService;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("pagePaged 第 2 页不应返回第一页数据（分页偏移修复）")
    void pagePagedSecondPageDoesNotRepeatFirstPage() {
        Long postId = insertPostWithManyComments();

        PageResult<DiscussionCommentVO> page1 = commentService.pagePaged(postId, 0, 10);
        PageResult<DiscussionCommentVO> page2 = commentService.pagePaged(postId, 1, 10);

        assertNotNull(page1, "第一页不应为 null");
        assertNotNull(page2, "第二页不应为 null");
        assertEquals(10, page1.getItems().size(), "第一页应有 10 条");
        assertEquals(10, page2.getItems().size(), "第二页应有 10 条");
        assertNotEquals(
                page1.getItems().get(0).getId(),
                page2.getItems().get(0).getId(),
                "P1-C 分页偏移：第二页第一条不应与第一页第一条重复");
        assertEquals(20, page1.getTotalElements(), "共 20 条评论");
    }

    private Long insertPostWithManyComments() {
        Long postId = jdbc.queryForObject(
                "INSERT INTO discussion_posts(course_id, title, content, user_id, created_at, updated_at) "
                        + "VALUES (1, 'paging-test', 'content', 7, now(), now()) RETURNING id",
                Long.class);

        for (int i = 1; i <= 20; i++) {
            jdbc.update(
                    "INSERT INTO discussion_comments(post_id, user_id, content, status, created_at, updated_at) "
                            + "VALUES (?, 7, ?, 1, now() + (? || ' minutes')::interval, now())",
                    postId, "comment-" + i, i);
        }
        return postId;
    }
}
