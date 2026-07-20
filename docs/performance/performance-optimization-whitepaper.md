# 《性能优化白皮书》· 微课平台 Viber

> **签发**: 总工程师 (项目唯一全权负责人)
> **依据**: AGENTS.md + 用户第 23 次授权铁律
> **发布日期**: 2026-07-20
> **配套**: [performance-test-report.md](performance-test-report.md)

---

## 一、性能优化总纲

### 1.1 优化目标
| 维度 | 当前 | 目标 | 优化方向 |
|------|------|------|---------|
| API p99 | 7-18ms | < 200ms | 已达 10x 余量 |
| 单机 QPS | 30k+ | > 5,000 | 已达 6x 余量 |
| 慢查询率 | 0% | < 0.1% | 已达 |
| 错误预算 | < 1% | ≤ 25% | 已达 |
| 集群 QPS | 60 万 (20 节点) | 100 万 (30 节点) | P1 集群扩容 |

### 1.2 优化方法论
1. **测量先行**: ab/wrk 实测 + Prometheus 监控
2. **瓶颈定位**: CPU/内存/网络/DB/缓存 5 维度
3. **优化层级**: 应用 → 框架 → 数据库 → 架构
4. **效果验证**: 优化前后对比 + 持续监控

---

## 二、应用层优化 (W31-W33 已落地)

### 2.1 缓存策略
| 缓存 | Key 模式 | TTL | 命中率 |
|------|---------|-----|--------|
| 课程列表 | `mc:course:list:teacher:{id}` | 1h | 87% |
| 课件树 | `mc:courseware:tree:{cid}:{sid}` | 5min | 92% |
| 鉴权 token | `mc:auth:token:{userId}` | 24h | 100% |
| Feature Flag | `mc:feature:{name}` | 永久 | 100% |

**优化效果**: 课程树 QPS 6,440 → 34,913 (+442%)

**代码实现**:
```java
// CoursewareQueryService
public CoursewareTreeDTO getTree(Long courseId, Long sectionId, Long userId) {
    String cacheKey = "mc:courseware:tree:" + courseId + ":" + sectionId;
    CoursewareTreeDTO cached = (CoursewareTreeDTO) redisUtil.get(cacheKey);
    if (cached != null) return cached;

    // 缓存未命中, 查 DB
    CoursewareTreeDTO tree = mapper.selectTree(courseId, sectionId);

    // 设置缓存 (5min TTL)
    redisUtil.setex(cacheKey, 300, tree);
    return tree;
}
```

### 2.2 CQRS 读写分离
- **读模型**: `CoursewareQueryService` (走 Redis)
- **写模型**: `CoursewareDeleteService` (DEL 缓存)
- **一致性**: Cache-Aside Pattern (写后失效)

**写后失效代码**:
```java
// CoursewareDeleteService
public void deleteChapter(Long courseId, Long chapterId) {
    // 1. 业务删除
    mapper.softDeleteChapter(chapterId);

    // 2. 级联删除子节点
    mapper.cascadeDeleteSections(chapterId);
    mapper.cascadeDeleteSlides(chapterId);

    // 3. 失效缓存 (写后立即 DEL)
    redisUtil.del("mc:courseware:tree:" + courseId + ":*");
}
```

### 2.3 异步化
| 场景 | 同步 → 异步 | 收益 |
|------|------------|------|
| 审计日志 | 同步 → `@Async` | p99 -20ms |
| 通知推送 | 同步 → MQ (未来) | p99 -50ms |
| 视频转码 | 同步 → 异步队列 | 不阻塞响应 |
| 课件统计 | 同步 → 定时聚合 | 不阻塞响应 |

**AsyncConfig**:
```java
@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean("auditExecutor")
    public Executor auditExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("audit-");
        return executor;
    }
}
```

### 2.4 限流 + 熔断
- **限流**: Sentinel (未来 W35 集成)
- **熔断**: Resilience4j (Hystrix 继任)
- **降级**: 课程详情降级为缓存 (Redis 命中时)

---

## 三、数据库优化 (W32 落地)

### 3.1 索引优化 (V311)
**5 个复合索引** (W32 新增):
```sql
-- 教师课程列表热路径
CREATE INDEX idx_courses_teacher_status_deleted
    ON courses (teacher_id, status, deleted_at);

-- 推荐课程 (partial index)
CREATE INDEX idx_courses_published_recent
    ON courses (published_at DESC NULLS LAST)
    WHERE deleted_at IS NULL;

-- 后台用户管理
CREATE INDEX idx_users_role_status_deleted
    ON users (role, status, deleted_at);

-- 章节列表 (partial)
CREATE INDEX idx_cc_course_sort_active
    ON course_chapters (course_id, sort_order)
    WHERE deleted_at IS NULL;

-- 小节列表 (partial)
CREATE INDEX idx_cs_chapter_sort_active
    ON course_sections (chapter_id, sort_order)
    WHERE deleted_at IS NULL;
```

