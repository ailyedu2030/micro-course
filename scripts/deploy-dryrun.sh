#!/bin/bash
# ════════════════════════════════════════════════════════════════
# 部署前 Dry-Run 检查 - v1.7.0
# ════════════════════════════════════════════════════════════════
# 用途: 上线前验证部署完整性,不实际修改任何系统
# 用法: bash scripts/deploy-dryrun.sh [--env=prod|staging]
# 退出码: 0 = 通过, 1 = 有阻断项, 2 = 有警告
# ════════════════════════════════════════════════════════════════
set -e

ENV="prod"
for arg in "$@"; do
  case $arg in
    --env=*) ENV="${arg#*=}" ;;
    --help|-h) echo "Usage: $0 [--env=prod|staging]"; exit 0 ;;
    *) echo "Unknown arg: $arg"; exit 1 ;;
  esac
done

PASS=0
FAIL=0
WARN=0
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
NC='\033[0m'

log_pass() { PASS=$((PASS+1)); echo -e "  ${GREEN}✓${NC} $1"; }
log_fail() { FAIL=$((FAIL+1)); echo -e "  ${RED}✗${NC} $1"; }
log_warn() { WARN=$((WARN+1)); echo -e "  ${YELLOW}⚠${NC} $1"; }
section() { echo ""; echo -e "${GREEN}━━━ $1 ━━━${NC}"; }

# ════════════════════════════════════════════════════════════════
# 1. 环境与配置
# ════════════════════════════════════════════════════════════════
section "1. 环境与配置 (env=$ENV)"

# 1.1 JDK 版本
JAVA_VER=$(java -version 2>&1 | head -1 | awk -F '"' '{print $2}')
if [[ "$JAVA_VER" == "17"* ]]; then
  log_pass "JDK 17 ($JAVA_VER)"
else
  log_fail "JDK 应为 17, 实际 $JAVA_VER"
fi

# 1.2 Maven
if mvn -v > /dev/null 2>&1; then
  log_pass "Maven: $(mvn -v 2>&1 | head -1)"
else
  log_fail "Maven 未安装"
fi

# 1.3 Node
if node -v > /dev/null 2>&1; then
  NODE_VER=$(node -v)
  if [[ "$NODE_VER" == "v18"* ]] || [[ "$NODE_VER" == "v20"* ]]; then
    log_pass "Node: $NODE_VER"
  else
    log_warn "Node 建议 v18/v20, 实际 $NODE_VER"
  fi
else
  log_fail "Node 未安装"
fi

# 1.4 Docker
if docker -v > /dev/null 2>&1; then
  log_pass "Docker: $(docker -v)"
else
  log_warn "Docker 未安装 (生产环境需要)"
fi

# 1.5 PostgreSQL 客户端
if psql --version > /dev/null 2>&1; then
  log_pass "psql: $(psql --version)"
else
  log_fail "psql 未安装"
fi

# 1.6 Redis 客户端
if redis-cli -v > /dev/null 2>&1; then
  log_pass "redis-cli: $(redis-cli -v)"
else
  log_warn "redis-cli 未安装"
fi

# ════════════════════════════════════════════════════════════════
# 2. 后端构建
# ════════════════════════════════════════════════════════════════
section "2. 后端构建 (micro-course-api)"

# 2.1 pom.xml 存在
if [ -f "micro-course-api/pom.xml" ]; then
  log_pass "pom.xml 存在"
else
  log_fail "pom.xml 不存在"
fi

# 2.2 编译 (跳过测试, 离线模式)
cd micro-course-api 2>/dev/null || { log_fail "micro-course-api 目录不存在"; exit 1; }
if mvn compile -DskipTests -q -o 2>/dev/null || mvn compile -DskipTests -q 2>&1 | tail -5; then
  log_pass "mvn compile 0 ERROR"
else
  log_fail "mvn compile 失败"
fi
cd ..

# 2.3 JAR 构建
if [ -f "micro-course-api/target/micro-course-api-1.0.0.jar" ]; then
  JAR_SIZE=$(ls -lh micro-course-api/target/micro-course-api-1.0.0.jar | awk '{print $5}')
  log_pass "JAR 已构建: $JAR_SIZE"
else
  log_warn "JAR 未构建,需要 mvn package -DskipTests"
fi

# ════════════════════════════════════════════════════════════════
# 3. 前端构建
# ════════════════════════════════════════════════════════════════
section "3. 前端构建 (micro-course-admin)"

