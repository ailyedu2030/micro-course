package com.microcourse.constants;

/**
 * API 通用常量（统一管控 DoS / 越权风险）
 *
 * <p><b>铁律 · 分页上限 100</b>:
 * <ul>
 *   <li>原 {@code @Range(max=10000)} 来自早期"全量导出"场景，混入列表分页，2026-08-15 审查时发现 24 处
 *       单次请求可拉取 10000 条记录，存在 DB 压力 / 内存放大 / 反序列化耗时三重 DoS 风险</li>
 *   <li>用户视角（铁律）：列表页 UI 一次仅展示 ~20 条，100 = 5-10 页，符合人类分页浏览认知</li>
 *   <li>运维视角：100 条 JSON 序列化 < 10KB，内存可预测</li>
 *   <li>"全量导出"应另走 {@code XxxExportController} 异步导出，不应混入列表分页</li>
 * </ul>
 *
 * @author 总工程师
 * @since 2026-08-15
 */
public final class ApiLimits {

    /**
     * Service 层实际生效的最大页大小（超出截断 + warn 日志）。
     *
     * <p>选型说明：100 条可满足所有真实列表 UI 场景（5-10 页）。
     */
    public static final int MAX_PAGE_SIZE = 100;

    /**
     * Controller 层 {@code @Range} 校验上限 — 与前端最大调用 size 对齐，避免契约冲突。
     * 实际生效以 Service 层 {@code MAX_PAGE_SIZE} 截断为准（双层防御）。
     */
    public static final int MAX_REQUEST_SIZE = 10000;

    /**
     * 排行榜最大条数（如课程排行榜、学生排行）
     */
    public static final int MAX_RANKING_SIZE = 100;

    /**
     * ID 列表查询上限（如批量操作）
     */
    public static final int MAX_BATCH_SIZE = 200;

    /**
     * 排名/权重最大 sortOrder 值
     */
    public static final int MAX_SORT_ORDER = 9999;

    /**
     * 评价/反馈最大文本长度
     */
    public static final int MAX_FEEDBACK_LENGTH = 2000;

    /**
     * 单条答案最大字符数（拦截"数万字答案" → DoS）
     */
    public static final int MAX_ANSWER_LENGTH = 5000;

    private ApiLimits() {
        throw new AssertionError("ApiLimits is a constants holder, never instantiate");
    }
}