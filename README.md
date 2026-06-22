# 微课管理平台 (Micro-Course Management Platform)

基于 Spring Boot 3 + Vue 3 的企业级在线学习管理系统。

## 技术栈

| 层 | 技术 |
|---|------|
| 后端 | Spring Boot 3.2.12 / Java 17 / MyBatis-Plus 3.5.6 / Spring Security |
| 前端 | Vue 3.4 / Element Plus 2.5 / Pinia 2.1 / Vite 5 / Axios 1.6 |
| 数据库 | PostgreSQL 17.5 / Flyway 10.20.1 |
| 缓存 | Redis 7 / Lettuce |
| 视频 | FFmpeg / HLS (hls.js) / OpenPDF |

## 快速启动

```bash
# 1. 克隆并配置环境变量
cp .env.example .env
# 编辑 .env 填入 DB_PASSWORD / JWT_SECRET / VIDEO_SIGN_SECRET

# 2. 一键启动全栈
docker compose up -d

# 3. 访问
open http://localhost
```

## 手动启动（开发）

```bash
# 后端
cd micro-course-api
export DB_USERNAME=postgres DB_PASSWORD=*** JWT_SECRET=***
mvn spring-boot:run

# 前端
cd micro-course-admin
npm install
npm run dev
```

## 测试

```bash
# 后端测试 (228)
cd micro-course-api && mvn test

# 前端 lint
cd micro-course-admin && npm run lint

# 项目预检
bash .claude/skills/microcourse/scripts/precheck.sh
```

## 项目结构

```
micro-course-api/         # Spring Boot 后端
├── src/main/java/com/microcourse/
│   ├── controller/       # REST 控制器
│   ├── service/          # 业务逻辑层
│   ├── repository/       # MyBatis-Plus Mapper
│   ├── entity/           # 数据实体
│   ├── dto/              # 请求/响应 DTO
│   ├── config/           # Spring Security / MyBatis-Plus / 其它配置
│   ├── exception/        # 全局异常处理 + ErrorCode
│   └── util/             # 工具类 (JWT / Security / Redis)
├── src/main/resources/db/migration/  # Flyway 迁移 (V1-V72)
└── Dockerfile

micro-course-admin/       # Vue 3 前端
├── src/
│   ├── views/            # 页面组件
│   │   ├── student/      # 学生端 (CourseSquare/VideoPlayer/Profile/...)
│   │   ├── teacher/      # 教师端 (Dashboard/Grades/TeachingClasses/...)
│   │   ├── admin/        # 管理后台 (Dashboard/Settings/Logs/...)
│   │   ├── academic/     # 教务处 Dashboard
│   │   └── courses/      # 课程管理 (List/Detail/Chapters/Videos/...)
│   ├── api/              # API 封装 (Axios)
│   ├── store/            # Pinia 状态管理
│   ├── router/           # 路由配置
│   └── utils/            # 工具函数 (auth/request)
├── nginx.conf
└── Dockerfile

docker-compose.yml        # 生产部署编排
.github/workflows/ci.yml  # CI/CD 流水线
```

## 核心功能

- **课程广场**：分类导航/搜索/推荐/评分展示
- **视频播放**：HLS 流媒体/倍速/进度记忆/书签/笔记
- **随堂练习**：单/多/判/填四种题型/自动批改/错题集
- **学习统计**：总时长/完成课程/正确率趋势/连续打卡
- **证书系统**：课程完成自动颁发 OpenPDF 证书
- **徽章系统**：FIRST_COURSE/ALL_COURSES/SEVEN_DAY_STREAK
- **评价系统**：课程评分/评论/avgRating 实时聚合
- **多角色**：学生/教师/教务处/管理员 四层权限体系

## 发布历史

参见 [CHANGELOG.md](./CHANGELOG.md) — v1.17.0

## CI/CD

GitHub Actions 流水线定义在 `.github/workflows/ci.yml`：

| Job | 内容 | 门禁 |
|-----|------|------|
| **backend** | `mvn test` + JaCoCo coverage | 228 tests, 行覆盖率 ≥ 10% |
| **frontend** | ESLint + build | 0 error, build SUCCESS |
| **docker** | `docker compose build` | 镜像构建通过 |

### 本地运行 CI 检查

```bash
# 后端全套
cd micro-course-api && mvn clean test

# 前端全套
cd micro-course-admin && npm run lint && npm run build

# 预检
bash .claude/skills/microcourse/scripts/precheck.sh
```

## CI/CD 状态

- **Backend**: 228/228 tests pass · 0 failures · 0 errors · JaCoCo coverage
- **Frontend**: 0 ESLint errors · 0 ESLint warnings · `npm run build` SUCCESS
- **Precheck**: 13/13 PASS
- **Workflow**: `.github/workflows/ci.yml` (3 jobs 并行: backend / frontend / docker)

## 当前版本

**v1.16.0** (2026-06-22) — 全量交叉验证收官 · CI/CD 零警告流水线 · 228/228 tests pass

## 环境变量

| 变量 | 必须 | 说明 |
|------|:----:|------|
| `DB_USERNAME` | ✅ | PostgreSQL 用户名 |
| `DB_PASSWORD` | ✅ | PostgreSQL 密码 |
| `JWT_SECRET` | ✅ | JWT 签名密钥（64+ 字符随机串） |
| `VIDEO_SIGN_SECRET` | ✅ | 视频签名密钥（必须不同于 JWT_SECRET） |
| `REDIS_PASSWORD` | ❌ | Redis 密码（生产环境建议设置） |
| `CORS_ALLOWED_ORIGINS` | ❌ | 跨域来源（默认 localhost:80） |

## 生产部署

```bash
# 1. 配置环境变量
cp .env.example .env
# 编辑 .env 填入真实密钥

# 2. 启动
docker compose up -d

# 3. 验证健康检查
curl http://localhost/health

# 4. 查看日志
docker compose logs -f
```

### Nginx 安全配置

- API 限流: 30 请求/秒 (burst 50)
- Body 上限: 2GB（视频上传）
- 安全头: X-Content-Type-Options / X-Frame-Options / X-XSS-Protection / Referrer-Policy
- gzip 压缩: text/css/js/json + HLS mpegurl/mp2t
- 静态资源: 1 年强缓存，关闭 access_log |
