-- V306: PPT 页间逻辑关联表 (用户核心诉求: 页间关联性)
--
-- 三种 flow_type: NEXT (默认线性), BRANCH_DEPENDS (依赖 quiz 结果), SKIP_IF_KNOWN (用户已知则跳过)
-- Rollback 路径: DROP TABLE slide_ppt_flow CASCADE;

CREATE TABLE slide_ppt_flow (
    id BIGSERIAL PRIMARY KEY,
    section_id BIGINT NOT NULL,
    from_page_id BIGINT NOT NULL,
    to_page_id BIGINT,                    -- NULL = 课件结束

    flow_type VARCHAR(20) NOT NULL,
    priority INT NOT NULL DEFAULT 0,
    depends_on_quiz_id BIGINT,
    condition_expression TEXT,
    description VARCHAR(500),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_ppt_flow_section FOREIGN KEY (section_id)
        REFERENCES course_sections(id) ON DELETE CASCADE,
    CONSTRAINT fk_ppt_flow_from FOREIGN KEY (from_page_id)
        REFERENCES slide_ppt_pages(id) ON DELETE CASCADE,
    CONSTRAINT fk_ppt_flow_to FOREIGN KEY (to_page_id)
        REFERENCES slide_ppt_pages(id) ON DELETE SET NULL,
    CONSTRAINT fk_ppt_flow_quiz FOREIGN KEY (depends_on_quiz_id)
        REFERENCES section_quizzes(id) ON DELETE SET NULL,
    CONSTRAINT chk_ppt_flow_type CHECK (flow_type IN ('NEXT','BRANCH_DEPENDS','SKIP_IF_KNOWN'))
);

CREATE INDEX idx_ppt_flow_section_from ON slide_ppt_flow(section_id, from_page_id, priority);

COMMENT ON TABLE slide_ppt_flow IS 'PPT 课件页间跳转逻辑 (V306)';
COMMENT ON COLUMN slide_ppt_flow.flow_type IS 'NEXT=线性, BRANCH_DEPENDS=条件分支, SKIP_IF_KNOWN=智能跳过';
COMMENT ON COLUMN slide_ppt_flow.condition_expression IS 'SKIP 场景: "user_progress >= 0.8"';