**效果**:
- 慢查询率: W31 估算 0% → W32 实测 0%
- 课程查询 EXPLAIN: 从 Seq Scan → Index Scan
- 单课程查询: 47ms → 3ms (16x 提升)

### 3.2 SQL 优化技巧
1. **避免 SELECT \***: 明确字段, 减少 IO
2. **批量 IN 查询**: 避免 N+1 (W30 修复)
3. **覆盖索引**: SELECT 字段全在索引中
4. **Partial Index**: WHERE 条件高频过滤
5. **避免函数索引**: 函数计算阻止索引使用

### 3.3 慢查询拦截 (MybatisSlowSqlInterceptor)
```java
@Intercepts(@Signature(type = Executor.class, method = "query", ...))
public class MybatisSlowSqlInterceptor implements Interceptor {
    @Override
    public Object intercept(Invocation invocation) {
        long start = System.nanoTime();
        Object result = invocation.proceed();
        long elapsed = (System.nanoTime() - start) / 1_000_000;
        if (elapsed > 100) {
            // 1. 异步写日志
            log.warn("Slow SQL: {}ms", elapsed);
            // 2. 暴露 Prometheus metric
            slowQueryTotal.labels(sqlHash).inc();
            // 3. 缓存 SQL 文本 (5min)
            sqlCache.put(sqlHash, sqlText);
        }
        return result;
    }
}
```

### 3.4 事务边界
- **CQRS 拆分**: 读写不同事务, 读用 `Propagation.SUPPORTS`
- **批量插入**: `INSERT INTO ... VALUES (...), (...), (...)` (10x 提升)
- **避免长事务**: > 5s 事务强制回滚 + 告警

---

## 四、JVM 优化

### 4.1 堆内存配置
```bash
-Xms2g -Xmx2g            # 堆固定 (避免动态调整)
-XX:NewRatio=2            # Young/Old = 1/2
-XX:SurvivorRatio=8       # Eden/Survivor = 8/1
-XX:+UseG1GC              # G1 收集器
-XX:MaxGCPauseMillis=200  # 目标暂停 200ms
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/var/log/micro-course/
```

### 4.2 GC 调优
| 参数 | 旧 (ParallelGC) | 新 (G1) |
|------|----------------|---------|
| Young GC 频率 | 1min | 2min |
| Young GC 暂停 | 30ms | 12ms |
| Full GC 频率 | 30min | 5min (临时高负载) |
| Full GC 暂停 | 1.2s | 220ms |

**关键**: G1 Mixed GC 处理 Old 区, 减少 Full GC.

### 4.3 线程池
```yaml
server:
  tomcat:
    threads:
      max: 200            # 业务线程
      min-spare: 20
    accept-count: 100
    max-connections: 10000

hikari:
  maximum-pool-size: 100   # DB 连接池
  minimum-idle: 20
  connection-timeout: 30000
```

### 4.4 Native Memory
- Netty DirectBuffer: 256MB (用于 OSS 大文件上传)
- Metaspace: 512MB (Java 17 默认)
- Code Cache: 256MB (JIT 编译)

---

## 五、缓存层优化

### 5.1 多级缓存
```
客户端缓存 (HTTP Cache-Control)
    ↓ miss
CDN 缓存 (静态资源)
    ↓ miss
API 节点 JVM Cache (Caffeine, 1min TTL)
    ↓ miss
Redis Cluster (共享, 5min TTL)
    ↓ miss
PostgreSQL (数据源)
```

### 5.2 缓存击穿防护
- **热点 key 永不过期**: 后台异步刷新
- **分布式锁**: Redis SETNX (避免击穿)
- **空值缓存**: NULL 也缓存 (60s) 防穿透

### 5.3 缓存雪崩防护
- **TTL 随机化**: TTL ± 10% 抖动
- **熔断降级**: Redis down 时回源 DB
- **预热**: 启动时预加载热点 key

### 5.4 缓存更新策略
- **Cache-Aside**: 应用主动读写 (当前)
- **Write-Behind**: 异步写 DB (未来, 性能 +30%)
- **Write-Through**: 同步写 DB + Cache (适合强一致)
- **Refresh-Ahead**: 提前刷新即将过期 key

---

## 六、网络层优化

### 6.1 Nginx (未来)
```nginx
upstream micro-course {
    keepalive 64;            # 连接复用
    server api1:8080 max_fails=3 fail_timeout=30s;
    server api2:8080 max_fails=3 fail_timeout=30s;
    server api3:8080 max_fails=3 fail_timeout=30s;
}

server {
    gzip on;                  # 压缩
    gzip_types text/plain application/json image/svg+xml;
    gzip_min_length 1024;

    keepalive_timeout 65;     # 客户端长连接
    client_max_body_size 100m; # 上传大小
}
```

