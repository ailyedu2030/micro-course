# 《模块交互流程图》· 微课平台 Viber

> **签发**: 总工程师 (项目唯一全权负责人)
> **依据**: AGENTS.md + 用户第 23 次授权铁律
> **配套**: [module-responsibility-specification.md](module-responsibility-specification.md)

---

## 1. 学员学习路径 (核心流程)

```mermaid
sequenceDiagram
    autonumber
    participant U as 学员 (H5/小程序)
    participant GW as 网关 (未来)
    participant AuthC as AuthController
    participant AuthS as AuthService
    participant JwtU as JwtUtil
    participant EC as EnrollmentController
    participant ES as EnrollmentService
    participant LPC as LearningProgressController
    participant LPS as LearningProgressService
    participant CWC as CoursewareQueryController
    participant CWS as CoursewareQueryService
    participant R as Redis
    participant DB as PostgreSQL

    U->>GW: POST /api/auth/login {username, password}
    GW->>AuthC: 转发 + X-Trace-Id
    AuthC->>AuthS: login(user)
    AuthS->>DB: SELECT user WHERE username=?
    DB-->>AuthS: User row
    AuthS->>JwtU: generateToken(userId, role)
    JwtU-->>AuthS: accessToken
    AuthS->>R: SET mc:auth:token:{userId} = {accessToken} EX 86400
    AuthS-->>AuthC: R{accessToken, refreshToken}
    AuthC-->>U: 200 {accessToken, refreshToken}

    Note over U,R: ──── 鉴权已就绪 ────

    U->>GW: GET /api/courses?teacherId=35<br/>Authorization: Bearer token
    GW->>EC: 转发
    EC->>ES: listEnrollments(userId)
    ES->>R: GET mc:enroll:user:{userId}
    alt 缓存命中
        R-->>ES: [enrollment1, ...]
    else 缓存未命中
        ES->>DB: SELECT enrollments + courses
        DB-->>ES: list
        ES->>R: SET mc:enroll:user:{userId} EX 3600
    end
    ES-->>EC: 报名列表
    EC-->>U: 200 {courses: [...]}

    U->>LPC: GET /api/learning-progress/{courseId}
    LPC->>LPS: getProgress(userId, courseId)
    LPS->>DB: SELECT progress + chapter + section
    DB-->>LPS: rows
    LPS-->>LPC: 进度数据
    LPC-->>U: 200 {progress: 75%, completedSections: [...]}

    U->>CWC: GET /api/courses/{cid}/courseware/{sectionId}
    CWC->>CWS: getTree(courseId, sectionId, userId)
    CWS->>R: GET mc:courseware:tree:{cid}:{sectionId}
    alt 缓存命中
        R-->>CWS: tree JSON
    else 缓存未命中
        CWS->>DB: SELECT chapter + section + slide_ppt + slide_html
        DB-->>CWS: rows
        CWS->>R: SET EX 300
    end
    CWS-->>CWC: 课件树
    CWC-->>U: 200 {tree: [...]}
```

---

## 2. 教师创建课件流程 (CQRS + 状态机)

