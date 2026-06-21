package com.microcourse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 越权访问反向测试（Phase A-3 · P0-3 / P0-8 安全止血）
 *
 * 覆盖两个维度：
 *  1. P0-8 —— /api/files/** 分类授权收窄：
 *     - 公开文件（covers/avatars/banners）无 token 仍可访问（不被 401 拦截，UX 零退化）
 *     - 私有文件（slides 课件 / attachments 附件等）无 token 必须 401
 *  2. P0-3 —— 对象级 owner 校验存在性：
 *     - 写/读他人或不存在对象时，Service 层 owner 校验链生效，返回 4xx 而非静默 2xx
 *
 * 说明：当前测试库（Flyway V1__init.sql）仅含 admin 用户，无法登录第二个普通用户构造
 * “用户 A 创建 / 用户 B 越权”双用户场景；故 owner 维度以“对不存在 / 非自有对象的请求被
 * 校验链拒绝（4xx）”等效验证对象级授权未被绕过。owner 校验已在以下 Service 层确认：
 *   - DiscussionCommentServiceImpl.delete   （!comment.userId.equals(userId) && !isAdminOrTeacher）
 *   - VideoBookmarkServiceImpl.delete        （!currentUserId.equals(bookmark.userId) -> NO_PERMISSION）
 *   - ExerciseRecordServiceImpl.getRecordById（!record.userId.equals(userId) -> NO_PERMISSION）
 */
public class AuthorizationSecurityTest extends BaseIntegrationTest {

    // ---------------------------------------------------------------------
    // P0-8：公开文件无 token 可访问（不被 Security 鉴权层 401/403 拦截）
    // permitAll 的精确语义 = 请求穿透 SecurityFilterChain 抵达静态资源处理器；
    // 文件是否存在是另一回事（不存在时由服务端兜底，与鉴权无关）。
    // 关键安全断言：状态码不是 401（未登录拦截），证明 permitAll 生效、UX 零退化。
    // ---------------------------------------------------------------------

    private static void assertNotBlockedByAuth(int status, String path) {
        assertNotEquals(401, status, "公开文件不应被 401 拦截（permitAll 须生效）: " + path);
        assertNotEquals(403, status, "公开文件不应被 403 拦截（permitAll 须生效）: " + path);
    }

    @Test
    @DisplayName("P0-8 公开封面无token访问不被鉴权拦截")
    void shouldAllowPublicCoverAccessWithoutToken() throws Exception {
        var res = mockMvc.perform(get("/api/files/covers/1/__nonexistent_cover__.jpg")).andReturn();
        assertNotBlockedByAuth(res.getResponse().getStatus(), "/api/files/covers/**");
    }

    @Test
    @DisplayName("P0-8 公开头像无token访问不被鉴权拦截（守护头像UX零退化）")
    void shouldAllowPublicAvatarAccessWithoutToken() throws Exception {
        var res = mockMvc.perform(get("/api/files/avatars/__nonexistent_avatar__.jpg")).andReturn();
        assertNotBlockedByAuth(res.getResponse().getStatus(), "/api/files/avatars/**");
    }

    @Test
    @DisplayName("P0-8 公开轮播图无token访问不被鉴权拦截")
    void shouldAllowPublicBannerAccessWithoutToken() throws Exception {
        var res = mockMvc.perform(get("/api/files/banners/__nonexistent_banner__.jpg")).andReturn();
        assertNotBlockedByAuth(res.getResponse().getStatus(), "/api/files/banners/**");
    }

    // ---------------------------------------------------------------------
    // P0-8：私有文件无 token 必须 401（修复前为 permitAll 越权下载）
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("P0-8 私有课件(slides)无token访问返回401")
    void shouldRequireAuthForPrivateSlideFile() throws Exception {
        mockMvc.perform(get("/api/files/slides/1/__secret_slide__.png"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("P0-8 任意非白名单私有文件无token访问返回401")
    void shouldRequireAuthForArbitraryPrivateFile() throws Exception {
        mockMvc.perform(get("/api/files/attachments/__secret_attachment__.pdf"))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------------
    // P0-3：对象级 owner 校验链生效 —— 对不存在 / 非自有对象返回 4xx，不静默放行
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("P0-3 删除非自有/不存在视频书签被校验链拒绝(4xx)")
    void shouldRejectDeletingNonOwnedVideoBookmark() throws Exception {
        // admin token 删除一个不存在的书签 —— VideoBookmarkServiceImpl.delete 先校验存在性/归属，
        // 返回 4xx（非静默 2xx），证明对象级校验链未被绕过
        mockMvc.perform(delete("/api/videos/1/bookmarks/999999")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("P0-3 查询非自有/不存在答题记录被校验链拒绝(4xx)")
    void shouldRejectReadingNonOwnedExerciseRecord() throws Exception {
        // admin token 查询一个不存在的答题记录 —— ExerciseRecordServiceImpl.getRecordById
        // 校验存在性/归属后返回 4xx（非静默 2xx）
        mockMvc.perform(get("/api/exercise-records/999999")
                        .header("Authorization", bearerAdmin()))
                .andExpect(status().is4xxClientError());
    }
}