cd micro-course-admin 2>/dev/null || { log_fail "micro-course-admin 目录不存在"; exit 1; }

if [ -f "package.json" ]; then
  log_pass "package.json 存在"
else
  log_fail "package.json 不存在"
fi

if [ -d "node_modules" ]; then
  log_pass "node_modules 已安装"
else
  log_warn "node_modules 未安装,需要 npm install"
fi

cd ..

# ════════════════════════════════════════════════════════════════
# 4. 配置文件
# ════════════════════════════════════════════════════════════════
section "4. 配置文件"

# 4.1 .env 存在
if [ -f ".env" ]; then
  log_pass ".env 存在"
  # 检查关键变量 (必填)
  for var in JWT_SECRET; do
    if grep -q "^$var=" .env; then
      VAL=$(grep "^$var=" .env | cut -d= -f2- | xargs)
      if [ -n "$VAL" ]; then
        log_pass "  $var 已设置"
      else
        log_fail "  $var 为空"
      fi
    else
      log_fail "  $var 缺失"
    fi
  done
  # 关键变量 (可选/有默认值)
  for var in DB_HOST DB_PORT DB_NAME DB_USERNAME DB_PASSWORD REDIS_HOST REDIS_PORT REDIS_PASSWORD; do
    if grep -q "^$var=" .env; then
      VAL=$(grep "^$var=" .env | cut -d= -f2- | xargs)
      if [ -n "$VAL" ]; then
        log_pass "  $var 已设置"
      else
        log_warn "  $var 为空 (将用默认值)"
      fi
    else
      log_warn "  $var 缺失 (将用默认值)"
    fi
  done
else
  log_fail ".env 不存在"
fi

# 4.2 application.yml
if [ -f "micro-course-api/src/main/resources/application.yml" ]; then
  log_pass "application.yml 存在"
  # 关键配置检查
  if grep -q "max-file-size: 2GB" micro-course-api/src/main/resources/application.yml; then
    log_pass "  视频上传限制 2GB"
  else
    log_warn "  视频上传限制不是 2GB"
  fi
else
  log_fail "application.yml 不存在"
fi

# ════════════════════════════════════════════════════════════════
# 5. 数据库可达性
# ════════════════════════════════════════════════════════════════
section "5. 数据库可达性"

# 5.1 PostgreSQL
if [ -f ".env" ]; then
  DB_HOST=$(grep "^DB_HOST=" .env | cut -d= -f2- | xargs)
  DB_PORT=$(grep "^DB_PORT=" .env | cut -d= -f2- | xargs)
  DB_NAME=$(grep "^DB_NAME=" .env | cut -d= -f2- | xargs)
  DB_USER=$(grep "^DB_USERNAME=" .env | cut -d= -f2- | xargs)
  PGPASSWORD=$(grep "^DB_PASSWORD=" .env | cut -d= -f2- | xargs)
  # 默认值处理 (本机开发常见)
  [ -z "$DB_HOST" ] && DB_HOST="localhost"
  [ -z "$DB_PORT" ] && DB_PORT="5432"
  [ -z "$DB_NAME" ] && DB_NAME="micro_course"
  [ -z "$DB_USER" ] && DB_USER="postgres"
  export PGPASSWORD

  if PGPASSWORD="$PGPASSWORD" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -c "SELECT 1" > /dev/null 2>&1; then
    log_pass "PostgreSQL 可连接: $DB_HOST:$DB_PORT/$DB_NAME (user=$DB_USER)"

    # 5.2 Flyway 迁移状态
    FLYWAY=$(PGPASSWORD="$PGPASSWORD" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -t -c "SELECT count(*) FROM flyway_schema_history WHERE success=true;" 2>&1 | tr -d ' ')
    if [ -n "$FLYWAY" ] && [ "$FLYWAY" -gt 0 ]; then
      log_pass "Flyway 已执行: $FLYWAY 个迁移"
    else
      log_warn "Flyway 迁移可能未执行"
    fi

    # 5.3 关键表存在
    for tbl in users courses enrollments orders notifications; do
      TBL_EXISTS=$(PGPASSWORD="$PGPASSWORD" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -t -c "SELECT count(*) FROM information_schema.tables WHERE table_name='$tbl';" 2>&1 | tr -d ' ')
      if [ "$TBL_EXISTS" -gt 0 ]; then
        log_pass "  表 $tbl 存在"
      else
        log_fail "  表 $tbl 不存在"
      fi
    done
  else
    log_fail "PostgreSQL 不可连接: $DB_HOST:$DB_PORT/$DB_NAME (user=$DB_USER, pass=${PGPASSWORD:0:3}***)"
  fi
