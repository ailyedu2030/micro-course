package com.microcourse.plugin.interactive.flow;

import com.microcourse.plugin.interactive.dto.PptFlowDTO;
import com.microcourse.plugin.interactive.entity.SlidePptFlow;
import com.microcourse.plugin.interactive.mapper.SlidePptFlowMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * FlowEngine · PPT 页间跳转决策引擎 (spec 4.1 / flow/)
 *
 * 决策流程:
 * 1. 拉取 section + fromPageId 的所有 flow 规则 (按 priority ASC)
 * 2. 遍历 handler 链, 找到首个匹配
 * 3. 返回下一个 pageId (null 表示结束)
 *
 * 兜底: 任何规则未匹配 → 返回 null, 调用方应退化为 page_number+1
 */
@Component
public class FlowEngine {

    private static final Logger log = LoggerFactory.getLogger(FlowEngine.class);

    private final SlidePptFlowMapper flowMapper;
    private final List<FlowHandler> handlers;

    @Autowired
    public FlowEngine(SlidePptFlowMapper flowMapper, List<FlowHandler> handlers) {
        this.flowMapper = flowMapper;
        this.handlers = handlers;
    }

    /**
     * 决策下一个 pageId
     * @return nextPageId or null (表示结束, 或调用方应退化线性)
     */
    public Long decideNextPage(Long sectionId, FlowContext context) {
        if (sectionId == null || context == null || context.getCurrentPageId() == null) {
            return null;
        }
        List<SlidePptFlow> entities = flowMapper.listBySectionAndFromPage(sectionId, context.getCurrentPageId());
        List<PptFlowDTO> rules = entities == null ? List.of() : toDtoList(entities);
        if (rules.isEmpty()) {
            log.debug("[FlowEngine] no rules for section={} fromPage={}", sectionId, context.getCurrentPageId());
            return null;
        }
        for (PptFlowDTO rule : rules) {
            for (FlowHandler h : handlers) {
                if (h.matches(rule, context)) {
                    Long next = h.resolveNextPage(rule, context);
                    log.info("[FlowEngine] MATCH type={} rule={} → nextPage={}",
                            rule.getFlowType(), rule.getId(), next);
                    return next;
                }
            }
        }
        log.debug("[FlowEngine] no handler matched, return null");
        return null;
    }

    public List<PptFlowDTO> listFlows(Long sectionId) {
        List<SlidePptFlow> entities = flowMapper.listBySection(sectionId);
        return entities == null ? List.of() : toDtoList(entities);
    }

    private List<PptFlowDTO> toDtoList(List<SlidePptFlow> entities) {
        List<PptFlowDTO> out = new ArrayList<>(entities.size());
        for (SlidePptFlow e : entities) {
            PptFlowDTO d = new PptFlowDTO();
            d.setId(e.getId());
            d.setSectionId(e.getSectionId());
            d.setFromPageId(e.getFromPageId());
            d.setToPageId(e.getToPageId());
            d.setFlowType(e.getFlowType());
            d.setPriority(e.getPriority());
            d.setDependsOnQuizId(e.getDependsOnQuizId());
            d.setConditionExpression(e.getConditionExpression());
            d.setDescription(e.getDescription());
            d.setCreatedAt(e.getCreatedAt());
            d.setUpdatedAt(e.getUpdatedAt());
            out.add(d);
        }
        return out;
    }
}