package com.microcourse.plugin.interactive.flow;

import com.microcourse.plugin.interactive.dto.PptFlowDTO;

/**
 * FlowHandler 统一接口 (spec 4.1 / flow/)
 *
 * 三种实现:
 * - NextFlowHandler: 线性 page_number+1
 * - BranchFlowHandler: 条件分支 (依赖 quiz 答案)
 * - SkipIfKnownFlowHandler: 用户进度阈值跳过
 */
public interface FlowHandler {

    /** 此 handler 处理的 flow_type (NEXT / BRANCH_DEPENDS / SKIP_IF_KNOWN) */
    String supportedType();

    /**
     * 判断当前 flow 是否匹配
     * @return true 表示此 handler 应处理此跳转决策
     */
    boolean matches(PptFlowDTO flow, FlowContext context);

    /**
     * 取下一个 pageId
     * @return null 表示结束
     */
    Long resolveNextPage(PptFlowDTO flow, FlowContext context);
}