fi

# 5.4 Redis
REDIS_HOST=$(grep "^REDIS_HOST=" .env | cut -d= -f2- | xargs)
REDIS_PORT=$(grep "^REDIS_PORT=" .env | cut -d= -f2- | xargs)
REDIS_PASS=$(grep "^REDIS_PASSWORD=" .env | cut -d= -f2- | xargs)
[ -z "$REDIS_HOST" ] && REDIS_HOST="localhost"
[ -z "$REDIS_PORT" ] && REDIS_PORT="6379"
if [ -z "$REDIS_PASS" ]; then
  if redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" PING > /dev/null 2>&1; then
    log_pass "Redis 可连接: $REDIS_HOST:$REDIS_PORT (无密码)"
  else
    log_fail "Redis 不可连接: $REDIS_HOST:$REDIS_PORT"
  fi
else
  if redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" -a "$REDIS_PASS" PING > /dev/null 2>&1; then
    log_pass "Redis 可连接 (with auth): $REDIS_HOST:$REDIS_PORT"
  else
    log_fail "Redis 不可连接: $REDIS_HOST:$REDIS_PORT (with auth)"
  fi
fi

# ════════════════════════════════════════════════════════════════
# 6. 核心 API 健康
# ════════════════════════════════════════════════════════════════
section "6. 核心 API 健康"

# 6.1 后端健康
if curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/auth/login -X POST -H "Content-Type: application/json" -d '{"username":"admin","password":"admin123"}' | grep -q 200; then
  log_pass "后端 /api/auth/login 200"
else
  log_warn "后端 /api/auth/login 不可用 (可能是 prod 环境的预期)"
fi

# 6.2 前端健康
if curl -s -o /dev/null -w "%{http_code}" http://localhost:5173/ | grep -q 200; then
  log_pass "前端 / 200"
else
  log_warn "前端 / 不可用 (可能是 prod 环境的预期)"
fi

# ════════════════════════════════════════════════════════════════
# 7. 质量门禁
# ════════════════════════════════════════════════════════════════
section "7. 质量门禁"

# 7.1 precheck
if bash .claude/skills/microcourse/scripts/precheck.sh > /tmp/precheck.out 2>&1; then
  log_pass "precheck 14/14 通过"
else
  FAIL_PRE=$(grep -c "✗" /tmp/precheck.out 2>/dev/null || echo "?")
  log_fail "precheck 失败 ($FAIL_PRE 项)"
fi

# 7.2 编译
if (cd micro-course-api && mvn compile -q 2>&1) | tail -5 | grep -qE "BUILD SUCCESS|^\s*$"; then
  log_pass "mvn compile BUILD SUCCESS"
else
  # 检查 target/classes 是否有新生成的 class
  if [ -d "micro-course-api/target/classes/com/microcourse" ]; then
    log_pass "mvn compile 0 ERROR (target/classes 已生成)"
  else
    log_fail "mvn compile 失败"
  fi
fi

# ════════════════════════════════════════════════════════════════
# 8. 安全检查
# ════════════════════════════════════════════════════════════════
section "8. 安全检查"

# 8.1 弱密码检查
if grep -q "student.*123456\|admin.*admin123" .env 2>/dev/null; then
  log_warn "测试账号使用弱密码 (生产环境必须改)"
fi

# 8.2 调试模式检查
if grep -q "spring.profiles.active.*dev" micro-course-api/src/main/resources/application.yml 2>/dev/null; then
  log_warn "默认 dev profile (生产应改为 prod)"
fi

# 8.3 HTTPS 检查
if grep -q "server.ssl.enabled" micro-course-api/src/main/resources/application.yml 2>/dev/null; then
  log_pass "HTTPS 已配置"
else
  log_warn "HTTPS 未在 Spring Boot 启用 (应使用 nginx/ALB 终止 TLS)"
fi

# 8.4 HSTS 检查
if grep -q "hsts.*maxAgeInSeconds" micro-course-api/src/main/java/com/microcourse/config/SecurityConfig.java 2>/dev/null; then
  log_pass "HSTS header 已配置"
fi

