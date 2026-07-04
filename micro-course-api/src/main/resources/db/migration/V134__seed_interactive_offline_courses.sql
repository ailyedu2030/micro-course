-- V134__seed_interactive_offline_courses.sql
-- 为所有测试教师账号创建互动课件种子数据,保证不论用哪个教师账号登录都能看到数据。
-- 幂等：检查课程标题是否存在,存在则跳过。
-- 覆盖账号: teacher(3), teacher2(4), teacher3(5), teacher4(6), p0_teacher(22)
DO $$
DECLARE
  v_uid     INTEGER;
  v_cid     BIGINT;
  v_chid    BIGINT;
  v_slid    BIGINT;
BEGIN
  FOR v_uid IN SELECT id FROM users WHERE id IN (3,4,5,6,22) ORDER BY id LOOP

    -- Course: INTERACTIVE, title 用教师名做后缀以防冲突
    SELECT id INTO v_cid FROM courses
      WHERE teacher_id=v_uid AND course_type='INTERACTIVE' AND title LIKE '互动课件演示%';
    IF v_cid IS NULL THEN
      INSERT INTO courses (title, course_type, teacher_id, category_id, status, difficulty, description, is_free, version, created_at, updated_at)
      VALUES ('互动课件演示 - ' || v_uid, 'INTERACTIVE', v_uid, 1, 2, 2, '端到端测试互动课件', true, 0, NOW(), NOW())
      RETURNING id INTO v_cid;
    END IF;

    -- Chapter
    SELECT id INTO v_chid FROM course_chapters WHERE course_id=v_cid AND title='章节一';
    IF v_chid IS NULL THEN
      INSERT INTO course_chapters (course_id, title, sort_order, chapter_type, created_at, updated_at, version)
      VALUES (v_cid, '章节一', 1, 'INTERACTIVE', NOW(), NOW(), 0)
      RETURNING id INTO v_chid;
    END IF;

    -- Slide
    SELECT id INTO v_slid FROM course_slides WHERE course_id=v_cid;
    IF v_slid IS NULL THEN
      INSERT INTO course_slides (course_id, file_name, file_url, total_pages, status, created_at, updated_at)
      VALUES (v_cid, 'demo.pdf', '/data/'||v_cid||'/demo.pdf', 2, 2, NOW(), NOW())
      RETURNING id INTO v_slid;
    END IF;

    -- Slide pages (2 per course)
    IF NOT EXISTS (SELECT 1 FROM slide_pages WHERE slide_id=v_slid AND page_number=1) THEN
      INSERT INTO slide_pages (slide_id, course_id, page_number, image_url, thumbnail_url, image_width, image_height, extracted_text, narration_script, narration_status, created_at, updated_at)
      VALUES
        (v_slid, v_cid, 1, '/img/'||v_cid||'/p1.png', '/img/'||v_cid||'/t1.png', 1920, 1080, '第 1 页', '第 1 页讲述稿', 'AI_GENERATED', NOW(), NOW()),
        (v_slid, v_cid, 2, '/img/'||v_cid||'/p2.png', '/img/'||v_cid||'/t2.png', 1920, 1080, '第 2 页', '第 2 页讲述稿', 'PENDING', NOW(), NOW());
    END IF;
  END LOOP;

  RAISE NOTICE 'Seed complete: all teachers have interactive demo courses';
END $$;