```mermaid
sequenceDiagram
    autonumber
    participant T as 教师
    participant LC as LessonController
    participant LS as LessonService
    participant CSM as CourseStateMachine
    participant DB as PostgreSQL
    participant R as Redis
    participant SS as SectionService
    participant CQS as CoursewareQueryService
    participant Audit as AuditLogWriter

    T->>LC: POST /api/courses/{cid}/lessons<br/>{title, sortOrder}
    LC->>LS: createLesson(courseId, lessonDto)
    LS->>CSM: checkStateTransition(DRAFT → DRAFT)
    CSM-->>LS: OK
    LS->>DB: BEGIN
    LS->>DB: INSERT course_chapters
    LS->>Audit: logAction("create", "chapter", chapterId)
    LS->>DB: COMMIT
    LS->>R: DEL mc:courseware:tree:{cid}:*
    LS-->>LC: chapterId
    LC-->>T: 200 {chapterId: 205}

    T->>LC: POST /api/courses/{cid}/sections<br/>{chapterId, title}
    LC->>LS: createSection(courseId, sectionDto)
    LS->>SS: createSection(...)
    SS->>DB: BEGIN
    SS->>DB: INSERT course_sections
    SS->>Audit: logAction("create", "section", sectionId)
    SS->>DB: COMMIT
    SS->>R: DEL mc:courseware:tree:{cid}:*
    SS-->>LC: sectionId
    LC-->>T: 200 {sectionId: 44}

    T->>LC: POST /api/courses/{cid}/slides/ppt<br/>{sectionId, imageUrl, pageNumber}
    LC->>LS: createPptSlide(...)
    LS->>DB: INSERT slide_ppt_pages
    LS->>R: DEL mc:courseware:tree:{cid}:*
    LS-->>LC: pageId
    LC-->>T: 200 {pageId: 11}

    Note over T,CQS: ──── 学员侧自动看到树 ────

    T->>CQS: (lazy load) GET /api/courses/{cid}/courseware/{sectionId}
    CQS->>R: GET mc:courseware:tree:{cid}:{sectionId}
    R-->>CQS: 缓存未命中 (已 DEL)
    CQS->>DB: SELECT chapter + section + slide_ppt + slide_html
    DB-->>CQS: 完整树
    CQS->>R: SET EX 300
    CQS-->>T: 200 {tree: [...]}
```

---

## 3. 课件删除流程 (W31 IDOR 防御)

```mermaid
sequenceDiagram
    autonumber
    participant T as 教师 sytafe
    participant CDC as CoursewareDeleteController
    participant CDS as CoursewareDeleteService
    participant SU as SecurityUtil
    participant SecCtx as SecurityContext
    participant DB as PostgreSQL
    participant R as Redis
    participant Audit as AuditLogWriter
    participant CQS as CoursewareQueryService

    T->>CDC: DELETE /api/courses/79/courseware/chapters/205<br/>Authorization: Bearer {token}
    CDC->>SU: getCurrentUserId()
    SU->>SecCtx: Authentication.getPrincipal()
    SecCtx-->>SU: userId=35
    SU-->>CDC: userId=35

    CDC->>CDS: deleteChapter(courseId=79, chapterId=205, userId=35)
    CDS->>DB: SELECT course WHERE id=79
    DB-->>CDS: Course(teacherId=35, status=DRAFT)
    CDS->>SU: isOwnerOrAdmin(teacherId=35, currentUser=35)
    SU-->>CDS: true (通过)

    CDS->>DB: BEGIN
    CDS->>DB: UPDATE course_chapters SET deleted_at=NOW() WHERE id=205
    CDS->>DB: UPDATE course_sections SET deleted_at=NOW() WHERE chapter_id=205
    CDS->>DB: DELETE FROM slide_ppt_pages WHERE chapter_id=205
    CDS->>DB: DELETE FROM slide_html_segments WHERE chapter_id=205
    CDS->>Audit: logAction("delete", "chapter", chapterId=205)
    CDS->>DB: COMMIT

    CDS->>R: DEL mc:courseware:tree:79:*
    CDS-->>CDC: 200 {deletedCount: 1, cascadeCount: 4}
    CDC-->>T: 200 {code: 200, message: "删除成功", data: {deletedCount: 1, cascadeCount: 4}}

    Note over T,CQS: ──── IDOR 防御失败场景 ────

    T->>CDC: DELETE /api/courses/80/courseware/chapters/1<br/>(courseId=80 不属于 user 35)
    CDC->>CDS: deleteChapter(80, 1, 35)
    CDS->>DB: SELECT course WHERE id=80
    DB-->>CDS: Course(teacherId=99, status=PUBLISHED)
    CDS->>SU: isOwnerOrAdmin(teacherId=99, currentUser=35)
    SU-->>CDS: false
    CDS-->>CDC: throw BusinessException(RESOURCE_NOT_FOUND, 9006)
    CDC-->>T: 200 {code: 9006, message: "资源不存在", data: null}
    Note right of T: 故意不暴露存在性 (防枚举)
```

---

## 4. 视频播放流程 (签名 URL + 流鉴权)

