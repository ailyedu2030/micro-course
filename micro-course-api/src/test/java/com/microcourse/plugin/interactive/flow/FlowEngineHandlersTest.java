package com.microcourse.plugin.interactive.flow;

import com.microcourse.plugin.interactive.dto.PptFlowDTO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FlowEngineHandlersTest {

    @Test
    void nextHandler_matchesNextType() {
        NextFlowHandler h = new NextFlowHandler();
        PptFlowDTO flow = newFlow("NEXT", 100L);
        FlowContext ctx = new FlowContext(50L, 1L, 0.5, null, null);
        assertTrue(h.matches(flow, ctx));
        assertEquals(100L, h.resolveNextPage(flow, ctx));
    }

    @Test
    void nextHandler_doesNotMatchOtherTypes() {
        NextFlowHandler h = new NextFlowHandler();
        PptFlowDTO flow = newFlow("BRANCH_DEPENDS", 100L);
        FlowContext ctx = new FlowContext(50L, 1L, 0.5, 10L, true);
        assertFalse(h.matches(flow, ctx));
    }

    @Test
    void branchHandler_matchesCorrectQuizAndAnswer() {
        BranchFlowHandler h = new BranchFlowHandler();
        PptFlowDTO flow = newFlow("BRANCH_DEPENDS", 100L);
        flow.setDependsOnQuizId(10L);
        FlowContext ctx = new FlowContext(50L, 1L, 0.5, 10L, true);
        assertTrue(h.matches(flow, ctx));
        assertEquals(100L, h.resolveNextPage(flow, ctx));
    }

    @Test
    void branchHandler_doesNotMatchWrongAnswer() {
        BranchFlowHandler h = new BranchFlowHandler();
        PptFlowDTO flow = newFlow("BRANCH_DEPENDS", 100L);
        flow.setDependsOnQuizId(10L);
        FlowContext ctx = new FlowContext(50L, 1L, 0.5, 10L, false);
        assertTrue(h.matches(flow, ctx));  // quiz id 匹配
        assertNull(h.resolveNextPage(flow, ctx));  // 但答案错 → null 退化
    }

    @Test
    void branchHandler_doesNotMatchDifferentQuiz() {
        BranchFlowHandler h = new BranchFlowHandler();
        PptFlowDTO flow = newFlow("BRANCH_DEPENDS", 100L);
        flow.setDependsOnQuizId(10L);
        FlowContext ctx = new FlowContext(50L, 1L, 0.5, 99L, true);
        assertFalse(h.matches(flow, ctx));
    }

    @Test
    void skipHandler_matchesProgressThreshold() {
        SkipIfKnownFlowHandler h = new SkipIfKnownFlowHandler();
        PptFlowDTO flow = newFlow("SKIP_IF_KNOWN", 100L);
        flow.setConditionExpression("user_progress >= 0.8");
        FlowContext ctxOk = new FlowContext(50L, 1L, 0.85, null, null);
        FlowContext ctxLow = new FlowContext(50L, 1L, 0.5, null, null);
        assertTrue(h.matches(flow, ctxOk));
        assertEquals(100L, h.resolveNextPage(flow, ctxOk));
        assertFalse(h.matches(flow, ctxLow));
    }

    @Test
    void skipHandler_supportsAllOperators() {
        SkipIfKnownFlowHandler h = new SkipIfKnownFlowHandler();
        FlowContext base = new FlowContext(50L, 1L, 0.5, null, null);

        PptFlowDTO ge = newFlow("SKIP_IF_KNOWN", 1L);
        ge.setConditionExpression("user_progress >= 0.5");
        assertTrue(h.matches(ge, base));

        PptFlowDTO gt = newFlow("SKIP_IF_KNOWN", 1L);
        gt.setConditionExpression("user_progress > 0.5");
        assertFalse(h.matches(gt, base));  // 0.5 not > 0.5

        PptFlowDTO le = newFlow("SKIP_IF_KNOWN", 1L);
        le.setConditionExpression("user_progress <= 0.5");
        assertTrue(h.matches(le, base));

        PptFlowDTO lt = newFlow("SKIP_IF_KNOWN", 1L);
        lt.setConditionExpression("user_progress < 0.5");
        assertFalse(h.matches(lt, base));
    }

    @Test
    void skipHandler_rejectsUnknownExpressions() {
        SkipIfKnownFlowHandler h = new SkipIfKnownFlowHandler();
        PptFlowDTO flow = newFlow("SKIP_IF_KNOWN", 1L);
        flow.setConditionExpression("malicious_call()");
        FlowContext ctx = new FlowContext(50L, 1L, 0.5, null, null);
        assertFalse(h.matches(flow, ctx));
    }

    private PptFlowDTO newFlow(String type, Long toPageId) {
        PptFlowDTO f = new PptFlowDTO();
        f.setFlowType(type);
        f.setToPageId(toPageId);
        return f;
    }
}