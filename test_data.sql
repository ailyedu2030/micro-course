-- ============================================================
-- 微课平台 完整测试数据
-- 3部门 × 2专业 × 3班 | 4教师 × 5课程 | 2管理员/教务 | 15学生 × 3选课
-- ============================================================

BEGIN;

-- --- 清理（已执行，仅作保险） ---
SET session_replication_role = REPLICA;
TRUNCATE TABLE learning_progress, course_reviews, discussion_comments, discussion_posts,
                notifications, banners, grades, enrollments, teaching_classes,
                exercise_questions, questions, exercises, videos, course_chapters,
                courses, classes, majors, departments, course_categories,
                users RESTART IDENTITY CASCADE;
SET session_replication_role = DEFAULT;

-- ============================================================
-- 1. 部门 (3)
-- ============================================================
INSERT INTO departments (id, name, code, parent_id, sort_order, created_at, updated_at) VALUES
(1, '计算机科学与技术学院', 'CS', NULL, 1, NOW(), NOW()),
(2, '软件工程学院', 'SE', NULL, 2, NOW(), NOW()),
(3, '信息工程学院', 'IE', NULL, 3, NOW(), NOW());

-- ============================================================
-- 2. 专业 (6 = 3部门 × 2)
-- ============================================================
INSERT INTO majors (id, name, code, department_id, sort_order, created_at, updated_at) VALUES
(1, '计算机科学与技术', 'CS101', 1, 1, NOW(), NOW()),
(2, '软件工程', 'SE101', 2, 1, NOW(), NOW()),
(3, '网络工程', 'NE101', 1, 2, NOW(), NOW()),
(4, '数据科学与大数据技术', 'DS101', 3, 1, NOW(), NOW()),
(5, '人工智能', 'AI101', 3, 2, NOW(), NOW()),
(6, '信息安全', 'IS101', 2, 2, NOW(), NOW());

-- ============================================================
-- 3. 课程分类
-- ============================================================
INSERT INTO course_categories (id, name, parent_id, level, sort_order, created_at, updated_at) VALUES
(1, '编程基础', NULL, 1, 1, NOW(), NOW()),
(2, '数据结构与算法', NULL, 1, 2, NOW(), NOW()),
(3, '数据库', NULL, 1, 3, NOW(), NOW()),
(4, 'Web开发', NULL, 1, 4, NOW(), NOW()),
(5, '人工智能', NULL, 1, 5, NOW(), NOW());

-- ============================================================
-- 4. 班级 (9 = 3专业 × 3班)
-- ============================================================
INSERT INTO classes (id, name, major_id, grade, counselor_id, sort_order, created_at, updated_at) VALUES
-- 计算机科学与技术专业（大一/大二/大三）
(1, '计算机2101班', 1, '2021', NULL, 1, NOW(), NOW()),
(2, '计算机2201班', 1, '2022', NULL, 2, NOW(), NOW()),
(3, '计算机2301班', 1, '2023', NULL, 3, NOW(), NOW()),
-- 软件工程专业
(4, '软件2101班', 2, '2021', NULL, 1, NOW(), NOW()),
(5, '软件2201班', 2, '2022', NULL, 2, NOW(), NOW()),
(6, '软件2301班', 2, '2023', NULL, 3, NOW(), NOW()),
-- 数据科学与大数据技术
(7, '数据2101班', 4, '2021', NULL, 1, NOW(), NOW()),
(8, '数据2201班', 4, '2022', NULL, 2, NOW(), NOW()),
(9, '数据2301班', 4, '2023', NULL, 3, NOW(), NOW());

-- ============================================================
-- 5. 用户
-- 用户ID分配：
--   管理员/教务: 1-2
--   教师: 3-6
--   学生: 7-21
-- ============================================================

-- 管理员 (role=ADMIN)
INSERT INTO users (id, username, password, real_name, email, role, status, cas_bound, created_at, updated_at) VALUES
(1, 'admin', '$2a$10$N.QWRIYSJFHedcsy4I8MYeXfJAZ63DM3pLik2HPGtv/EzkJ1s3CKW', '系统管理员', 'admin@microcourse.edu', 'ADMIN', 1, false, NOW(), NOW());

-- 教务 (role=ACADEMIC)
INSERT INTO users (id, username, password, real_name, email, role, status, cas_bound, created_at, updated_at) VALUES
(2, 'academic', '$2a$10$N.QWRIYSJFHedcsy4I8MYeXfJAZ63DM3pLik2HPGtv/EzkJ1s3CKW', '张教务', 'academic@microcourse.edu', 'ACADEMIC', 1, false, NOW(), NOW());

-- 教师 (role=TEACHER, teacher_no)
INSERT INTO users (id, username, password, real_name, email, role, teacher_no, department_id, status, cas_bound, created_at, updated_at) VALUES
(3, 'teacher', '$2a$10$N.QWRIYSJFHedcsy4I8MYeXfJAZ63DM3pLik2HPGtv/EzkJ1s3CKW', '李教授', 'li@microcourse.edu', 'TEACHER', 'T001', 1, 1, false, NOW(), NOW()),
(4, 'teacher2', '$2a$10$N.QWRIYSJFHedcsy4I8MYeXfJAZ63DM3pLik2HPGtv/EzkJ1s3CKW', '王讲师', 'wang@microcourse.edu', 'TEACHER', 'T002', 2, 1, false, NOW(), NOW()),
(5, 'teacher3', '$2a$10$N.QWRIYSJFHedcsy4I8MYeXfJAZ63DM3pLik2HPGtv/EzkJ1s3CKW', '赵副教授', 'zhao@microcourse.edu', 'TEACHER', 'T003', 1, 1, false, NOW(), NOW()),
(6, 'teacher4', '$2a$10$N.QWRIYSJFHedcsy4I8MYeXfJAZ63DM3pLik2HPGtv/EzkJ1s3CKW', '刘助理', 'liu@microcourse.edu', 'TEACHER', 'T004', 3, 1, false, NOW(), NOW());