```mermaid
sequenceDiagram
    autonumber
    participant S as 学员
    participant VC as VideoController
    participant VAS as VideoAccessService
    participant VS as VideoService
    participant VSU as VideoSignUtil
    participant R as Redis
    participant OSS as 阿里云 OSS
    participant DB as PostgreSQL

    S->>VC: GET /api/videos/{videoId}<br/>Authorization: Bearer {token}
    VC->>VAS: canAccess(userId, videoId)
    VAS->>DB: SELECT enrollment WHERE userId=? AND courseId=?
    DB-->>VAS: enrollment
    alt 已报名
        VAS->>R: GET mc:video:access:{userId}:{videoId}
        R-->>VAS: null (首次)
        VAS->>R: SET EX 60
        VAS-->>VC: allowed=true
    else 未报名
        VAS-->>VC: throw 10003 FORBIDDEN
    end

    VC->>VS: getVideo(videoId)
    VS->>DB: SELECT video WHERE id=?
    DB-->>VS: Video(ossKey, status=READY)
    VS->>VSU: generateSignedUrl(ossKey, ttl=3600)
    VSU-->>VS: signedUrl
    VS-->>VC: VideoMetadata
    VC-->>S: 200 {url: "https://oss.aliyuncs.com/...?Expires=...", duration: 1200}

    S->>OSS: GET signedUrl (Range: bytes=0-1048575)
    OSS-->>S: 206 Partial Content (1MB)
    S->>S: 播放 (HTML5 video)
```

---

## 5. 鉴权 + 限流流程 (Spring Security + JWT)

```mermaid
sequenceDiagram
    autonumber
    participant C as 客户端
    participant SF as SecurityFilterChain
    participant JAF as JwtAuthenticationFilter
    participant AAF as ApiKeyAuthenticationFilter
    participant JU as JwtUtil
    participant R as Redis
    participant CTRL as Controller
    participant SU as SecurityUtil
    participant SecCtx as SecurityContext

    C->>SF: HTTP Request
    SF->>JAF: doFilter (Authorization: Bearer)
    JAF->>JU: parseToken(token)
    alt token 有效
        JU-->>JAF: {userId, role, exp}
        JAF->>R: GET mc:auth:token:{userId}
        alt token 匹配
            R-->>JAF: tokenValue
            JAF->>SecCtx: setAuthentication(userId, role)
        else token 不匹配 (黑名单)
            R-->>JAF: null
            JAF-->>C: 401 UNAUTHORIZED
        end
    else token 无效/过期
        JU-->>JAF: InvalidTokenException
        JAF->>AAF: doFilter (X-Api-Key)
        AAF->>R: GET mc:api:key:{apiKey}
        alt apiKey 匹配
            R-->>AAF: principal=internal-service
            AAF->>SecCtx: setAuthentication
        else apiKey 不匹配
            AAF-->>C: 401
        end
    end

    SF->>CTRL: invoke (authenticated)
    CTRL->>SU: getCurrentUserId()
    SU->>SecCtx: getPrincipal()
    SecCtx-->>SU: userId
    SU-->>CTRL: userId
    CTRL->>CTRL: 业务处理
    CTRL-->>C: 200 R{data}
```

---

## 6. 慢查询监控流程 (MyBatis 拦截)

```mermaid
sequenceDiagram
    autonumber
    participant S as Service
    participant MP as MyBatis Plus
    participant MSI as MybatisSlowSqlInterceptor
    participant DB as PostgreSQL
    participant AL as 异步日志
    participant Prom as Prometheus exporter
    participant Cache as Caffeine

    S->>MP: mapper.selectList(queryWrapper)
    MP->>MSI: intercept(invocation)
    MSI->>MSI: startTime = System.nanoTime()
    MSI->>DB: SQL: SELECT * FROM courses WHERE teacher_id=? AND deleted_at IS NULL
    DB-->>MSI: 10 rows
    MSI->>MSI: elapsed = 127ms
    alt elapsed > 100ms
        MSI->>AL: log.warn("Slow SQL: {} ({}ms)", sql, elapsed)
        AL->>Prom: 暴露 slow_query_total{sql="SELECT * FROM courses..."} = 1
        AL->>Cache: put("slow_sql_hash", sqlText, 5min TTL)
    else elapsed ≤ 100ms
        MSI->>Prom: 暴露 query_total{result="success"} = N
    end
    MSI->>MP: return result
    MP-->>S: List<Course>
```

