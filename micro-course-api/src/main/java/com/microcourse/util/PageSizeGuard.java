package com.microcourse.util;

import com.microcourse.constants.ApiLimits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 分页 size 硬限工具（Service 层统一截断）
 *
 * <p>P1-I-2026-08-15 · 双层防御：
 * <ul>
 *   <li>第 1 层 — Controller {@code @Range} 校验契约（{@code ApiLimits.MAX_REQUEST_SIZE=10000}，兼容现有前端调用）</li>
 *   <li>第 2 层 — 本工具在 Service 层硬截到 {@code ApiLimits.MAX_PAGE_SIZE=100}，超出记 warn 日志</li>
 * </ul>
 *
 * <p><b>为什么不直接收紧到 100：</b>前端 27 处使用 size=999/1000 "拉全量"，若直接收紧会批量失败
 * （已有 26 处改为 size=100，1 处改用 fetchAllPages 循环）。本工具确保即使前端绕过 size 限制
 * 拉到全量（如 service 直接用 list()），也由 Service 层截断避免 OOM。
 *
 * @author 总工程师
 * @since 2026-08-15
 */
public final class PageSizeGuard {

    private static final Logger LOG = LoggerFactory.getLogger(PageSizeGuard.class);

    /**
     * 截断 List 至 ApiLimits.MAX_PAGE_SIZE，超出记 warn 日志。
     *
     * @param list  原始列表（可为 null）
     * @param scene 调用场景描述（用于日志定位）
     * @return 截断后的列表（保留原顺序前 N 条）
     */
    public static <T> List<T> cap(List<T> list, String scene) {
        if (list == null) return null;
        if (list.size() <= ApiLimits.MAX_PAGE_SIZE) return list;
        LOG.warn("[PageSizeGuard] scene={} 原始 size={} 超过 MAX_PAGE_SIZE={}，已截断；调用方应改用 PageResult 分页",
            scene, list.size(), ApiLimits.MAX_PAGE_SIZE);
        return new java.util.ArrayList<>(list.subList(0, ApiLimits.MAX_PAGE_SIZE));
    }

    /**
     * 校验 size 参数，超出则夹紧（clamp）到上限 + warn 日志。
     *
     * <p>用于 Service 方法入口处将 size 夹到合理范围。
     */
    public static int clampSize(int requested) {
        if (requested <= 0) return 1;
        if (requested > ApiLimits.MAX_PAGE_SIZE) {
            LOG.warn("[PageSizeGuard] requested size={} 超过 MAX_PAGE_SIZE={}，已夹紧", requested, ApiLimits.MAX_PAGE_SIZE);
            return ApiLimits.MAX_PAGE_SIZE;
        }
        return requested;
    }

    private PageSizeGuard() {
        throw new AssertionError("PageSizeGuard is a utility class, never instantiate");
    }
}