-- 学生 (role=STUDENT, student_no, class_id)
-- 计算机2101班 (class_id=1): 7,8,9,10,11
-- 软件2101班 (class_id=4): 12,13,14
-- 数据2101班 (class_id=7): 15,16,17
-- 计算机2201班 (class_id=2): 18,19,20
-- 软件2201班 (class_id=5): 21
INSERT INTO users (id, username, password, real_name, email, role, student_no, class_id, major_id, department_id, grade, enrollment_year, status, cas_bound, created_at, updated_at) VALUES
(7,  'student',     'student123', '陈小明', 'chen@microcourse.edu', 'STUDENT', 'S2101001', 1, 1, 1, '2021', '2021', 1, false, NOW(), NOW()),
(8,  'student2',    'student123', '林小红', 'lin@microcourse.edu', 'STUDENT', 'S2101002', 1, 1, 1, '2021', '2021', 1, false, NOW(), NOW()),
(9,  'student3',    'student123', '黄小华', 'huang@microcourse.edu', 'STUDENT', 'S2101003', 1, 1, 1, '2021', '2021', 1, false, NOW(), NOW()),
(10, 'student4',    'student123', '周小杰', 'zhou@microcourse.edu', 'STUDENT', 'S2101004', 1, 1, 1, '2021', '2021', 1, false, NOW(), NOW()),
(11, 'student5',    'student123', '吴小丽', 'wu@microcourse.edu',  'STUDENT', 'S2101005', 1, 1, 1, '2021', '2021', 1, false, NOW(), NOW()),
(12, 'student6',    'student123', '郑小强', 'zheng@microcourse.edu','STUDENT', 'S2102001', 4, 2, 2, '2021', '2021', 1, false, NOW(), NOW()),
(13, 'student7',    'student123', '孙小丹', 'sun@microcourse.edu', 'STUDENT', 'S2102002', 4, 2, 2, '2021', '2021', 1, false, NOW(), NOW()),
(14, 'student8',    'student123', '马小虎', 'ma@microcourse.edu',  'STUDENT', 'S2102003', 4, 2, 2, '2021', '2021', 1, false, NOW(), NOW()),
(15, 'student9',    'student123', '朱小鹏', 'zhu@microcourse.edu', 'STUDENT', 'S2103001', 7, 4, 3, '2021', '2021', 1, false, NOW(), NOW()),
(16, 'student10',   'student123', '胡小鸣', 'hu@microcourse.edu',  'STUDENT', 'S2103002', 7, 4, 3, '2021', '2021', 1, false, NOW(), NOW()),
(17, 'student11',   'student123', '郭小涛', 'guo@microcourse.edu',  'STUDENT', 'S2103003', 7, 4, 3, '2021', '2021', 1, false, NOW(), NOW()),
(18, 'student12',   'student123', '杨小欢', 'yang@microcourse.edu', 'STUDENT', 'S2201001', 2, 1, 1, '2022', '2022', 1, false, NOW(), NOW()),
(19, 'student13',   'student123', '赵小燕', 'zhaoy@microcourse.edu','STUDENT', 'S2201002', 2, 1, 1, '2022', '2022', 1, false, NOW(), NOW()),
(20, 'student14',   'student123', '吴小宇', 'wuy@microcourse.edu',  'STUDENT', 'S2201003', 2, 1, 1, '2022', '2022', 1, false, NOW(), NOW()),
(21, 'student15',   'student123', '周小佳', 'zhouj@microcourse.edu', 'STUDENT', 'S2202001', 5, 2, 2, '2022', '2022', 1, false, NOW(), NOW());

-- ============================================================
-- 6. 课程 (5门, 由4位教师教授)
-- ID: 1,2,3,4,5
-- ============================================================
INSERT INTO courses (id, title, subtitle, summary, category_id, teacher_id, offer_department_id, semester, credit_hours, course_nature, max_students, difficulty, status, description, student_count, avg_rating, published_at, created_at, updated_at, version, is_recommended, tags) VALUES
(1, 'Java程序设计基础', '从零入门Java编程', '本课程面向零基础学生，系统讲授Java语言基础语法、面向对象思想和常用类库。', 1, 3, 1, '2024-1', 64, 'MAJOR', 120, 1, 2, '通过本课程学习，学生将掌握Java语言基本语法和面向对象编程思想，能够独立完成简单Java程序的开发。', 12, 4.50, NOW(), NOW(), NOW(), 1, true, 'Java,编程入门,面向对象'),
(2, '数据结构与算法', '经典数据结构与算法实现', '线性表、栈、队列、树、图等数据结构及其常用算法实现与分析。', 2, 3, 1, '2024-1', 72, 'MAJOR', 100, 3, 2, '培养学生理解并应用数据结构解决实际问题的能力，掌握算法分析基本方法。', 8, 4.70, NOW(), NOW(), NOW(), 1, true, '数据结构,算法,竞赛'),
(3, '数据库系统原理', '从关系模型到SQL优化', '关系数据库理论、SQL语言、数据库设计、事务管理与性能优化。', 3, 4, 2, '2024-1', 56, 'MAJOR', 100, 2, 2, '使学生掌握数据库系统的核心概念，能够进行数据库设计和SQL编程。', 10, 4.30, NOW(), NOW(), NOW(), 1, false, '数据库,SQL,PostgreSQL'),
(4, 'Web应用开发', 'Spring Boot + Vue全栈实践', '基于Spring Boot后端和Vue前端的全栈Web应用开发，含RESTful API设计和数据库交互。', 4, 5, 1, '2024-2', 64, 'MAJOR', 80, 2, 2, '培养学生掌握现代Web应用开发全流程，独立完成中小型Web系统。', 6, 4.60, NOW(), NOW(), NOW(), 1, true, 'SpringBoot,Vue,REST'),
(5, '机器学习基础', '理论+实践机器学习入门', '监督学习、无监督学习、深度学习基础，常用机器学习算法原理与Python实现。', 5, 6, 3, '2024-2', 64, 'MAJOR', 60, 4, 2, '使学生理解机器学习基本概念和常用算法，能够使用scikit-learn等工具完成基础机器学习任务。', 5, 4.80, NOW(), NOW(), NOW(), 1, false, '机器学习,Python,sklearn');

