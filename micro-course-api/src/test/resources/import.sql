-- Test data for integration tests
-- All users have password: admin123 (BCrypt encoded with $2a$ prefix for Spring Security)
-- Generated using: BCryptPasswordEncoder.encode("admin123")

INSERT INTO users (id, username, password, real_name, email, role, status, cas_bound, created_at, updated_at)
VALUES (1, 'admin', '$2a$12$lSQy6WeZnpZzW4/7yAE4D.eV0lhf3JIPGA9nyw0sYGL2EvvAM5h7C', 'System Admin', 'admin@microcourse.local', 'ADMIN', 1, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (id, username, password, real_name, email, role, status, cas_bound, created_at, updated_at)
VALUES (2, 'teacher1', '$2a$12$lSQy6WeZnpZzW4/7yAE4D.eV0lhf3JIPGA9nyw0sYGL2EvvAM5h7C', 'Teacher One', 'teacher1@microcourse.local', 'TEACHER', 1, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (id, username, password, real_name, email, role, status, cas_bound, created_at, updated_at)
VALUES (3, 'student1', '$2a$12$lSQy6WeZnpZzW4/7yAE4D.eV0lhf3JIPGA9nyw0sYGL2EvvAM5h7C', 'Student One', 'student1@microcourse.local', 'STUDENT', 1, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Insert departments
INSERT INTO departments (id, name, code, sort_order, created_at, updated_at)
VALUES (1, 'Computer Science', 'CS', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Insert majors
INSERT INTO majors (id, name, code, department_id, sort_order, created_at, updated_at)
VALUES (1, 'Software Engineering', 'SE', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Insert classes
INSERT INTO classes (id, name, major_id, grade, sort_order, created_at, updated_at)
VALUES (1, 'CS2021 Class A', 1, '2021', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Update teacher1 with department and major
UPDATE users SET department_id = 1, major_id = 1 WHERE username = 'teacher1';

-- Update student1 with department, major, and class
UPDATE users SET department_id = 1, major_id = 1, class_id = 1, student_no = '2021001' WHERE username = 'student1';

-- Insert course categories
INSERT INTO course_categories (id, name, parent_id, level, sort_order, created_at, updated_at)
VALUES (1, 'Programming', NULL, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO course_categories (id, name, parent_id, level, sort_order, created_at, updated_at)
VALUES (2, 'Mathematics', NULL, 1, 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Insert courses
INSERT INTO courses (id, title, category_id, teacher_id, status, created_at, updated_at, version)
VALUES (1, 'Java Programming 101', 1, 2, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

INSERT INTO courses (id, title, category_id, teacher_id, status, created_at, updated_at, version)
VALUES (2, 'Advanced Python', 1, 2, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

INSERT INTO courses (id, title, category_id, teacher_id, status, created_at, updated_at, version)
VALUES (3, 'Linear Algebra', 2, 2, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

INSERT INTO courses (id, title, category_id, teacher_id, status, created_at, updated_at, version)
VALUES (4, 'Data Structures', 1, 2, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Insert course chapters
INSERT INTO course_chapters (id, course_id, title, sort_order, chapter_type, created_at, updated_at, version)
VALUES (1, 1, 'Chapter 1: Introduction', 1, 'VIDEO', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Insert videos
INSERT INTO videos (id, course_id, chapter_id, title, url, hls_url, status, progress, created_at, updated_at, version)
VALUES (1, 1, 1, 'Java Introduction', '/data/videos/1/1.mp4', '/data/videos/1/1.m3u8', 2, 100, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

INSERT INTO videos (id, course_id, chapter_id, title, url, hls_url, status, progress, created_at, updated_at, version)
VALUES (2, 1, 1, 'Java Variables', '/data/videos/1/2.mp4', '/data/videos/1/2.m3u8', 2, 100, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

-- Insert enrollments
INSERT INTO enrollments (id, user_id, course_id, progress, completed, enrolled_at, updated_at)
VALUES (1, 3, 1, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO enrollments (id, user_id, course_id, progress, completed, enrolled_at, updated_at)
VALUES (2, 3, 2, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);