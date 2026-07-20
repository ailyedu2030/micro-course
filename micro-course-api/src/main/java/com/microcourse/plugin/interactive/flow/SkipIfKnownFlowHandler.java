package com.microcourse.plugin.interactive.flow;

import com.microcourse.plugin.interactive.dto.PptFlowDTO;
import org.springframework.stereotype.Component;

/**
 * SkipIfKnownFlowHandler · 智能跳过 (用户进度 ≥ 阈值)
 *
 * condition_expression 格式: "user_progress >= 0.8" 或 "user_progress > 0.5"
 * 匹配成功且条件成立 → 跳转 toPageId (跳过中间页)
 */
@Component
public class SkipIfKnownFlowHandler implements FlowHandler {

    @Override
    public String supportedType() {
        return "SKIP_IF_KNOWN";
    }

    @Override
    public boolean matches(PptFlowDTO flow, FlowContext context) {
        if (!"SKIP_IF_KNOWN".equals(flow.getFlowType())) return false;
        if (flow.getConditionExpression() == null || flow.getConditionExpression().isBlank()) {
            return false;
        }
        return evaluate(flow.getConditionExpression(), context);
    }

    @Override
    public Long resolveNextPage(PptFlowDTO flow, FlowContext context) {
        return matches(flow, context) ? flow.getToPageId() : null;
    }

    /**
     * 评估条件表达式 (简化版: 仅支持 user_progress >= / > N)
     * 生产环境应替换为安全的表达式引擎 (aviator / spEL 沙箱)
     */
    private boolean evaluate(String expr, FlowContext ctx) {
        Double progress = ctx.getUserProgress();
        if (progress == null) return false;
        String e = expr.trim();
        if (e.startsWith("user_progress >= ")) {
            double threshold = parseThreshold(e, 17);
            return progress >= threshold;
        }
        if (e.startsWith("user_progress > ")) {
            double threshold = parseThreshold(e, 16);
            return progress > threshold;
        }
        if (e.startsWith("user_progress <= ")) {
            double threshold = parseThreshold(e, 17);
            return progress <= threshold;
        }
        if (e.startsWith("user_progress < ")) {
            double threshold = parseThreshold(e, 16);
            return progress < threshold;
        }
        return false;
    }

    private double parseThreshold(String expr, int prefixLen) {
        try {
            return Double.parseDouble(expr.substring(prefixLen).trim());
        } catch (Exception ex) {
            return Double.MAX_VALUE;
        }
    }
}