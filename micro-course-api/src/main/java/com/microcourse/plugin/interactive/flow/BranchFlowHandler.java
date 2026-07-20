package com.microcourse.plugin.interactive.flow;

import com.microcourse.plugin.interactive.dto.PptFlowDTO;
import org.springframework.stereotype.Component;

/**
 * BranchFlowHandler · 条件分支跳转 (依赖 quiz 答案)
 *
 * 决策逻辑:
 * - flow.lastQuizId == context.lastQuizId
 * - 用户答案与 flow.dependsOnQuizId 对应的"正确分支"匹配 → 跳转 toPageId
 * - 否则 → 不匹配 (返回 null, 让 Engine 退化为 NEXT)
 */
@Component
public class BranchFlowHandler implements FlowHandler {

    @Override
    public String supportedType() {
        return "BRANCH_DEPENDS";
    }

    @Override
    public boolean matches(PptFlowDTO flow, FlowContext context) {
        if (!"BRANCH_DEPENDS".equals(flow.getFlowType())) return false;
        if (flow.getDependsOnQuizId() == null) return false;
        if (context.getLastQuizId() == null) return false;
        return flow.getDependsOnQuizId().equals(context.getLastQuizId());
    }

    @Override
    public Long resolveNextPage(PptFlowDTO flow, FlowContext context) {
        if (Boolean.TRUE.equals(context.getLastQuizAnswer())) {
            return flow.getToPageId();
        }
        return null;  // 答错 → 让 Engine 退化到线性
    }
}