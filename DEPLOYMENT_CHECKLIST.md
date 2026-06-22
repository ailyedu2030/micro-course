# 微课平台部署检查清单

> 部署前 24 小时 & 部署前 1 小时执行，部署后 30 分钟验证

---

## 部署前 24 小时

- [ ] 代码已合并到 `main` 分支
- [ ] CI 全部通过（GitHub Actions / GitLab CI）
- [ ] 备份当前生产数据库
- [ ] 备份当前生产环境配置文件（`application-prod.yml`、环境变量）
- [ ] 通知所有利益相关者部署时间窗口
- [ ] 确认回滚方案已演练或准备就绪

---

## 部署前 1 小时

### 系统依赖检查

```bash
# PostgreSQL 版本（需 ≥ 17.5）
psql --version

# Redis 版本（需 ≥ 7）
redis-cli --version

# 磁盘空间（建议 ≥ 50GB）
df -h /

# 内存（建议 ≥ 4GB）
free -h
```

- [ ] PostgreSQL 版本 ≥ 17.5
- [ ] Redis 版本 ≥ 7
- [ ] 磁盘空间 ≥ 50GB
- [ ] 可用内存 ≥ 4GB
- [ ] 准备环境变量（见下方清单）

---

## 环境变量清单

| 变量名 | 用途 | 示例值 | 必填 | 生成方式 |
|--------|------|--------|------|----------|
| `DB_URL` | PostgreSQL 连接地址 | `jdbc:postgresql://localhost:5432/micro_course` | 是 | - |
| `DB_USERNAME` | 数据库用户名 | `microcourse_user` | 是 | - |
| `DB_PASSWORD` | 数据库密码 | `P@ssw0rd!2026` | 是 | `openssl rand -base64 32` |
| `REDIS_HOST` | Redis 主机 | `localhost` | 是 | - |
| `REDIS_PORT` | Redis 端口 | `6379` | 是 | - |
| `REDIS_PASSWORD` | Redis 密码 | `Redis!P@ss2026` | 否 | `openssl rand -base64 32` |
| `REDIS_DB` | Redis 数据库编号 | `0` | 否 | 默认 0 |
| `JWT_SECRET` | JWT 签名密钥 | `yoursecretkey32charactersminimum!` | 是 | `openssl rand -base64 32`（取前 32+ 字符） |
| `JWT_EXPIRATION` | JWT 过期时间(ms) | `1800000`（30分钟） | 否 | 默认 7200000（2小时） |
| `JWT_REFRESH_EXPIRATION` | Refresh Token 过期时间(ms) | `604800000`（7天） | 否 | 默认 604800000 |
| `VIDEO_SIGN_SECRET` | 视频签名密钥 | `VideoSign!Secret32Chars$2026` | 是 | `openssl rand -base64 32`（独立密钥，不与 JWT 共用） |
| `VIDEO_STORAGE_BASE_DIR` | 视频存储根目录 | `/data/micro-course/videos` | 是 | - |
| `VIDEO_UPLOAD_DIR` | 视频上传目录 | `/data/micro-course/videos/upload` | 否 | 默认 `uploads/videos` |
| `VIDEO_COVER_DIR` | 视频封面目录 | `/data/micro-course/covers` | 否 | 默认 `uploads/covers` |
| `VIDEO_TRANSCODE_TIMEOUT` | FFmpeg 转码超时(分钟) | `60` | 否 | 默认 60 |
| `DEEPSEEK_API_KEY` | DeepSeek API 密钥 | `sk-xxxxx` | 否 | 从 DeepSeek 平台获取 |
| `CORS_ALLOWED_ORIGINS` | CORS 允许的源（逗号分隔） | `http://localhost:5173,https://course.example.com` | 是 | 企业方提供 |
| `LOG_LEVEL` | 日志级别 | `INFO` | 否 | 默认 INFO |
| `PAY_MODE` | 支付模式 | `mock` 或 `real` | 是 | 生产设为 `real` |
| `PAYMENT_CALLBACK_SECRET` | 支付回调密钥 | `Pay!Callback$ecret32` | 支付开启时必填 | `openssl rand -base64 32` |
| `SLIDES_STORAGE_PATH` | 课件存储目录 | `/data/micro-course/slides` | 否 | 默认 `uploads/slides` |
| `SLOW_SQL_THRESHOLD_MS` | 慢 SQL 阈值(ms) | `500` | 否 | 默认 500 |
| `PLUGIN_INTERACTIVE_ENABLED` | 互动插件开关 | `true` | 否 | 默认 true |