-- ============================================================
-- 7. 课程章节 (每门课3-4章)
-- ============================================================
INSERT INTO course_chapters (id, course_id, title, description, sort_order, chapter_type, duration, created_at, updated_at, version, learning_objectives) VALUES
-- 课程1: Java程序设计基础
(1,  1, 'Java语言入门', '开发环境、第一个Java程序、基础语法', 1, 'CHAPTER', 7200, NOW(), NOW(), 1, '掌握Java开发环境搭建，理解Java程序基本结构'),
(2,  1, '面向对象基础', '类与对象、封装、继承、多态', 2, 'CHAPTER', 10800, NOW(), NOW(), 1, '理解面向对象三大特性，能够设计简单类'),
(3,  1, 'Java核心类库', 'String、集合框架、IO流、异常处理', 3, 'CHAPTER', 14400, NOW(), NOW(), 1, '熟练使用Java常用类库进行程序开发'),
-- 课程2: 数据结构与算法
(4,  2, '线性结构', '数组、链表、栈、队列', 1, 'CHAPTER', 10800, NOW(), NOW(), 1, '掌握线性结构原理和实现'),
(5,  2, '树形结构', '二叉树、AVL树、红黑树、B树', 2, 'CHAPTER', 14400, NOW(), NOW(), 1, '理解树形结构及其应用场景'),
(6,  2, '图与算法', '图的表示、遍历、最短路径、最小生成树', 3, 'CHAPTER', 14400, NOW(), NOW(), 1, '掌握图算法基础'),
-- 课程3: 数据库系统原理
(7,  3, '关系模型与SQL', '关系模型、DDL、DML、DQL', 1, 'CHAPTER', 10800, NOW(), NOW(), 1, '理解关系模型，熟练使用SQL'),
(8,  3, '数据库设计', 'ER图、范式理论、数据库规范化', 2, 'CHAPTER', 7200, NOW(), NOW(), 1, '能够进行概念结构设计到逻辑结构设计的转换'),
(9,  3, '数据库高级特性', '事务、并发控制、查询优化', 3, 'CHAPTER', 7200, NOW(), NOW(), 1, '理解事务ACID特性，掌握并发控制基础'),
-- 课程4: Web应用开发
(10, 4, 'Spring Boot入门', 'RESTful API设计、依赖注入', 1, 'CHAPTER', 7200, NOW(), NOW(), 1, '掌握Spring Boot基本用法'),
(11, 4, 'Vue前端开发', '组件化思想、Pinia状态管理、路由', 2, 'CHAPTER', 10800, NOW(), NOW(), 1, '掌握Vue3核心概念'),
(12, 4, '全栈项目实战', '前后端联调、部署', 3, 'CHAPTER', 14400, NOW(), NOW(), 1, '完成一个完整全栈项目'),
-- 课程5: 机器学习基础
(13, 5, '机器学习概述', '监督学习、无监督学习、模型评估', 1, 'CHAPTER', 5400, NOW(), NOW(), 1, '理解机器学习基本范式'),
(14, 5, '监督学习算法', '线性回归、逻辑回归、决策树、SVM', 2, 'CHAPTER', 14400, NOW(), NOW(), 1, '掌握常用监督学习算法原理'),
(15, 5, '无监督学习与深度学习', '聚类、PCA、神经网络基础', 3, 'CHAPTER', 10800, NOW(), NOW(), 1, '理解无监督学习和神经网络基础');

-- ============================================================
-- 8. 视频 (每章节1-2个视频)
-- ============================================================
INSERT INTO videos (id, chapter_id, course_id, title, original_name, file_size, duration, url, status, sort_order, created_at, updated_at, version) VALUES
-- Java Chapter 1
(1,  1, 1, 'Java开发环境搭建', 'java-env.mp4', 524288000, 3600, 'https://cdn.microcourse.edu/videos/java-env.mp4', 2, 1, NOW(), NOW(), 1),
(2,  1, 1, '第一个Java程序', 'java-first.mp4', 314572800, 3600, 'https://cdn.microcourse.edu/videos/java-first.mp4', 2, 2, NOW(), NOW(), 1),
-- Java Chapter 2
(3,  2, 1, '类与对象详解', 'java-oop.mp4', 629145600, 5400, 'https://cdn.microcourse.edu/videos/java-oop.mp4', 2, 1, NOW(), NOW(), 1),
(4,  2, 1, '继承与多态', 'java-polymorph.mp4', 524288000, 5400, 'https://cdn.microcourse.edu/videos/java-polymorph.mp4', 2, 2, NOW(), NOW(), 1),
-- Java Chapter 3
(5,  3, 1, 'String类源码解读', 'java-string.mp4', 419430400, 7200, 'https://cdn.microcourse.edu/videos/java-string.mp4', 2, 1, NOW(), NOW(), 1),
-- DS Chapter 1
(6,  4, 2, '数组与链表实现', 'ds-array.mp4', 471859200, 5400, 'https://cdn.microcourse.edu/videos/ds-array.mp4', 2, 1, NOW(), NOW(), 1),
(7,  4, 2, '栈和队列应用', 'ds-stack.mp4', 314572800, 5400, 'https://cdn.microcourse.edu/videos/ds-stack.mp4', 2, 2, NOW(), NOW(), 1),
-- DS Chapter 2
(8,  5, 2, '二叉树遍历', 'ds-tree.mp4', 524288000, 7200, 'https://cdn.microcourse.edu/videos/ds-tree.mp4', 2, 1, NOW(), NOW(), 1),
-- DB Chapter 1
(9,  7, 3, 'SQL基础查询', 'db-sql.mp4', 419430400, 5400, 'https://cdn.microcourse.edu/videos/db-sql.mp4', 2, 1, NOW(), NOW(), 1),
(10, 7, 3, '连接查询与子查询', 'db-join.mp4', 367001600, 5400, 'https://cdn.microcourse.edu/videos/db-join.mp4', 2, 2, NOW(), NOW(), 1),
-- DB Chapter 2
(11, 8, 3, 'ER图设计方法', 'db-er.mp4', 314572800, 3600, 'https://cdn.microcourse.edu/videos/db-er.mp4', 2, 1, NOW(), NOW(), 1),
-- Web Chapter 1
(12, 10, 4, 'RESTful API设计原则', 'web-rest.mp4', 262144000, 3600, 'https://cdn.microcourse.edu/videos/web-rest.mp4', 2, 1, NOW(), NOW(), 1),
-- Web Chapter 2
(13, 11, 4, 'Vue3核心概念', 'web-vue.mp4', 524288000, 5400, 'https://cdn.microcourse.edu/videos/web-vue.mp4', 2, 1, NOW(), NOW(), 1),
-- ML Chapter 1
(14, 13, 5, '机器学习入门概述', 'ml-intro.mp4', 209715200, 2700, 'https://cdn.microcourse.edu/videos/ml-intro.mp4', 2, 1, NOW(), NOW(), 1),
-- ML Chapter 2
(15, 14, 5, '线性回归详解', 'ml-linear.mp4', 419430400, 7200, 'https://cdn.microcourse.edu/videos/ml-linear.mp4', 2, 1, NOW(), NOW(), 1);

