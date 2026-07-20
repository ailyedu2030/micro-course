package com.microcourse.plugin.interactive.flow;

import com.microcourse.plugin.interactive.dto.PptFlowDTO;
import org.springframework.stereotype.Component;

/**
 * NextFlowHandler · 线性跳转 (page_number + 1)
 *
 * 兜底: 任何 flow 未匹配时 FlowEngine 退化为线性 page+1.
 */
@Component
public class NextFlowHandler implements FlowHandler {

    @Override
    public String supportedType() {
        return "NEXT";
    }

    @Override
    public boolean matches(PptFlowDTO flow, FlowContext context) {
        return "NEXT".equals(flow.getFlowType());
    }

    @Override
    public Long resolveNextPage(PptFlowDTO flow, FlowContext context) {
        return flow.getToPageId();
    }
}