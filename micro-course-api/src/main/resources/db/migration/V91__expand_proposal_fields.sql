-- V91__expand_proposal_fields.sql
-- Phase 15: 扩展 micro_specialty_proposals 表支持整理收纳微专业申请表系统
-- 依赖：V84 已创建该表

ALTER TABLE micro_specialty_proposals
    -- 类型（固定选项）
    ADD COLUMN IF NOT EXISTS type                      VARCHAR(20)  NOT NULL DEFAULT '急需紧缺型',

    -- 面向对象
    ADD COLUMN IF NOT EXISTS target_audience           VARCHAR(200),

    -- 面向学科及专业
    ADD COLUMN IF NOT EXISTS target_disciplines        VARCHAR(500),

    -- 总学分
    ADD COLUMN IF NOT EXISTS total_credits             INTEGER      DEFAULT 0,

    -- 课程门数
    ADD COLUMN IF NOT EXISTS course_count              INTEGER      DEFAULT 0,

    -- 共建高校
    ADD COLUMN IF NOT EXISTS co_build_universities     VARCHAR(500),

    -- 拟共享高校
    ADD COLUMN IF NOT EXISTS planned_share_universities VARCHAR(500),

    -- 招生名额
    ADD COLUMN IF NOT EXISTS enrollment_quota          INTEGER      DEFAULT 0,

    -- 成班人数
    ADD COLUMN IF NOT EXISTS class_size                INTEGER      DEFAULT 0,

    -- 开课时间
    ADD COLUMN IF NOT EXISTS start_date                TIMESTAMP,

    -- 学制
    ADD COLUMN IF NOT EXISTS duration                  VARCHAR(50),

    -- 是否产教融合
    ADD COLUMN IF NOT EXISTS is_industry_academic      BOOLEAN      DEFAULT FALSE,

    -- 产教合作单位
    ADD COLUMN IF NOT EXISTS industry_partners         VARCHAR(500),

    -- 微专业介绍（富文本）
    ADD COLUMN IF NOT EXISTS introduction              TEXT,

    -- 社会需求及就业前景分析
    ADD COLUMN IF NOT EXISTS market_demand_analysis    TEXT,

    -- 微专业简介
    ADD COLUMN IF NOT EXISTS specialty_overview        TEXT,

    -- 课程体系设置情况
    ADD COLUMN IF NOT EXISTS curriculum_design         TEXT,

    -- 建设条件保障
    ADD COLUMN IF NOT EXISTS construction_guarantee    TEXT,

    -- 专业负责人姓名
    ADD COLUMN IF NOT EXISTS lead_name                 VARCHAR(50),

    -- 专业负责人职称
    ADD COLUMN IF NOT EXISTS lead_title                VARCHAR(50),

    -- 专业负责人职务
    ADD COLUMN IF NOT EXISTS lead_position             VARCHAR(100),

    -- 专业负责人联系电话
    ADD COLUMN IF NOT EXISTS lead_phone                VARCHAR(20),

    -- 主要研究方向
    ADD COLUMN IF NOT EXISTS lead_research_direction   TEXT,

    -- 承担主要任务与主讲课程
    ADD COLUMN IF NOT EXISTS lead_main_tasks           TEXT,

    -- 联系电话（表头用）
    ADD COLUMN IF NOT EXISTS contact_phone             VARCHAR(20),

    -- 申请时间
    ADD COLUMN IF NOT EXISTS apply_date                TIMESTAMP;

-- 修改 status 默认值为 DRAFT（仅对新行，不改旧数据）
ALTER TABLE micro_specialty_proposals ALTER COLUMN status SET DEFAULT 'DRAFT';

-- 新增索引
CREATE INDEX IF NOT EXISTS idx_msp_type       ON micro_specialty_proposals(type);
CREATE INDEX IF NOT EXISTS idx_msp_apply_date ON micro_specialty_proposals(apply_date);