---

## 7. 课件同步到 OSS + 触发转码

```mermaid
sequenceDiagram
    autonumber
    participant T as 教师
    participant SAC as StorageApplicationController
    participant SAS as StorageApplicationService
    participant R as Redis
    participant OSS as 阿里云 OSS
    participant VTS as VideoTranscodeService
    participant Q as 异步队列
    participant TR as OSS 回调
    participant NS as NotificationService

    T->>SAC: POST /api/storage-applications {type: VIDEO, file: video.mp4}
    SAC->>SAS: applyForStorage(userId, dto)
    SAS->>OSS: generateUploadUrl(ossKey, ttl=3600)
    OSS-->>SAS: signedUrl
    SAS->>R: SET mc:storage:apply:{applyId} = {PENDING} EX 86400
    SAS-->>SAC: {applyId, uploadUrl, fields}
    SAC-->>T: 200 {applyId: 88, uploadUrl: "https://..."}

    T->>OSS: PUT uploadUrl (multipart)
    OSS-->>T: 200 (uploadId)
    T->>SAC: POST /api/storage-applications/{applyId}/complete
    SAC->>SAS: completeUpload(applyId)
    SAS->>R: SET mc:storage:apply:{applyId} = {UPLOADED} EX 86400
    SAS->>Q: enqueue(transcodeJob{applyId, ossKey})
    SAS-->>SAC: 200
    SAC-->>T: 200 {status: UPLOADED}

    Q->>VTS: handle(transcodeJob)
    VTS->>OSS: getObject(ossKey)
    OSS-->>VTS: video stream
    VTS->>VTS: ffmpeg transcode (1080p + 720p + 480p)
    VTS->>OSS: putObject(transcoded/{ossKey}_{resolution}.mp4)
    VTS->>R: SET mc:storage:apply:{applyId} = {READY} EX 86400
    VTS->>NS: notify(teacherId, "视频转码完成")
    NS-->>T: 推送通知 (站内/Email)
```

---

## 8. 错题本自动归集流程

```mermaid
sequenceDiagram
    autonumber
    participant S as 学员
    participant ERC as ExerciseRecordController
    participant ERS as ExerciseRecordService
    participant DB as PostgreSQL
    participant WQS as WrongQuestionService
    participant Cache as Redis

    S->>ERC: POST /api/exercise-records {exerciseId, answer, duration}
    ERC->>ERS: submitRecord(userId, exerciseId, answer)
    ERS->>DB: SELECT exercise WHERE id=?
    DB-->>ERS: Exercise(correctAnswer=...)
    ERS->>ERS: compare(answer, correctAnswer)
    alt 答错
        ERS->>WQS: addToWrongQuestion(userId, exerciseId)
        WQS->>DB: INSERT wrong_questions (ON CONFLICT DO NOTHING)
        WQS->>Cache: DEL mc:wrong:user:{userId} (让下次查询重读 DB)
    else 答对
        ERS->>DB: UPDATE wrong_questions SET mastered=true WHERE userId=? AND exerciseId=?
    end
    ERS->>DB: INSERT exercise_records
    ERS-->>ERC: {isCorrect, correctAnswer}
    ERC-->>S: 200 {isCorrect: false, correctAnswer: "B"}
```

---

## 9. 全文搜索 + Elasticsearch (未来, 已规划)

```mermaid
sequenceDiagram
    autonumber
    participant U as 用户
    participant SC as SearchController
    participant SS as SearchService
    participant ES as Elasticsearch
    participant R as Redis
    participant DB as PostgreSQL

    U->>SC: GET /api/search?q=音视频
    SC->>SS: search(keyword, userId)
    SS->>ES: search(courses, keyword, {size: 20})
    ES-->>SS: 20 docs (courseId, title, score)
    SS->>R: GET mc:course:meta:{courseId1..20} (批量)
    R-->>SS: 缓存元数据
    alt 缓存未命中
        SS->>DB: SELECT * FROM courses WHERE id IN (?)
        DB-->>SS: rows
        SS->>R: SET EX 3600
    end
    SS-->>SC: search results
    SC-->>U: 200 {results: [...], total: 47}
```

