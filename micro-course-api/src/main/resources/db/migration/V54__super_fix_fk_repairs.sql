-- ===================================================================
-- V54: Super-Fix FK & 索引修复迁移
-- 
-- 审计发现修复 (基于 .audit-cache/findings/audit-all-migrations.json)
--   F-002: badges.user_id 缺少 FK → users(id)
--   F-003: certificates.user_id 缺少 FK → users(id)
--   F-004: certificates.course_id 缺少 FK → courses(id)
--   F-005: grades.course_id / user_id 缺少 FK
--   F-006: grades.exercise_id 缺少 FK
--   F-007: grades.graded_by 缺少 FK
--   F-016: question_tag_relations 缺少 ON DELETE CASCADE
--   F-017: user_follows 缺少 ON DELETE CASCADE
--   F-018: score_histories 缺少 ON DELETE CASCADE
--   F-019: course_notes 缺少 ON DELETE CASCADE
--   F-020: video_bookmarks 缺少 ON DELETE CASCADE
--   F-021: attachments.uploader_id 缺少 FK
-- ===================================================================

-- F-002: badges.user_id → users(id) ON DELETE CASCADE
ALTER TABLE badges DROP CONSTRAINT IF EXISTS badges_user_id_fkey;
ALTER TABLE badges ADD CONSTRAINT fk_badges_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

-- F-003/F-004: certificates.user_id/course_id → users/courses ON DELETE CASCADE
ALTER TABLE certificates DROP CONSTRAINT IF EXISTS certificates_user_id_fkey;
ALTER TABLE certificates ADD CONSTRAINT fk_certificates_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
ALTER TABLE certificates DROP CONSTRAINT IF EXISTS certificates_course_id_fkey;
ALTER TABLE certificates ADD CONSTRAINT fk_certificates_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE;

-- F-005: grades.course_id/user_id → courses/users ON DELETE CASCADE
ALTER TABLE grades DROP CONSTRAINT IF EXISTS grades_course_id_fkey;
ALTER TABLE grades DROP CONSTRAINT IF EXISTS fk_grades_course;
ALTER TABLE grades ADD CONSTRAINT fk_grades_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE;
ALTER TABLE grades DROP CONSTRAINT IF EXISTS grades_user_id_fkey;
ALTER TABLE grades DROP CONSTRAINT IF EXISTS fk_grades_user;
ALTER TABLE grades ADD CONSTRAINT fk_grades_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

-- F-006: grades.exercise_id → exercises(id) ON DELETE SET NULL
ALTER TABLE grades DROP CONSTRAINT IF EXISTS grades_exercise_id_fkey;
ALTER TABLE grades DROP CONSTRAINT IF EXISTS fk_grades_exercise;
ALTER TABLE grades ADD CONSTRAINT fk_grades_exercise FOREIGN KEY (exercise_id) REFERENCES exercises(id) ON DELETE SET NULL;

-- F-007: grades.graded_by → users(id) ON DELETE SET NULL
ALTER TABLE grades DROP CONSTRAINT IF EXISTS grades_graded_by_fkey;
ALTER TABLE grades DROP CONSTRAINT IF EXISTS fk_grades_graded_by;
ALTER TABLE grades ADD CONSTRAINT fk_grades_graded_by FOREIGN KEY (graded_by) REFERENCES users(id) ON DELETE SET NULL;

-- F-016: question_tag_relations ON DELETE CASCADE
ALTER TABLE question_tag_relations DROP CONSTRAINT IF EXISTS question_tag_relations_question_id_fkey;
ALTER TABLE question_tag_relations DROP CONSTRAINT IF EXISTS fk_qtr_question;
ALTER TABLE question_tag_relations ADD CONSTRAINT fk_qtr_question FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE CASCADE;
ALTER TABLE question_tag_relations DROP CONSTRAINT IF EXISTS question_tag_relations_tag_id_fkey;
ALTER TABLE question_tag_relations DROP CONSTRAINT IF EXISTS fk_qtr_tag;
ALTER TABLE question_tag_relations ADD CONSTRAINT fk_qtr_tag FOREIGN KEY (tag_id) REFERENCES tags(id) ON DELETE CASCADE;

