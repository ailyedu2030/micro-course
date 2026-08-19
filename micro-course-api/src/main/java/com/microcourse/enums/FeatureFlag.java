package com.microcourse.enums;

/**
 * Feature flags / 灰度开关 (F10-D2 实现)
 *
 * <p>设计目标：与 {@code gray-release.sh} 脚本对接，从 Redis 读取配置，
 * 业务代码通过 {@link com.microcourse.service.GrayReleaseService} 查询。
 *
 * <h3>添加新 flag 流程</h3>
 * <ol>
 *   <li>在本枚举添加枚举值（小写 snake_case）
 *   <li>在生产 Redis 执行: {@code SET mc:feature:flags '{"flag_name":true}'}
 *   <li>代码调用: {@code grayReleaseService.isFeatureEnabled(FeatureFlag.FLAG_NAME)}
 * </ol>
 *
 * <h3>注意</h3>
 * <ul>
 *   <li>flag 名遵循小写 snake_case，便于 shell/curl 操作
 *   <li>Redis 中以 JSON Map 存储所有 flag: {@code {"flag_name":true,"other":false}}
 *   <li>查询失败（Redis 不可用 / JSON 解析错误）时默认 {@code false}（fail-closed）
 * </ul>
 *
 * @author F10-D2 Phase 9 (2026-08-18)
 */
public enum FeatureFlag {
    /**
     * 微专业班级批量导入功能
     */
    MICRO_SPECIALTY_CLASS_IMPORT,

    /**
     * 新版支付流程（Stripe / 微信支付 集成预留）
     */
    NEW_PAYMENT_FLOW,

    /**
     * AI 讲述稿批量生成
     */
    AI_NARRATION_BATCH_GEN,

    /**
     * 视频转码新版本（V2 编码器）
     */
    VIDEO_TRANSCODE_V2,

    /**
     * 实时通知（WebSocket 推送）实验性开关
     */
    REALTIME_NOTIFICATION_WS
}