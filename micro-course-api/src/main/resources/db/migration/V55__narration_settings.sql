-- V55: 讲述稿生成设置表
-- 允许教师自定义 AI 讲述稿的演讲人身份、受众、风格、总时长
-- 幂等：表已存在则跳过 CREATE，仅补充缺失列

CREATE TABLE IF NOT EXISTS narration_settings (
    id BIGSERIAL PRIMARY KEY,
    course_id BIGINT NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    speaker_identity VARCHAR(200) NOT NULL DEFAULT '大学教师',
    target_audience VARCHAR(200) NOT NULL DEFAULT '学生',
    speaking_style VARCHAR(200) NOT NULL DEFAULT '亲切自然，像在课堂上讲课',
    total_duration_minutes INT NOT NULL DEFAULT 15,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_narration_settings_course UNIQUE (course_id)
);

-- 兼容旧列名：如果表已存在且列名为 duration_minutes，则重命名为 total_duration_minutes
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'narration_settings' AND column_name = 'duration_minutes') THEN
        ALTER TABLE narration_settings RENAME COLUMN duration_minutes TO total_duration_minutes;
        ALTER TABLE narration_settings ALTER COLUMN total_duration_minutes SET DEFAULT 15;
    END IF;
END $$;

COMMENT ON TABLE narration_settings IS '讲述稿生成设置';
COMMENT ON COLUMN narration_settings.speaker_identity IS '演讲人身份，如：大学英语教师、专业讲师';
COMMENT ON COLUMN narration_settings.target_audience IS '目标受众，如：专升本学生、大一新生';
COMMENT ON COLUMN narration_settings.speaking_style IS '演讲风格要求，如：亲切自然、专业严谨';
COMMENT ON COLUMN narration_settings.total_duration_minutes IS '总讲述时长（分钟），AI 将根据各页内容重要性自动分配';
