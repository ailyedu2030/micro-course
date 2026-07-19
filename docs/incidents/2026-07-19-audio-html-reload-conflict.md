# 2026-07-19 事故复盘 — Audio/HTML 重载冲突导致 segmentAudios 丢失与 token 403

> **事故等级**: P1-C (客户可感知: 课件音频无法播放 + token URL 全部 403)
> **触发场景**: Trae 端 opencode 工具调用 `_publish_section.py` 时 Step 4 (audio batch) 与 Step 8 (HTML) 时序冲突
> **影响范围**: 27 节单页 HTML_DIRECT 课件的音频元数据丢失, 405 个 token URL 失效
> **根因等级**: P0 设计缺陷 (后端接口 UPSERT 语义不清 + token 校验与单页 HTML_DIRECT 模型不匹配)

---

## 1. 症状

### 1.1 客户可见症状
- 学生播放课件时, 15 段音频全部 403, 无法听讲
- 教师控制台 "课件预览" 无音频元素

### 1.2 测试报告症状 (Trae 端实测, 2026-07-18)
| # | 报告项 | 期望 | 实际 |
|---|--------|------|------|
| 1 | `GET /slides/pages?sectionId=650` → `segmentAudios` 数组 | 15 条 | **0 条** |
| 2 | reload 生成的 token URL GET | 200 + 音频字节 | **403 Forbidden** (4/4 抽样) |
| 3 | `htmlContent` 长度 | 50920 字节 (本地新版) | **37402 字节** |

---

## 2. 根因 (3 层叠加)

### 2.1 P0-1 · 后端 `uploadHtmlFile` 是 destructive UPSERT

**位置**: `micro-course-api/src/main/java/com/microcourse/plugin/interactive/service/impl/SlideServiceImpl.java:296-309`

**旧逻辑**:
```java
// 删除该 slide 旧的 pages（一对一覆盖）
slidePageMapper.delete(new LambdaQueryWrapper<SlidePage>().eq(SlidePage::getSlideId, sid));
SlidePage page = new SlidePage();
...
slidePageMapper.insert(page);
```

**问题**: `delete` + `insert` 会清空 page 行的所有 audio 元数据:
- `narration_audio_url` (含 token)
- `audio_duration`
- `segment_count`
- `voice` / `tts_model`
- `generated_at`

**直接后果**: Step 4 (uploadBatch) 写入的 audio 元数据被 Step 8 (uploadHtml) 完全擦除。

### 2.2 P0-2 · `validateAudioToken` 与单页 HTML_DIRECT 模型不匹配

**位置**: `micro-course-api/src/main/java/com/microcourse/plugin/interactive/service/impl/TtsServiceImpl.java:796-806`

**旧逻辑**:
```java
public boolean validateAudioToken(Long courseId, Integer pageNumber, Long sectionId, String token) {
    ...
    LambdaQueryWrapper<SlidePage> qw = new LambdaQueryWrapper<SlidePage>()
            .eq(SlidePage::getCourseId, courseId)
            .eq(SlidePage::getSectionId, sectionId)
            .eq(SlidePage::getPageNumber, pageNumber);  // ← 严格按 pageNumber 查
    SlidePage page = slidePageMapper.selectOne(qw);
    if (page == null || page.getNarrationAudioUrl() == null) return false;
    return page.getNarrationAudioUrl().contains("token=" + token);
}
```

**问题**: 单页 HTML_DIRECT 场景下, SlidePage 表只有 pageNumber=1 一条记录。
- reload 工具生成的 15 个 token URL 是 `/pages/{1..15}/audio?token=xxx`
- 实际请求 `pages/2/audio?token=xxx` 时, `selectOne(pageNumber=2)` 返回 null → **false → 403**

### 2.3 P0-3 · opencode 8 步流程的时序耦合

**位置**: `_publish_section.py` (opencode 端脚本)

**问题**: 脚本默认按 Step 1→8 顺序执行, Step 4 (audio) 先写入 `narration_audio_url`, Step 8 (HTML) 后用 `uploadHtmlFile` 删除并重建 page, **直接抹除 Step 4 的全部音频元数据**。

---

## 3. 横向扫描

| 检查项 | 结果 |
|--------|------|
| 同模式代码 (`slidePageMapper.delete` + `insert`) | 仅 `uploadHtmlFile` 一处 |
| 其他 destructive UPSERT 接口 | `SlideController.upload` (PPTX) 也是 delete+insert, 但 PPTX 场景不涉及 audio 元数据, **不构成 P0** |
| `validateAudioToken` 同模式 | 仅 `TtsController.getAudio` 调用, **不构成横向问题** |
| `HtmlSanitizer.sanitizeForCourseware` 对 50920 字节 HTML 的过滤 | 经实测, emoji 与 inline script 会被清洗, 但 <audio src> 与 AUDIO_SEG_NN_URL 占位符应保留. 待 R5 重测确认 |