### 生产环境额外配置（application-prod.yml）

```yaml
logging:
  level:
    com.microcourse: INFO
    org.flywaydb: WARN
    org.springframework.security: WARN
springdoc:
  api-docs:
    enabled: false
  swagger-ui:
    enabled: false
```

---

## 部署步骤

### 1. 数据库备份

```bash
# 全量备份
pg_dump -U microcourse_user -d micro_course -F c -b -v -f /backup/micro_course_$(date +%Y%m%d_%H%M%S).dump

# 验证备份文件
pg_restore --list /backup/micro_course_*.dump | head -20
```

### 2. 上传 JAR 包

```bash
# 上传最新 JAR 包到 /opt/micro-course/versions/
scp target/micro-course-api-*.jar root@server:/opt/micro-course/versions/

# 建立软链接指向当前版本
ln -sf /opt/micro-course/versions/micro-course-api-1.x.x.jar /opt/micro-course/current/micro-course-api.jar
```

### 3. 创建配置文件

```bash
# 创建环境变量文件 /opt/micro-course/env
cat > /opt/micro-course/env/.env << 'EOF'
DB_URL=jdbc:postgresql://localhost:5432/micro_course
DB_USERNAME=microcourse_user
DB_PASSWORD=your_secure_password
JWT_SECRET=your_32_char_minimum_secret_key
VIDEO_SIGN_SECRET=your_video_sign_secret_key_32chars
REDIS_HOST=localhost
REDIS_PORT=6379
CORS_ALLOWED_ORIGINS=https://course.example.com
PAY_MODE=mock
EOF
```

### 4. 启动应用

```bash
# 首次部署会自动执行 Flyway 迁移（V79 → V80 → V81）
systemctl start micro-course-api

# 查看启动日志确认 Flyway 迁移执行
journalctl -u micro-course-api -f --lines=100
```

### 5. 健康检查

```bash
curl -s http://localhost:8080/actuator/health
# 预期返回: {"status":"UP"}

# 检查 Flyway 迁移记录
psql -U microcourse_user -d micro_course -c "SELECT version, description FROM flyway_schema_history WHERE success=true ORDER BY installed_rank DESC LIMIT 5;"
```

### 6. 冒烟测试

```bash
# 运行冒烟测试脚本
bash /opt/micro-course/SMOKE_TEST.sh
```

---

## 部署后 30 分钟

- [ ] 健康检查 `curl http://localhost:8080/actuator/health` 返回 `UP`
- [ ] 日志无 `ERROR` 级别异常（允许 `WARN`）
  ```bash
  journalctl -u micro-course-api --since="30 minutes ago" | grep -i error
  ```
- [ ] 数据库迁移成功（检查 V81 已执行）
  ```bash
  psql -U microcourse_user -d micro_course -c "SELECT version, description FROM flyway_schema_history WHERE version IN ('V79','V80','V81');"
  ```
- [ ] 核心流程测试通过（运行 `SMOKE_TEST.sh`）
- [ ] 数据库连接池正常（无连接泄漏告警）
  ```bash
  curl -s http://localhost:8080/actuator/metrics/hikaricp.connections.active | jq
  ```

---

## 部署检查清单速览

```
部署前 24h:  □代码合并  □CI通过  □DB备份  □配置备份  □通知相关方
部署前 1h:   □PG版本  □Redis版本  □磁盘≥50GB  □内存≥4GB  □环境变量就绪
部署中:      □DB备份  □JAR上传  □配置创建  □启动+Flyway  □健康检查  □冒烟测试
部署后 30m:  □Health UP  □无ERROR  □V81迁移  □冒烟通过
```