-- F-017: user_follows ON DELETE CASCADE
ALTER TABLE user_follows DROP CONSTRAINT IF EXISTS user_follows_follower_id_fkey;
ALTER TABLE user_follows DROP CONSTRAINT IF EXISTS fk_uf_follower;
ALTER TABLE user_follows ADD CONSTRAINT fk_uf_follower FOREIGN KEY (follower_id) REFERENCES users(id) ON DELETE CASCADE;
ALTER TABLE user_follows DROP CONSTRAINT IF EXISTS user_follows_following_id_fkey;
ALTER TABLE user_follows DROP CONSTRAINT IF EXISTS fk_uf_following;
ALTER TABLE user_follows ADD CONSTRAINT fk_uf_following FOREIGN KEY (following_id) REFERENCES users(id) ON DELETE CASCADE;

-- F-018: score_histories ON DELETE CASCADE
ALTER TABLE score_histories DROP CONSTRAINT IF EXISTS score_histories_enrollment_id_fkey;
ALTER TABLE score_histories DROP CONSTRAINT IF EXISTS fk_sh_enrollment;
ALTER TABLE score_histories ADD CONSTRAINT fk_sh_enrollment FOREIGN KEY (enrollment_id) REFERENCES enrollments(id) ON DELETE CASCADE;

-- F-019: course_notes ON DELETE CASCADE
ALTER TABLE course_notes DROP CONSTRAINT IF EXISTS course_notes_user_id_fkey;
ALTER TABLE course_notes DROP CONSTRAINT IF EXISTS fk_cn_user;
ALTER TABLE course_notes ADD CONSTRAINT fk_cn_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
ALTER TABLE course_notes DROP CONSTRAINT IF EXISTS course_notes_course_id_fkey;
ALTER TABLE course_notes DROP CONSTRAINT IF EXISTS fk_cn_course;
ALTER TABLE course_notes ADD CONSTRAINT fk_cn_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE;

-- F-020: video_bookmarks ON DELETE CASCADE
ALTER TABLE video_bookmarks DROP CONSTRAINT IF EXISTS video_bookmarks_user_id_fkey;
ALTER TABLE video_bookmarks DROP CONSTRAINT IF EXISTS fk_vb_user;
ALTER TABLE video_bookmarks ADD CONSTRAINT fk_vb_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
ALTER TABLE video_bookmarks DROP CONSTRAINT IF EXISTS video_bookmarks_video_id_fkey;
ALTER TABLE video_bookmarks DROP CONSTRAINT IF EXISTS fk_vb_video;
ALTER TABLE video_bookmarks ADD CONSTRAINT fk_vb_video FOREIGN KEY (video_id) REFERENCES videos(id) ON DELETE CASCADE;

-- F-021: attachments.uploader_id → users(id) ON DELETE SET NULL
ALTER TABLE attachments DROP CONSTRAINT IF EXISTS attachments_uploader_id_fkey;
ALTER TABLE attachments DROP CONSTRAINT IF EXISTS fk_att_uploader;
ALTER TABLE attachments ADD CONSTRAINT fk_att_uploader FOREIGN KEY (uploader_id) REFERENCES users(id) ON DELETE SET NULL;

-- V49 slide_pages.slide_id ON DELETE CASCADE (DF-NEW-3 修复)
ALTER TABLE slide_pages DROP CONSTRAINT IF EXISTS slide_pages_slide_id_fkey;
ALTER TABLE slide_pages DROP CONSTRAINT IF EXISTS fk_sp_slide;
ALTER TABLE slide_pages ADD CONSTRAINT fk_sp_slide FOREIGN KEY (slide_id) REFERENCES course_slides(id) ON DELETE CASCADE;

-- V49 course_slides.course_id ON DELETE CASCADE (DF-NEW-3 修复)
ALTER TABLE course_slides DROP CONSTRAINT IF EXISTS course_slides_course_id_fkey;
ALTER TABLE course_slides DROP CONSTRAINT IF EXISTS fk_cs_course;
ALTER TABLE course_slides ADD CONSTRAINT fk_cs_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE;

-- F-011: banners created_at/updated_at NOT NULL
ALTER TABLE banners ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE banners ALTER COLUMN updated_at SET NOT NULL;