---

## 4. 修复方案 (本次 PR)

### 4.1 P1-C 修复 #1: `uploadHtmlFile` 改为非破坏性 UPSERT

**位置**: `SlideServiceImpl.java:296-339`

**新逻辑**: 按 `(slideId, pageNumber=1)` 查找现有 page, 存在则只更新 `htmlContent` / `contentType` / `imageUrl` / `updatedAt`, **保留** `narration_audio_url` / `audio_duration` / `segment_count` / `voice` / `tts_model` / `generated_at` / `narration_status`。

### 4.2 P1-C 修复 #2: `validateAudioToken` 增加 sectionId 级 fallback

**位置**: `TtsServiceImpl.java:796-833`

**新逻辑**: 先按 `pageNumber` 精确匹配 (旧路径), 若失败则 fallback 到按 `sectionId` 查该 section 下任一含 token 的 page (单页 HTML_DIRECT 场景)。

### 4.3 不改 opencode 端 `_publish_section.py`

理由: opencode 端的 Step 4/8 时序在前端修复后已**自动无副作用** —— Step 8 不再擦除 audio 元数据。时序本身无需调整。

---

## 5. 单元测试覆盖

| 测试类 | 用例 | 验证点 |
|--------|------|--------|
| `SlideServiceTest$HtmlUpload$uploadHtmlFile_Upsert` | 复用 slide_id, **不** delete 旧 page, **不** insert 新 page, **保留** narration_audio_url + segmentCount | P1-C #1 |
| `TtsServiceTokenValidationTest` | 7 个用例: null token / null sectionId / 精确匹配 / sectionId fallback / 无匹配 / token 不一致 / 多 page section | P1-C #2 |

**测试结果**: 20/20 PASS (`Tests run: 20, Failures: 0, Errors: 0, Skipped: 0`)

---

## 6. 防止再发

### 6.1 代码层
- `validateAudioToken` 的 fallback 日志用 `[TTS] validateAudioToken sectionId-level fallback hit:` 标记, 便于审计时发现异常使用模式
- `uploadHtmlFile` 在 in-place 更新时输出 `[SlideUpload-HtmlFile] UPSERT(in-place)` 日志, 便于追踪是否被滥用为"覆盖式"操作

### 6.2 测试层
- `uploadHtmlFile_Upsert` 测试断言 audio 元数据保留, 任何后续修改破坏此行为将立即 FAIL
- `TtsServiceTokenValidationTest` 锁定 6 种 token 校验场景, 防止 fallback 逻辑被误删

### 6.3 流程层
- 任何同时操作 `slide_pages` 与 `audio_*` 的接口必须在 PR 描述中明确"是否保留 audio 元数据"
- precheck.sh 增加规则: 检测 `slidePageMapper.delete` + `slidePageMapper.insert` 同事务模式 (待 R5 验证后纳入)

---

## 7. 验证清单

- [x] precheck.sh 22/22 PASS
- [x] mvn compile 0 ERROR
- [x] mvn test 20/20 PASS (SlideServiceTest + TtsServiceTokenValidationTest)
- [x] local-dev-deploy.sh 15/15 PASS
- [ ] R5 重测脚本 (`_r5_verify_fix_20260719.py`) 在生产 URL 由 Trae 端执行后通过

---

## 8. PR 信息

- **分支**: `fix/audio-html-upload-preserves-metadata`
- **基于**: `main` (commit aebc08cb)
- **改动文件**:
  - `micro-course-api/src/main/java/com/microcourse/plugin/interactive/service/impl/SlideServiceImpl.java`
  - `micro-course-api/src/main/java/com/microcourse/plugin/interactive/service/impl/TtsServiceImpl.java`
  - `micro-course-api/src/test/java/com/microcourse/plugin/interactive/SlideServiceTest.java`
  - `micro-course-api/src/test/java/com/microcourse/plugin/interactive/TtsServiceTokenValidationTest.java` (新增)

---

**复盘**: 由于 opencode 端 Step 4/8 时序与后端 destructive UPSERT 双向缺陷叠加, 单页 HTML_DIRECT 课件的 audio 元数据在每次 publish 时被擦除. 修复从后端接口语义入手, 不依赖 opencode 端脚本改造, 修复面最小化.

— END —