-- ============================================================
-- 9. 练习 (每课程1-2个)
-- ============================================================
INSERT INTO exercises (id, chapter_id, course_id, title, pass_score, time_limit, max_attempts, shuffle_questions, shuffle_options, total_score, question_count, created_at, updated_at, version) VALUES
(1, 1, 1, 'Java基础语法练习', 60, 3600, 3, true, true, 100, 5, NOW(), NOW(), 1),
(2, 4, 2, '数据结构基础练习', 70, 3600, 3, true, false, 100, 5, NOW(), NOW(), 1),
(3, 7, 3, 'SQL查询练习', 60, 1800, 5, false, false, 100, 5, NOW(), NOW(), 1),
(4, 10, 4, 'RESTful API设计练习', 70, 3600, 3, true, false, 100, 4, NOW(), NOW(), 1),
(5, 13, 5, '机器学习基础概念测试', 60, 1800, 2, true, false, 100, 5, NOW(), NOW(), 1);

-- ============================================================
-- 10. 题目 (每练习5题)
-- ============================================================
INSERT INTO questions (id, course_id, teacher_id, question_type, content, options, answer, partial_score, explanation, difficulty, version, status, created_at, updated_at) VALUES
-- Exercise 1: Java基础语法
(1,  1, 3, 'SINGLE_CHOICE', '下列哪个是Java的入口方法？', '{"A":"main()","B":"Main()","C":"public static void main(String[] args)","D":"start()"}', 'C', false, 'Java程序入口必须是public static void main(String[] args)', 1, 1, 1, NOW(), NOW()),
(2,  1, 3, 'MULTIPLE_CHOICE', '下列哪些是Java的基本数据类型？', '{"A":"int","B":"Integer","C":"String","D":"boolean"}', 'A,D', false, 'int和boolean是基本类型，Integer和String是包装/引用类型', 1, 1, 1, NOW(), NOW()),
(3,  1, 3, 'SINGLE_CHOICE', 'new关键字的作用是？', '{"A":"声明变量","B":"创建对象","C":"调用方法","D":"定义类"}', 'B', false, 'new用于在堆内存创建对象', 1, 1, 1, NOW(), NOW()),
(4,  1, 3, 'SINGLE_CHOICE', '以下哪个不是面向对象特性？', '{"A":"封装","B":"继承","C":"多态","D":"递归"}', 'D', false, '递归是算法特性，不是OOP特性', 2, 1, 1, NOW(), NOW()),
(5,  1, 3, 'FILL_BLANK', 'Java中继承的关键字是____。', NULL, 'extends', false, 'Java使用extends关键字实现继承', 1, 1, 1, NOW(), NOW()),

-- Exercise 2: 数据结构
(6,  2, 3, 'SINGLE_CHOICE', '数组插入元素的时间复杂度是？', '{"A":"O(1)","B":"O(n)","C":"O(log n)","D":"O(n²)"}', 'B', false, '数组插入平均需要移动元素，时间复杂度O(n)', 2, 1, 1, NOW(), NOW()),
(7,  2, 3, 'SINGLE_CHOICE', '栈的特点是？', '{"A":"FIFO","B":"LIFO","C":"随机访问","D":"层次遍历"}', 'B', false, '栈是后进先出（LIFO）数据结构', 1, 1, 1, NOW(), NOW()),
(8,  2, 3, 'MULTIPLE_CHOICE', '哪些属于树形结构？', '{"A":"二叉树","B":"B树","C":"链表","D":"堆"}', 'A,B,D', false, '链表是线性结构，不是树形结构', 2, 1, 1, NOW(), NOW()),
(9,  2, 3, 'SINGLE_CHOICE', 'AVL树是一种？', '{"A":"二叉搜索树","B":"完全二叉树","C":"线索二叉树","D":"B树"}', 'A', false, 'AVL树是自平衡的二叉搜索树', 3, 1, 1, NOW(), NOW()),
(10, 2, 3, 'FILL_BLANK', '图的一种重要遍历方式是____遍历。', NULL, 'BFS或DFS', false, '图的两种主要遍历方式：广度优先(BFS)和深度优先(DFS)', 2, 1, 1, NOW(), NOW()),

-- Exercise 3: 数据库
(11, 3, 4, 'SINGLE_CHOICE', 'SQL中用于去重的关键字是？', '{"A":"DISTINCT","B":"UNIQUE","C":"GROUP BY","D":"ORDER BY"}', 'A', false, '使用DISTINCT关键字去除重复行', 1, 1, 1, NOW(), NOW()),
(12, 3, 4, 'MULTIPLE_CHOICE', '哪些是SQL的事务控制命令？', '{"A":"COMMIT","B":"ROLLBACK","C":"SAVEPOINT","D":"GRANT"}', 'A,B,C', false, 'GRANT是权限控制命令，不是事务控制', 2, 1, 1, NOW(), NOW()),
(13, 3, 4, 'SINGLE_CHOICE', '数据库acid特性不包括？', '{"A":"原子性","B":"一致性","C":"隔离性","D":"可靠性"}', 'D', false, 'ACID是原子性、一致性、隔离性、持久性', 1, 1, 1, NOW(), NOW()),
(14, 3, 4, 'SINGLE_CHOICE', '用于建立表结构的SQL是？', '{"A":"CREATE","B":"INSERT","C":"SELECT","D":"UPDATE"}', 'A', false, 'CREATE TABLE用于建立表结构', 1, 1, 1, NOW(), NOW()),
(15, 3, 4, 'FILL_BLANK', 'SQL中用于分组的子句是____。', NULL, 'GROUP BY', false, 'GROUP BY用于对结果集分组', 1, 1, 1, NOW(), NOW()),