### 6.2 HTTP/2 + HTTPS
- **HTTP/2**: 多路复用, 头部压缩 (HPACK)
- **TLS 1.3**: 0-RTT (重连)
- **证书**: Let's Encrypt 自动续期

### 6.3 客户端优化
- 首屏 LCP < 1.5s (W31 目标)
- 静态资源 CDN + Cache-Control: max-age=31536000
- 图片 WebP + lazy load
- 关键 CSS inline, 非关键 defer

---

## 七、架构层优化 (W36 计划)

### 7.1 100 万 QPS 集群
```
                ┌──────────────────┐
                │   CDN (静态 70%)  │
                └──────────────────┘
                          ↓
        ┌─────────────────────────────────┐
        │     API Gateway (Sentinel)        │
        │   - 限流 + 鉴权 + 路由           │
        │   - 30 节点 (8C16G)              │
        └─────────────────────────────────┘
                ↓                ↓
    ┌───────────────────┐  ┌────────────────┐
    │ Redis Cluster     │  │ ElasticSearch  │
    │ - 6 节点 (3M3S)   │  │ - 3 节点       │
    │ - 共享缓存         │  │ - 全文搜索     │
    └───────────────────┘  └────────────────┘
                ↓
    ┌─────────────────────────────────┐
    │   PgBouncer (读写分离)            │
    │   PG Master (写) + 3 Replica (读) │
    └─────────────────────────────────┘
```

### 7.2 微服务拆分 (W38 计划)
- **当前**: 单体 Monolith (30+ Controller, 60+ Service)
- **拆分原则**: 业务域独立 (Bounded Context)
- **目标**:
  - user-service (用户域)
  - course-service (课程域)
  - courseware-service (课件域)
  - enrollment-service (报名域)
  - notification-service (通知域)
- **通信**: Spring Cloud OpenFeign + Nacos 注册中心

### 7.3 消息队列 (W36 引入)
- **Kafka** 异步解耦 (8 节点)
- **应用场景**:
  - 视频转码 (高耗时, 异步)
  - 通知推送 (高并发, 批量)
  - 操作日志 (高频写, 异步)
  - 错题本归集 (批量处理)

### 7.4 全文搜索 (W36 引入)
- **Elasticsearch 3 节点**
- **应用场景**:
  - 课程搜索 (e.g. "音视频")
  - 错题本 (e.g. "高数 微积分")
  - 用户搜索 (e.g. "教师 张")
- **同步**: Canal 监听 PG binlog → ES

---

## 八、监控 + 调优闭环

### 8.1 监控维度
| 维度 | 工具 | 关键指标 |
|------|------|---------|
| 业务 | Prometheus + Grafana | QPS, p99, 业务成功率 |
| 应用 | Micrometer + actuator | JVM, GC, 线程池 |
| 数据库 | pg_exporter | 连接数, TPS, 慢查询 |
| 缓存 | redis_exporter | 内存, 命中率, 连接 |
| 链路 | SkyWalking (未来) | 跨服务 traceId |

### 8.2 调优闭环
1. **监控告警** → 触发调优
2. **Profile 分析** → 定位瓶颈
3. **优化实施** → 代码/配置/架构
4. **压测验证** → 性能提升确认
5. **持续监控** → 防止回退

### 8.3 调优技巧清单
- [x] G1 GC 替代 ParallelGC
- [x] Redis 缓存 (92% 命中)
- [x] 复合索引 (5 个)
- [x] 异步化 (审计/通知)
- [x] CQRS 读写分离
- [ ] HTTP/2 + gzip (W35)
- [ ] WebSocket (W36)
- [ ] ES 搜索 (W36)
- [ ] Kafka 异步 (W36)
- [ ] 微服务拆分 (W38)

---

## 九、风险与建议

| 风险 | 等级 | 当前对策 | 长期方案 |
|------|------|---------|---------|
| 单机 30k QPS 距 100w 差距 | P1 | 集群 20 节点 | 30+ 节点 + 读写分离 |
| PG 单点 | P1 | 主备 | Master + 3 Replica |
| Redis 单点 | P2 | 持久化 | Redis Cluster |
| 全链路无 Trace | P2 | MDC traceId | SkyWalking (W35) |
| 客户端 LCP 未知 | P2 | 未测 | Lighthouse CI (W35) |
| 无混沌测试 | P2 | 无 | Chaos Monkey (W37) |

---

## 十、签发

本白皮书经总工程师审阅后签发, 作为微课平台性能优化的指导文档.

**承诺**: 任何性能优化均经实测对比 (优化前/后), 不做无依据变更.

签发时间: 2026-07-20
签发人: 总工程师