---

## 10. 业务异常统一处理 (W32-33 修复后)

```mermaid
sequenceDiagram
    autonumber
    participant C as Controller
    participant S as Service
    participant BE as BusinessException
    participant GEH as GlobalExceptionHandler
    participant MDC as MDC (traceId)
    participant R as R<T> wrapper
    participant Logger as SLF4J
    participant Prom as Prometheus

    C->>S: invoke()
    S->>S: 业务逻辑
    alt 业务校验失败
        S->>BE: throw new BusinessException(ErrorCode.PARAM_INVALID, msg)
        BE-->>S: 异常向上抛
    end
    S-->>C: 异常向上抛
    C->>GEH: @ExceptionHandler(BusinessException.class)
    GEH->>MDC: get("traceId")
    MDC-->>GEH: traceId
    GEH->>Logger: log.warn("BusinessException code={} msg={}", code, msg)
    GEH->>Prom: 暴露 business_error_total{code="10001"} = 1
    GEH->>R: build R(code, message, data, traceId)
    GEH-->>C: ResponseEntity<R>
    C-->>Client: 200 R{code: 10001, message: "...", data: null, traceId: "abc"}

    alt 系统异常 (RuntimeException)
        S->>S: throw NullPointerException
        S-->>C: 异常向上抛
        C->>GEH: @ExceptionHandler(Exception.class)
        GEH->>Logger: log.error("Unhandled", e)
        GEH->>Prom: 暴露 system_error_total = 1
        GEH-->>C: 200 R{code: 500, message: "Internal Error", traceId}
    end
```

---

## 11. 课件树 CQRS 读写分离

```mermaid
flowchart LR
    Client[学员/教师] -->|GET /api/courses/cid/courseware/sid| CQC[CoursewareQueryController]
    CQC -->|invoke| CQS[CoursewareQueryService - 读模型]
    CQS -->|cache key| Redis[(Redis: mc:courseware:tree)]
    Redis -->|hit| CQS
    Redis -->|miss| Mapper[CoursewareMapper]
    Mapper -->|SELECT chapter + section + slide_ppt + slide_html| PG[(PostgreSQL 17)]
    PG --> Mapper
    Mapper --> CQS
    CQS -->|set cache TTL 300s| Redis
    CQS -->|返回 tree| Client

    Client -->|POST /api/courses/cid/courseware/...| CDC[CoursewareDeleteController]
    CDC --> CDS[CoursewareDeleteService - 写模型]
    CDS -->|DEL cache| Redis
    CDS --> Mapper
    Mapper -->|UPDATE/DELETE| PG
    CDS --> Audit[AuditLogWriter]
    Audit -->|async| AL[(操作日志表)]

    style CQS fill:#e1f5ff
    style CDS fill:#ffe1e1
    style Redis fill:#fff4e1
    style PG fill:#e1ffe1
```

**CQRS 原则**:
- 读: CoursewareQueryService + Redis 缓存
- 写: CoursewareDeleteService + DEL 缓存 + AuditLog
- 写后立即失效缓存, 保证读一致性

---

## 12. W34 关键验收清单

- [x] 11 个核心流程图 (学员/教师/视频/鉴权/慢查询/OSS/错题本/搜索/异常/CQRS)
- [x] 全部使用 Mermaid (团队通用, GitHub 渲染)
- [x] 包含 IDOR 防御 (W31 重点) 流程
- [x] 包含 CQRS 读分离 (W32 性能优化) 流程
- [x] 包含 慢查询拦截 (W32 性能) 流程
- [x] 包含 异常统一处理 (W32-33 修复) 流程
- [x] 100% 时序图覆盖核心业务场景

---

签发时间: 2026-07-20
签发人: 总工程师