-- Exercise 4: Web开发
(16, 4, 5, 'SINGLE_CHOICE', 'RESTful风格中，GET方法用于？', '{"A":"创建资源","B":"读取资源","C":"更新资源","D":"删除资源"}', 'B', false, 'GET方法用于读取/查询资源', 1, 1, 1, NOW(), NOW()),
(17, 4, 5, 'MULTIPLE_CHOICE', 'Spring Boot的核心特性包括？', '{"A":"自动配置","B":"嵌入式服务器","C":"依赖注入","D":"ORM映射"}', 'A,B,C', false, 'Spring Boot自动配置、嵌入式服务器、依赖注入是核心特性', 2, 1, 1, NOW(), NOW()),
(18, 4, 5, 'SINGLE_CHOICE', 'Vue中用于状态管理的库是？', '{"A":"Redux","B":"Pinia","C":"MobX","D":"Vuex only"', 'B', false, 'Pinia是Vue3官方推荐的状态管理库', 2, 1, 1, NOW(), NOW()),
(19, 4, 5, 'SINGLE_CHOICE', 'CORS跨域资源共享中，*表示？', '{"A":"允许所有域名","B":"只允许GET","C":"只允许POST","D":"禁止跨域"}', 'A', false, 'Access-Control-Allow-Origin: * 允许所有域名', 1, 1, 1, NOW(), NOW()),
(20, 4, 5, 'FILL_BLANK', 'HTTP状态码404表示____。', NULL, '资源未找到', false, '404 Not Found表示请求的资源不存在', 1, 1, 1, NOW(), NOW()),

-- Exercise 5: 机器学习
(21, 5, 6, 'SINGLE_CHOICE', '机器学习算法不包括？', '{"A":"线性回归","B":"决策树","C":"冒泡排序","D":"神经网络"}', 'C', false, '冒泡排序是确定性的排序算法，不是机器学习', 1, 1, 1, NOW(), NOW()),
(22, 5, 6, 'SINGLE_CHOICE', '监督学习需要？', '{"A":"有标签数据","B":"无标签数据","C":"不需要数据","D":"随机数据"}', 'A', false, '监督学习使用有标签的训练数据', 1, 1, 1, NOW(), NOW()),
(23, 5, 6, 'MULTIPLE_CHOICE', '哪些是分类算法？', '{"A":"KNN","B":"K-Means","C":"逻辑回归","D":"决策树"}', 'A,C,D', false, 'K-Means是聚类算法（无监督），其他都是分类', 2, 1, 1, NOW(), NOW()),
(24, 5, 6, 'SINGLE_CHOICE', '模型评估中，准确率的计算公式是？', '{"A":"正确数/总数","B":"错误数/总数","C":"预测正例/实际正例","D":"召回率/精确率"}', 'A', false, '准确率=预测正确的样本数/总样本数', 2, 1, 1, NOW(), NOW()),
(25, 5, 6, 'FILL_BLANK', '过拟合是指模型____泛化能力弱。', NULL, '过于复杂或训练', false, '过拟合指模型在训练集表现好但测试集表现差', 2, 1, 1, NOW(), NOW());

-- ============================================================
-- 11. 练习-题目关联
-- ============================================================
INSERT INTO exercise_questions (exercise_id, question_id, score, sort_order) VALUES
(1, 1, 20, 1), (1, 2, 20, 2), (1, 3, 20, 3), (1, 4, 20, 4), (1, 5, 20, 5),
(2, 6, 20, 1), (2, 7, 20, 2), (2, 8, 20, 3), (2, 9, 20, 4), (2, 10, 20, 5),
(3, 11, 20, 1), (3, 12, 20, 2), (3, 13, 20, 3), (3, 14, 20, 4), (3, 15, 20, 5),
(4, 16, 25, 1), (4, 17, 25, 2), (4, 18, 25, 3), (4, 19, 25, 4),
(5, 21, 20, 1), (5, 22, 20, 2), (5, 23, 20, 3), (5, 24, 20, 4), (5, 25, 20, 5);

