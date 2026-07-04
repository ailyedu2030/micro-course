-- V134__seed_interactive_offline_courses.sql
-- 为 p0_teacher (id=22) 创建端到端自测所需的互动课件/线下章/幻灯片种子数据。
-- 幂等：检查标题是否存在，存在则跳过。
-- PL/pgSQL 保证多表 INSERT 在复杂 FK 依赖下正确执行。
DO $$
DECLARE
  v_user_id   CONSTANT BIGINT := 22;  -- p0_teacher
  v_course_vue   BIGINT;
  v_course_py    BIGINT;
  v_course_eng   BIGINT;
  v_chapter_vue  BIGINT;
  v_chapter_py   BIGINT;
  v_chapter_eng  BIGINT;
  v_slide_vue    BIGINT;
  v_slide_py     BIGINT;
  v_eng_offline  BIGINT;
BEGIN

  -- ============================================================
  -- 课程 (3 门)
  -- ============================================================
  SELECT id INTO v_course_vue  FROM courses WHERE teacher_id=v_user_id AND title='Vue.js 组件化实战';
  SELECT id INTO v_course_py   FROM courses WHERE teacher_id=v_user_id AND title='Python 数据清洗实战';
  SELECT id INTO v_course_eng  FROM courses WHERE teacher_id=v_user_id AND title='英语演讲技巧';

  IF v_course_vue IS NULL THEN
    INSERT INTO courses (title, course_type, teacher_id, category_id, status, difficulty, description, is_free, version, created_at, updated_at)
    VALUES ('Vue.js 组件化实战', 'INTERACTIVE', v_user_id, 1, 2, 2, 'Vue 3 组件化实战', true, 0, NOW() - INTERVAL '40 days', NOW() - INTERVAL '1 day')
    RETURNING id INTO v_course_vue;
  END IF;

  IF v_course_py IS NULL THEN
    INSERT INTO courses (title, course_type, teacher_id, category_id, status, difficulty, description, is_free, version, created_at, updated_at)
    VALUES ('Python 数据清洗实战', 'INTERACTIVE', v_user_id, 5, 2, 2, 'Pandas 数据清洗', true, 0, NOW() - INTERVAL '20 days', NOW() - INTERVAL '2 days')
    RETURNING id INTO v_course_py;
  END IF;

  IF v_course_eng IS NULL THEN
    INSERT INTO courses (title, course_type, teacher_id, category_id, status, difficulty, description, is_free, version, created_at, updated_at)
    VALUES ('英语演讲技巧', 'INTERACTIVE', v_user_id, 1, 0, 1, '草稿演示', true, 0, NOW() - INTERVAL '2 days', NOW() - INTERVAL '1 day')
    RETURNING id INTO v_course_eng;
  END IF;

  -- ============================================================
  -- 章节 (3+1 个)
  -- ============================================================
  SELECT id INTO v_chapter_vue FROM course_chapters WHERE course_id=v_course_vue AND title='Vue 响应式原理';
  IF v_chapter_vue IS NULL THEN
    INSERT INTO course_chapters (course_id, title, sort_order, chapter_type, created_at, updated_at, version)
    VALUES (v_course_vue, 'Vue 响应式原理', 1, 'INTERACTIVE', NOW() - INTERVAL '40 days', NOW() - INTERVAL '40 days', 0)
    RETURNING id INTO v_chapter_vue;
  END IF;

  SELECT id INTO v_chapter_py FROM course_chapters WHERE course_id=v_course_py AND title='Python 数据质量';
  IF v_chapter_py IS NULL THEN
    INSERT INTO course_chapters (course_id, title, sort_order, chapter_type, created_at, updated_at, version)
    VALUES (v_course_py, 'Python 数据质量', 1, 'INTERACTIVE', NOW() - INTERVAL '20 days', NOW() - INTERVAL '20 days', 0)
    RETURNING id INTO v_chapter_py;
  END IF;

  SELECT id INTO v_chapter_eng FROM course_chapters WHERE course_id=v_course_eng AND title='克服紧张心理';
  IF v_chapter_eng IS NULL THEN
    INSERT INTO course_chapters (course_id, title, sort_order, chapter_type, created_at, updated_at, version)
    VALUES (v_course_eng, '克服紧张心理', 1, 'OFFLINE', NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days', 0)
    RETURNING id INTO v_chapter_eng;
  END IF;

  -- ============================================================
  -- 幻灯片 (2 份)
  -- ============================================================
  SELECT id INTO v_slide_vue FROM course_slides WHERE course_id=v_course_vue;
  IF v_slide_vue IS NULL THEN
    INSERT INTO course_slides (course_id, file_name, file_url, total_pages, status, created_at, updated_at)
    VALUES (v_course_vue, 'vue.pdf', '/data/'||v_course_vue||'/vue.pdf', 3, 2, NOW() - INTERVAL '39 days', NOW() - INTERVAL '1 day')
    RETURNING id INTO v_slide_vue;
  END IF;

  SELECT id INTO v_slide_py FROM course_slides WHERE course_id=v_course_py;
  IF v_slide_py IS NULL THEN
    INSERT INTO course_slides (course_id, file_name, file_url, total_pages, status, created_at, updated_at)
    VALUES (v_course_py, 'py.pdf', '/data/'||v_course_py||'/py.pdf', 2, 2, NOW() - INTERVAL '19 days', NOW() - INTERVAL '2 days')
    RETURNING id INTO v_slide_py;
  END IF;

  -- ============================================================
  -- 幻灯片页面 (3+2=5 页)
  -- ============================================================
  IF NOT EXISTS (SELECT 1 FROM slide_pages WHERE slide_id=v_slide_vue AND page_number=1) THEN
    INSERT INTO slide_pages (slide_id, course_id, page_number, image_url, thumbnail_url, image_width, image_height, extracted_text, narration_script, narration_status, created_at, updated_at)
    VALUES
      (v_slide_vue, v_course_vue, 1, '/img/'||v_course_vue||'/p1.png', '/img/'||v_course_vue||'/t1.png', 1920, 1080, 'Proxy 拦截', 'Vue 3 使用 Proxy 实现响应式', 'AI_GENERATED', NOW(), NOW()),
      (v_slide_vue, v_course_vue, 2, '/img/'||v_course_vue||'/p2.png', '/img/'||v_course_vue||'/t2.png', 1920, 1080, 'reactive()', 'reactive 创建响应式对象', 'AUDIO_READY', NOW(), NOW()),
      (v_slide_vue, v_course_vue, 3, '/img/'||v_course_vue||'/p3.png', '/img/'||v_course_vue||'/t3.png', 1920, 1080, 'computed', 'computed 缓存自动追踪', 'PENDING', NOW(), NOW());
  END IF;

  IF NOT EXISTS (SELECT 1 FROM slide_pages WHERE slide_id=v_slide_py AND page_number=1) THEN
    INSERT INTO slide_pages (slide_id, course_id, page_number, image_url, thumbnail_url, image_width, image_height, extracted_text, narration_script, narration_status, created_at, updated_at)
    VALUES
      (v_slide_py, v_course_py, 1, '/img/'||v_course_py||'/p1.png', '/img/'||v_course_py||'/t1.png', 1920, 1080, '数据质量评估', '6 类数据质量问题诊断', 'AUDIO_READY', NOW(), NOW()),
      (v_slide_py, v_course_py, 2, '/img/'||v_course_py||'/p2.png', '/img/'||v_course_py||'/t2.png', 1920, 1080, '缺失值处理', 'fillna 策略选择', 'PENDING', NOW(), NOW());
  END IF;

  -- ============================================================
  -- 线下课排期
  -- ============================================================
  SELECT id INTO v_eng_offline FROM chapter_offline_sessions WHERE chapter_id=v_chapter_eng LIMIT 1;
  IF v_eng_offline IS NULL THEN
    INSERT INTO chapter_offline_sessions (chapter_id, session_date, start_time, end_time, location, sort_order, created_at, updated_at, version)
    VALUES (v_chapter_eng, CURRENT_DATE + 3, '14:00', '16:00', '教学楼 A301', 1, NOW(), NOW(), 0);
  END IF;

  RAISE NOTICE 'Seed complete: courses(vue=%, py=%, eng=%), slides=%, pages=%',
    v_course_vue, v_course_py, v_course_eng, v_slide_vue, (SELECT COUNT(*) FROM slide_pages WHERE course_id IN (v_course_vue, v_course_py));

END $$;