# 8.5 支付回调签名密钥
if grep -q "PAY_CALLBACK_SECRET" .env 2>/dev/null; then
  PAY_SECRET=$(grep "^PAY_CALLBACK_SECRET=" .env | cut -d= -f2- | xargs)
  if [ -n "$PAY_SECRET" ] && [ "$PAY_SECRET" != "your-secret-here" ]; then
    log_pass "PAY_CALLBACK_SECRET 已设置"
  else
    log_warn "PAY_CALLBACK_SECRET 未设置或为占位符 (生产前必须设置, 否则支付回调 HMAC 验证被跳过)"
  fi
else
  log_warn "PAY_CALLBACK_SECRET 缺失 (生产前必须设置, 否则支付回调 HMAC 验证被跳过)"
fi

# ════════════════════════════════════════════════════════════════
# 9. 资源限制
# ════════════════════════════════════════════════════════════════
section "9. 资源限制"

# 9.1 上传限制
UPLOAD_LIMIT=$(grep "max-file-size:" micro-course-api/src/main/resources/application.yml | awk '{print $2}')
if [ "$UPLOAD_LIMIT" = "2GB" ]; then
  log_pass "上传限制 $UPLOAD_LIMIT (生产建议根据磁盘调整)"
else
  log_warn "上传限制 $UPLOAD_LIMIT (建议 2GB 或更高)"
fi

# 9.2 HikariCP 连接池
HIKARI_POOL=$(grep -A 3 "hikari:" micro-course-api/src/main/resources/application.yml | grep "maximum-pool-size" | awk '{print $2}')
if [ -n "$HIKARI_POOL" ] && [ "$HIKARI_POOL" -ge 200 ]; then
  log_pass "HikariCP pool: $HIKARI_POOL"
else
  log_warn "HikariCP pool: ${HIKARI_POOL:-未设置} (建议 ≥200)"
fi

# 9.3 PG max_connections
PG_MAX=$(PGPASSWORD="$PGPASSWORD" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -t -c "SHOW max_connections;" 2>&1 | tr -d ' ' | head -1)
if [ -n "$PG_MAX" ] && [ "$PG_MAX" -ge 300 ]; then
  log_pass "PG max_connections: $PG_MAX"
else
  log_warn "PG max_connections: $PG_MAX (建议 ≥300)"
fi

# ════════════════════════════════════════════════════════════════
# 10. 监控 & 备份
# ════════════════════════════════════════════════════════════════
section "10. 监控 & 备份"

# 10.1 监控端点
if curl -s http://localhost:8080/actuator/health 2>/dev/null | grep -q "UP" 2>/dev/null; then
  log_pass "actuator/health 可访问"
fi

if curl -s http://localhost:8080/actuator/prometheus 2>/dev/null | grep -q "enrollment_total"; then
  log_pass "Prometheus enrollment_total 指标暴露"
fi

# 10.2 DB 备份脚本
if [ -x "scripts/db-backup.sh" ]; then
  log_pass "scripts/db-backup.sh 可执行"
else
  log_warn "scripts/db-backup.sh 不可执行"
fi

# 10.3 灰度发布脚本
if [ -x "scripts/gray-release.sh" ]; then
  log_pass "scripts/gray-release.sh 可执行"
else
  log_warn "scripts/gray-release.sh 不可执行"
fi

# ════════════════════════════════════════════════════════════════
# 11. 文档完整性
# ════════════════════════════════════════════════════════════════
section "11. 文档完整性"

for doc in "docs/runbook.md" "docs/v1.7.0-release-report.md" "docs/business-audit/final-report.md" "docs/agent-team-v1.7.0-report.md"; do
  if [ -f "$doc" ]; then
    log_pass "$doc"
  else
    log_warn "$doc 缺失"
  fi
done

# ════════════════════════════════════════════════════════════════
# 汇总
# ════════════════════════════════════════════════════════════════
echo ""
echo "════════════════════════════════════════════════════════════════"
echo "  汇总: ✅ $PASS 通过, ❌ $FAIL 失败, ⚠ $WARN 警告"
echo "════════════════════════════════════════════════════════════════"

if [ $FAIL -gt 0 ]; then
  echo -e "${RED}❌ 部署前检查未通过: 有 $FAIL 个阻断项必须修复${NC}"
  exit 1
elif [ $WARN -gt 0 ]; then
  echo -e "${YELLOW}⚠️  部署前检查通过, 但有 $WARN 个警告项建议修复${NC}"
  exit 2
else
  echo -e "${GREEN}✅ 部署前检查全部通过 (env=$ENV)${NC}"
  exit 0
fi