-- ============================================================
-- 12. 选课记录
-- 每学生选3门课，部分已完成
-- ============================================================
INSERT INTO enrollments (id, course_id, user_id, progress, completed, final_score, final_grade, enrollment_status, enrolled_at, completed_at, updated_at) VALUES
-- 学生1(陈小明): 选Java、数据结构、Web (Java和DS已完成)
(1,  1, 7, 100.0, true,  85.0, '良好', 'ENROLLED', NOW()-INTERVAL '90 days', NOW()-INTERVAL '30 days', NOW()),
(2,  2, 7, 100.0, true,  92.0, '优秀', 'ENROLLED', NOW()-INTERVAL '85 days', NOW()-INTERVAL '25 days', NOW()),
(3,  3, 7,  45.0, false, NULL, NULL, 'ENROLLED', NOW()-INTERVAL '60 days', NULL, NOW()),
-- 学生2(林小红): 选Java、DB、Web (Java已完成)
(4,  1, 8, 100.0, true,  78.0, '中等', 'ENROLLED', NOW()-INTERVAL '90 days', NOW()-INTERVAL '35 days', NOW()),
(5,  3, 8,  60.0, false, NULL, NULL, 'ENROLLED', NOW()-INTERVAL '75 days', NULL, NOW()),
(6,  4, 8,  20.0, false, NULL, NULL, 'ENROLLED', NOW()-INTERVAL '30 days', NULL, NOW()),
-- 学生3(黄小华): 选Java、ML、数据结构 (全部在学)
(7,  1, 9,  55.0, false, NULL, NULL, 'ENROLLED', NOW()-INTERVAL '80 days', NULL, NOW()),
(8,  5, 9,  30.0, false, NULL, NULL, 'ENROLLED', NOW()-INTERVAL '50 days', NULL, NOW()),
(9,  2, 9,  25.0, false, NULL, NULL, 'ENROLLED', NOW()-INTERVAL '45 days', NULL, NOW()),
-- 学生4(周小杰): 选DB、ML、Web
(10, 3, 10, 70.0, false, NULL, NULL, 'ENROLLED', NOW()-INTERVAL '70 days', NULL, NOW()),
(11, 5, 10, 50.0, false, NULL, NULL, 'ENROLLED', NOW()-INTERVAL '55 days', NULL, NOW()),
(12, 4, 10, 35.0, false, NULL, NULL, 'ENROLLED', NOW()-INTERVAL '40 days', NULL, NOW()),
-- 学生5(吴小丽): 选Java、DS
(13, 1, 11, 80.0, false, NULL, NULL, 'ENROLLED', NOW()-INTERVAL '88 days', NULL, NOW()),
(14, 2, 11, 40.0, false, NULL, NULL, 'ENROLLED', NOW()-INTERVAL '65 days', NULL, NOW()),
-- 学生6(郑小强): 选DB、Web
(15, 3, 12, 55.0, false, NULL, NULL, 'ENROLLED', NOW()-INTERVAL '62 days', NULL, NOW()),
(16, 4, 12, 15.0, false, NULL, NULL, 'ENROLLED', NOW()-INTERVAL '28 days', NULL, NOW()),
-- 学生7(孙小丹): 选Java、ML、DS
(17, 1, 13, 65.0, false, NULL, NULL, 'ENROLLED', NOW()-INTERVAL '75 days', NULL, NOW()),
(18, 5, 13, 45.0, false, NULL, NULL, 'ENROLLED', NOW()-INTERVAL '48 days', NULL, NOW()),
(19, 2, 13, 30.0, false, NULL, NULL, 'ENROLLED', NOW()-INTERVAL '42 days', NULL, NOW()),
-- 学生8(马小虎): 选Java
(20, 1, 14, 90.0, false, NULL, NULL, 'ENROLLED', NOW()-INTERVAL '85 days', NULL, NOW()),
-- 学生9(朱小鹏): 选ML、DB
(21, 5, 15, 60.0, false, NULL, NULL, 'ENROLLED', NOW()-INTERVAL '58 days', NULL, NOW()),
(22, 3, 15, 40.0, false, NULL, NULL, 'ENROLLED', NOW()-INTERVAL '52 days', NULL, NOW()),
-- 学生10(胡小鸣): 选Java、DS
(23, 1, 16, 50.0, false, NULL, NULL, 'ENROLLED', NOW()-INTERVAL '72 days', NULL, NOW()),
(24, 2, 16, 35.0, false, NULL, NULL, 'ENROLLED', NOW()-INTERVAL '55 days', NULL, NOW()),
-- 学生11(郭小涛): 选ML、Web
(25, 5, 17, 75.0, false, NULL, NULL, 'ENROLLED', NOW()-INTERVAL '65 days', NULL, NOW()),
(26, 4, 17, 45.0, false, NULL, NULL, 'ENROLLED', NOW()-INTERVAL '38 days', NULL, NOW()),
-- 学生12(杨小欢): 选Java、DB
(27, 1, 18, 70.0, false, NULL, NULL, 'ENROLLED', NOW()-INTERVAL '68 days', NULL, NOW()),
(28, 3, 18, 25.0, false, NULL, NULL, 'ENROLLED', NOW()-INTERVAL '45 days', NULL, NOW()),
-- 学生13(赵小燕): 选DS、ML
(29, 2, 19, 55.0, false, NULL, NULL, 'ENROLLED', NOW()-INTERVAL '63 days', NULL, NOW()),
(30, 5, 19, 20.0, false, NULL, NULL, 'ENROLLED', NOW()-INTERVAL '35 days', NULL, NOW()),
-- 学生14(吴小宇): 选Java、Web
(31, 1, 20, 40.0, false, NULL, NULL, 'ENROLLED', NOW()-INTERVAL '50 days', NULL, NOW()),
(32, 4, 20, 60.0, false, NULL, NULL, 'ENROLLED', NOW()-INTERVAL '42 days', NULL, NOW()),
-- 学生15(周小佳): 选DB、ML
(33, 3, 21, 35.0, false, NULL, NULL, 'ENROLLED', NOW()-INTERVAL '48 days', NULL, NOW()),
(34, 5, 21, 50.0, false, NULL, NULL, 'ENROLLED', NOW()-INTERVAL '40 days', NULL, NOW());

-- ============================================================
-- 13. 成绩记录
-- ============================================================
INSERT INTO grades (id, course_id, user_id, exercise_id, score, total_score, passed, attempt_no, duration, comment, graded_by, graded_at, created_at) VALUES
-- Java练习成绩
(1,  1, 7,  1, 85.0, 100, true, 1, 2400, '基础扎实，细节需注意', 3, NOW()-INTERVAL '30 days', NOW()),
(2,  1, 8,  1, 78.0, 100, true, 1, 2800, '编码规范有待提升', 3, NOW()-INTERVAL '35 days', NOW()),
-- 数据结构练习成绩
(3,  2, 7,  2, 92.0, 100, true, 1, 3000, '算法思路清晰', 3, NOW()-INTERVAL '25 days', NOW()),
-- DB练习成绩
(4,  3, 7,  3, 73.0, 100, true, 1, 1500, 'SQL基础较好，子查询需加强', 4, NOW()-INTERVAL '15 days', NOW());

-- ============================================================
-- 14. 授课班级
-- ============================================================
INSERT INTO teaching_classes (id, course_id, teacher_id, name, max_students, student_count, schedule, location, semester, status, created_at, updated_at, version) VALUES
(1, 1, 3, 'Java2101班', 60, 12, '周一 3-4节, 周三 1-2节', '教学楼A101', '2024-1', 1, NOW(), NOW(), 1),
(2, 2, 3, '数据结构2101班', 50, 8, '周二 5-6节, 周四 3-4节', '教学楼B203', '2024-1', 1, NOW(), NOW(), 1),
(3, 3, 4, '数据库2101班', 55, 10, '周一 1-2节, 周五 3-4节', '教学楼A305', '2024-1', 1, NOW(), NOW(), 1),
(4, 4, 5, 'Web开发2101班', 45, 6, '周三 5-6节, 周五 1-2节', '实验楼C101', '2024-2', 1, NOW(), NOW(), 1),
(5, 5, 6, '机器学习2101班', 40, 5, '周二 7-8节, 周四 5-6节', '实验楼A502', '2024-2', 1, NOW(), NOW(), 1);

