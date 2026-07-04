#!/bin/bash
# seed-interactive-data.sh
# 互动课件端到端自测种子数据
# 使用: bash scripts/seed-interactive-data.sh
# 说明: 为 p0_teacher (id=22) 创建 3 门 INTERACTIVE 课程 + 2 份幻灯片 + 排期 + 教学班
set -euo pipefail

DB_URL="${DB_URL:-jdbc:postgresql://localhost:5432/micro_course}"
DB_USER="${DB_USER:-postgres}"
DB_PASS="${DB_PASSWORD:-postgres}"

echo "=== 插入互动课件种子数据 ==="
PGPASSWORD="$DB_PASS" psql -h localhost -U "$DB_USER" -d micro_course -f - <<'ENDSQL'
BEGIN;

DELETE FROM enrollments WHERE user_id = 22;
DELETE FROM course_favorites WHERE user_id = 22;
DELETE FROM teaching_classes WHERE teacher_id = 22;
DELETE FROM chapter_offline_sessions WHERE chapter_id IN (SELECT id FROM course_chapters WHERE course_id IN (SELECT id FROM courses WHERE teacher_id=22));
DELETE FROM slide_pages WHERE course_id IN (SELECT id FROM courses WHERE teacher_id=22);
DELETE FROM course_slides WHERE course_id IN (SELECT id FROM courses WHERE teacher_id=22);
DELETE FROM course_chapters WHERE course_id IN (SELECT id FROM courses WHERE teacher_id=22);
DELETE FROM courses WHERE teacher_id = 22;

INSERT INTO courses (title, course_type, teacher_id, category_id, status, difficulty, description, is_free, version, created_at, updated_at)
VALUES ('Vue.js 组件化实战', 'INTERACTIVE', 22, 1, 2, 2, 'Vue 3 组件化实战', true, 0, NOW() - INTERVAL '40 days', NOW() - INTERVAL '1 day');

INSERT INTO courses (title, course_type, teacher_id, category_id, status, difficulty, description, is_free, version, created_at, updated_at)
VALUES ('Python 数据清洗实战', 'INTERACTIVE', 22, 5, 2, 2, 'Pandas 数据清洗', true, 0, NOW() - INTERVAL '20 days', NOW() - INTERVAL '2 days');

INSERT INTO courses (title, course_type, teacher_id, category_id, status, difficulty, description, is_free, version, created_at, updated_at)
VALUES ('英语演讲技巧', 'INTERACTIVE', 22, 1, 0, 1, '草稿演示', true, 0, NOW() - INTERVAL '2 days', NOW() - INTERVAL '1 day');

INSERT INTO course_chapters (course_id, title, sort_order, chapter_type, created_at, updated_at, version)
  SELECT id, 'Vue 响应式原理', 1, 'INTERACTIVE', NOW() - INTERVAL '40 days', NOW() - INTERVAL '40 days', 0 FROM courses WHERE title='Vue.js 组件化实战';
INSERT INTO course_chapters (course_id, title, sort_order, chapter_type, created_at, updated_at, version)
  SELECT id, 'Python 数据质量', 1, 'INTERACTIVE', NOW() - INTERVAL '20 days', NOW() - INTERVAL '20 days', 0 FROM courses WHERE title='Python 数据清洗实战';
INSERT INTO course_chapters (course_id, title, sort_order, chapter_type, created_at, updated_at, version)
  SELECT id, '克服紧张心理', 1, 'OFFLINE', NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days', 0 FROM courses WHERE title='英语演讲技巧';

INSERT INTO course_slides (course_id, file_name, file_url, total_pages, status, created_at, updated_at)
  SELECT id, 'vue.pdf', '/data/'||id||'/vue.pdf', 3, 2, NOW() - INTERVAL '39 days', NOW() - INTERVAL '1 day' FROM courses WHERE title='Vue.js 组件化实战';
INSERT INTO course_slides (course_id, file_name, file_url, total_pages, status, created_at, updated_at)
  SELECT id, 'py.pdf', '/data/'||id||'/py.pdf', 2, 2, NOW() - INTERVAL '19 days', NOW() - INTERVAL '2 days' FROM courses WHERE title='Python 数据清洗实战';

INSERT INTO slide_pages (slide_id, course_id, page_number, image_url, thumbnail_url, image_width, image_height, extracted_text, narration_script, narration_status, created_at, updated_at)
SELECT cs.id, cs.course_id, v.pg_num, '/img/'||cs.course_id||'/p'||v.pg_num||'.png', '/img/'||cs.course_id||'/t'||v.pg_num||'.png', 1920, 1080, v.txt, v.script, v.status, NOW(), NOW()
FROM course_slides cs JOIN courses c ON c.id=cs.course_id,
(VALUES (1,'Proxy 拦截','Vue 3 使用 Proxy','AI_GENERATED'),(2,'reactive()','reactive 创建响应式','AUDIO_READY'),(3,'computed 缓存','computed 自动追踪','PENDING')) v(pg_num,txt,script,status)
WHERE c.title='Vue.js 组件化实战';

INSERT INTO slide_pages (slide_id, course_id, page_number, image_url, thumbnail_url, image_width, image_height, extracted_text, narration_script, narration_status, created_at, updated_at)
SELECT cs.id, cs.course_id, v.pg_num, '/img/'||cs.course_id||'/p'||v.pg_num||'.png', '/img/'||cs.course_id||'/t'||v.pg_num||'.png', 1920, 1080, v.txt, v.script, v.status, NOW(), NOW()
FROM course_slides cs JOIN courses c ON c.id=cs.course_id,
(VALUES (1,'数据质量评估','6 类数据质量问题诊断','AUDIO_READY'),(2,'缺失值处理','fillna 策略选择','PENDING')) v(pg_num,txt,script,status)
WHERE c.title='Python 数据清洗实战';

INSERT INTO chapter_offline_sessions (chapter_id, session_date, start_time, end_time, location, sort_order, created_at, updated_at, version)
SELECT cc.id, CURRENT_DATE + 3, '14:00', '16:00', '教学楼 A301', 1, NOW(), NOW(), 0
FROM course_chapters cc JOIN courses c ON c.id=cc.course_id WHERE c.title='英语演讲技巧' AND cc.title='克服紧张心理';

COMMIT;
ENDSQL

echo "✅ 种子数据插入完成"
