# Flyway 生产部署策略 — HTML 互动课件扩展 (V177)

## V177 迁移结构

| 文件 | 内容 | 事务 |
|------|------|------|
| `V177__slide_pages_content_type.sql` | 加列 + CHECK 约束 | 事务内（默认） |
| `V177b__slide_pages_content_type_index_concurrent.sql` | `CREATE INDEX CONCURRENTLY` | **事务外（强制）** |
| `V178__rollback_slide_pages_content_type.sql` | 回滚 | 事务内 |

## 部署顺序

### Step 1: 先执行 V177（事务内）

```bash
# cd /Users/jackie/微课平台/micro-course-api
# 保持默认 spring.flyway.execute-in-transaction=true
mvn flyway:migrate -Dflyway.locations=filesystem:src/main/resources/db/migration -Dflyway.target=177
```

### Step 2: 单独执行 V177b（事务外）

```bash
# 禁用事务，仅执行 V177b
spring.flyway.execute-in-transaction=false
mvn flyway:migrate -Dflyway.locations=filesystem:src/main/resources/db/migration -Dflyway.target=177b
```

**或手动 psql 执行**（推荐生产做法）：

```bash
psql -d micro_course -c "CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_slide_pages_content_type ON slide_pages(content_type);"
```

### Step 3: 继续后续迁移

```bash
# 恢复事务模式
spring.flyway.execute-in-transaction=true
mvn flyway:migrate
```

## 回滚

```bash
# 直接执行 V178（DROP IF EXISTS 安全可重复执行）
psql -d micro_course -f V178__rollback_slide_pages_content_type.sql
```

## 注意

- `CREATE INDEX CONCURRENTLY` 不能在 Flyway 默认事务模式中执行
- 索引不回导致功能异常（无索引时 content_type 过滤走全表扫描），
  但 V177 必须先于 V177b 执行（索引依赖列存在）
- V177b 不在事务中执行，失败不影响 V177 的字段+约束