-- ============================================================
-- 15. 讨论帖
-- ============================================================
INSERT INTO discussion_posts (id, course_id, chapter_id, user_id, title, content, is_anonymous, is_pinned, is_essence, comment_count, like_count, status, created_at, updated_at) VALUES
(1, 1, 1, 7,  'Java环境配置报错求解', '按照视频配置JDK一直报错class not found，能帮我看看吗？', false, false, false, 3, 5, 1, NOW()-INTERVAL '20 days', NOW()-INTERVAL '20 days'),
(2, 1, 2, 8,  '关于继承和多态的疑问', '老师讲的下转型为什么要强制转换，能举个例子吗？', false, false, true, 7, 12, 1, NOW()-INTERVAL '18 days', NOW()-INTERVAL '18 days'),
(3, 2, 4, 7,  '数组和链表的选择问题', '什么场景下应该用数组而不是链表？', false, false, false, 5, 8, 1, NOW()-INTERVAL '15 days', NOW()-INTERVAL '15 days'),
(4, 3, 7, 10, 'SQL子查询怎么优化', '我的子查询跑得很慢，有没有优化方法？', false, true, false, 4, 6, 1, NOW()-INTERVAL '12 days', NOW()-INTERVAL '12 days'),
(5, 5, 14, 9, '线性回归梯度下降收敛问题', '调试时loss一直不下降，是学习率的问题吗？', false, false, true, 6, 15, 1, NOW()-INTERVAL '10 days', NOW()-INTERVAL '10 days'),
(6, 1, 1, 11, '求助：String字符串拼接效率', 'String和StringBuilder性能差异大吗？', false, false, false, 2, 3, 1, NOW()-INTERVAL '8 days', NOW()-INTERVAL '8 days'),
(7, 4, 10, 12, 'RESTful API设计问题', 'PUT和PATCH的区别是什么？', false, false, false, 3, 7, 1, NOW()-INTERVAL '6 days', NOW()-INTERVAL '6 days'),
(8, 2, 5, 19, '红黑树删除操作看不懂', '删除节点的几种情况能详细讲讲吗？', false, false, false, 1, 2, 1, NOW()-INTERVAL '4 days', NOW()-INTERVAL '4 days');

-- ============================================================
-- 16. 讨论回复
-- ============================================================
INSERT INTO discussion_comments (id, post_id, parent_id, user_id, content, is_teacher_reply, like_count, status, created_at) VALUES
(1, 1, NULL, 3,  '检查一下环境变量JAVA_HOME是否配置正确', true, 3, 1, NOW()-INTERVAL '19 days'),
(2, 1, 1,   7,  '谢谢老师！我重新配了环境变量好了', false, 1, 1, NOW()-INTERVAL '19 days'),
(3, 2, NULL, 3,  '下转型是因为父类引用指向子类对象，需要强制转换才能访问子类特有方法。例如：Animal a = new Dog(); Dog d = (Dog)a;', true, 8, 1, NOW()-INTERVAL '17 days'),
(4, 3, NULL, 3,  '数组适合频繁随机访问，链表适合频繁插入删除。根据你的业务场景选择。', true, 5, 1, NOW()-INTERVAL '14 days'),
(5, 4, NULL, 4,  '可以尝试用EXPLAIN分析执行计划，看是否用了索引', true, 4, 1, NOW()-INTERVAL '11 days'),
(6, 5, NULL, 6,  '可能是学习率设置过大导致震荡，建议从0.01开始试试', true, 7, 1, NOW()-INTERVAL '9 days'),
(7, 6, NULL, 3,  'String每次拼接会创建新对象，StringBuilder在原对象上操作，高频场景下性能差异明显', true, 4, 1, NOW()-INTERVAL '7 days'),
(8, 7, NULL, 5,  'PUT是完整替换，PATCH是部分更新。RESTful设计中要根据语义选择', true, 5, 1, NOW()-INTERVAL '5 days');

-- ============================================================
-- 17. 课程评价
-- ============================================================
INSERT INTO course_reviews (id, course_id, user_id, rating, content, is_anonymous, created_at) VALUES
(1, 1, 7,  5, '李教授讲得非常细致，零基础也能跟上！', false, NOW()-INTERVAL '25 days'),
(2, 1, 8,  4, '课程内容很充实，就是节奏有点快', false, NOW()-INTERVAL '22 days'),
(3, 2, 7,  5, '数据结构讲得很清楚，算法部分收获很大', false, NOW()-INTERVAL '20 days'),
(4, 3, 10, 4, '王老师很负责，DB内容很实用', false, NOW()-INTERVAL '18 days'),
(5, 4, 8,  5, '全栈项目实战很有意义，学到了很多', false, NOW()-INTERVAL '15 days'),
(6, 5, 9,  5, '刘助理讲得很系统，机器学习入门必看', false, NOW()-INTERVAL '12 days'),
(7, 2, 19, 4, '内容偏难但很全面，需要多花时间', false, NOW()-INTERVAL '10 days'),
(8, 1, 11, 5, '强推！老师答疑很及时', false, NOW()-INTERVAL '8 days');

-- ============================================================
-- 18. 学习进度
-- ============================================================
INSERT INTO learning_progress (id, user_id, course_id, chapter_id, video_progress, video_position, exercise_completed, exercise_passed, total_watch_time, playback_speed, completed, last_watch_at, created_at) VALUES
-- 学生1 Java进度
(1,  7, 1, 1, 100, 3600, false, false, 7200, 1.0, true, NOW()-INTERVAL '30 days', NOW()),
(2,  7, 1, 2, 80,  4320, false, false, 8640, 1.0, false, NOW()-INTERVAL '15 days', NOW()),
-- 学生1 数据结构进度
(3,  7, 2, 4, 100, 5400, false, false, 5400, 1.0, true, NOW()-INTERVAL '25 days', NOW()),
(4,  7, 2, 5, 60,  4320, false, false, 7200, 1.0, false, NOW()-INTERVAL '10 days', NOW()),
-- 学生2 Java进度
(5,  8, 1, 1, 100, 3600, false, false, 3600, 1.0, true, NOW()-INTERVAL '35 days', NOW()),
(6,  8, 1, 2, 100, 5400, false, false, 5400, 1.0, true, NOW()-INTERVAL '20 days', NOW()),
-- 学生8 Java进度
(7,  14, 1, 1, 100, 3600, false, false, 3600, 1.0, true, NOW()-INTERVAL '40 days', NOW()),
(8,  14, 1, 2, 100, 5400, false, false, 5400, 1.0, true, NOW()-INTERVAL '30 days', NOW()),
(9,  14, 1, 3, 60,  4320, false, false, 4320, 1.0, false, NOW()-INTERVAL '20 days', NOW()),
-- 学生12 Web进度
(10, 12, 4, 10, 50,  1800, false, false, 1800, 1.0, false, NOW()-INTERVAL '15 days', NOW()),
-- 学生17 ML进度
(11, 17, 5, 13, 100, 2700, false, false, 2700, 1.0, true, NOW()-INTERVAL '20 days', NOW()),
(12, 17, 5, 14, 40,  2880, false, false, 2880, 1.0, false, NOW()-INTERVAL '8 days', NOW());

-- ============================================================
-- 19. 通知
-- ============================================================
INSERT INTO notifications (id, user_id, type, title, content, related_id, channel, is_read, read_at, created_at) VALUES
(1,  3,  'ENROLLMENT', '新学生选课通知', '陈小明 选择了您的 Java程序设计基础 课程', 1, 'SYSTEM', false, NULL, NOW()-INTERVAL '3 days'),
(2,  4,  'ENROLLMENT', '新学生选课通知', '郑小强 选择了您的 数据库系统原理 课程', 3, 'SYSTEM', false, NULL, NOW()-INTERVAL '5 days'),
(3,  7,  'GRADE',     '成绩发布通知', '您的 Java程序设计基础 课程作业已批改，得分85分', 1, 'SYSTEM', true, NOW()-INTERVAL '28 days', NOW()-INTERVAL '28 days'),
(4,  7,  'GRADE',     '成绩发布通知', '您的 数据结构与算法 课程练习已批改，得分92分', 3, 'SYSTEM', true, NOW()-INTERVAL '23 days', NOW()-INTERVAL '23 days'),
(5,  8,  'GRADE',     '成绩发布通知', '您的 Java程序设计基础 课程练习已批改，得分78分', 1, 'SYSTEM', false, NULL, NOW()-INTERVAL '30 days'),
(6,  7,  'DISCUSSION', '讨论回复通知', '李教授 回复了您在 Java程序设计基础 的提问', 1, 'SYSTEM', true, NOW()-INTERVAL '19 days', NOW()-INTERVAL '19 days'),
(7,  3,  'SYSTEM',    '系统公告', '本周三下午3点全体教师会议，请准时参加', NULL, 'SYSTEM', true, NOW()-INTERVAL '2 days', NOW()-INTERVAL '2 days'),
(8,  10, 'ENROLLMENT', '新学生选课通知', '周小杰 选择了您的 Web应用开发 课程', 4, 'SYSTEM', false, NULL, NOW()-INTERVAL '4 days');

-- ============================================================
-- 20. 轮播图
-- ============================================================
INSERT INTO banners (id, image_url, link_url, sort_order, enabled, created_at) VALUES
(1, 'https://cdn.microcourse.edu/banners/java-course.jpg', '/course/1', 1, true, NOW()),
(2, 'https://cdn.microcourse.edu/banners/ds-course.jpg',   '/course/2', 2, true, NOW()),
(3, 'https://cdn.microcourse.edu/banners/ml-course.jpg',  '/course/5', 3, true, NOW()),
(4, 'https://cdn.microcourse.edu/banners/welcome.jpg',     '/home',     4, true, NOW());

-- ============================================================
-- 验证数据
-- ============================================================
DO $$
BEGIN
  RAISE NOTICE '=== 数据验证 ===';
  RAISE NOTICE '部门: %', (SELECT COUNT(*) FROM departments);
  RAISE NOTICE '专业: %', (SELECT COUNT(*) FROM majors);
  RAISE NOTICE '班级: %', (SELECT COUNT(*) FROM classes);
  RAISE NOTICE '用户: % (管理员=%, 教务=%, 教师=%, 学生=%)',
    (SELECT COUNT(*) FROM users),
    (SELECT COUNT(*) FROM users WHERE role='ADMIN'),
    (SELECT COUNT(*) FROM users WHERE role='ACADEMIC'),
    (SELECT COUNT(*) FROM users WHERE role='TEACHER'),
    (SELECT COUNT(*) FROM users WHERE role='STUDENT');
  RAISE NOTICE '课程: %', (SELECT COUNT(*) FROM courses);
  RAISE NOTICE '章节: %', (SELECT COUNT(*) FROM course_chapters);
  RAISE NOTICE '视频: %', (SELECT COUNT(*) FROM videos);
  RAISE NOTICE '练习: %', (SELECT COUNT(*) FROM exercises);
  RAISE NOTICE '题目: %', (SELECT COUNT(*) FROM questions);
  RAISE NOTICE '选课: %', (SELECT COUNT(*) FROM enrollments);
  RAISE NOTICE '成绩: %', (SELECT COUNT(*) FROM grades);
  RAISE NOTICE '讨论帖: %', (SELECT COUNT(*) FROM discussion_posts);
  RAISE NOTICE '回复: %', (SELECT COUNT(*) FROM discussion_comments);
  RAISE NOTICE '评价: %', (SELECT COUNT(*) FROM course_reviews);
  RAISE NOTICE '进度: %', (SELECT COUNT(*) FROM learning_progress);
  RAISE NOTICE '通知: %', (SELECT COUNT(*) FROM notifications);
  RAISE NOTICE '轮播图: %', (SELECT COUNT(*) FROM banners);
END $$;

COMMIT;
