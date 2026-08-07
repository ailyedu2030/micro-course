# R2 后端评审报告（独立 Agent 取证）

> 评审 Agent：`/root/review_r2b_backend`
> 日期：2026-08-06
> 评审对象：`docs/design/2026-08-06-PPT-HTML-音频同步控制方案.md` §13.2 B-01~B-10 矩阵
> 铁律：只读源码，未修改任何源码文件；未访问生产。

## 结论摘要

- B-01~B-10 逐项独立取证完成：**9 项 PASS / 1 项需修订（B-02 表述修正）+ 3 项新增发现（N-1~N-3）**。
- 方案全部关键决策与现有后端代码匹配，无虚构依赖；与方案 §13.2 结论一致性 9/10，B-02 需修正表述。
- 最严重确认：**v2 音频生成链路（D3）确凿为死路**——两张 v2 音频表除插入 GENERATING 行外全仓零写入方、零 worker、mapper 无 update 方法。

## B 矩阵逐项结论

| # | 检查项 | 结论 | 证据（本 Agent 独立读取） | 与方案结论 | 级别 |
|---|--------|------|--------------------------|-----------|------|
| B-01 | D3：v2 音频生成无消费者 | **PASS（实锤）** | `PptCoursewareServiceImpl.generateAudio` L167-190 仅 `audio.setStatus("GENERATING")` + insert；`HtmlCoursewareServiceImpl.generateSegmentAudio` L236-259 同构；全仓 `setStatus("READY")`/`setStatus("FAILED")` 0 命中；`@Scheduled` 清单（OutboxPollerWorker/MicroSpecialtyInviteExpiryJob/UserRetentionCleanupJob/TeacherTierPromotionJob/MicroSpecialtyProgressAggregator/OrderQueryServiceImpl）无 TTS 消费；`slideRenderExecutor` 仅 PPT 渲染/TTS legacy/Narration 三处 @Async | 一致 | P0 |
| B-02 | TtsWorker 与表结构匹配 | **REVISE（表述修正）** | V302/V305 表结构齐全（status CHECK('GENERATING','READY','FAILED')、generation_started_at、completed_at、storage_path、file_size_bytes、audio_duration_ms、voice_used/model_used、idx_*_status 索引），**但全仓不存在 `TtsWorker` 类**（grep `TtsWorker|AudioWorker|ttsWorker` 0 命中）；mapper 仅有 listByScript/findByToken/listByPageIds/listByUnitIds 读方法，无 update 方法 | **不一致：表结构齐备 PASS，但"TtsWorker 匹配"表述误导——worker 不存在** | P1-I（文档） |
| B-03 | D4：getPages 未聚合 v2 | **PASS（实锤）** | `SlideServiceImpl.getPages()` L518-556 仅 `slidePageMapper.selectList(qw)`（legacy slide_pages）+ toPageVO，无 v2 表查询；`getSegmentAudios`/`getPage` 同源 | 一致 | P1-C |
| B-04 | 聚合设计（v2>legacy、VO 扩展）可行 | **PASS** | `SlidePageVO` 已有 `narrationAudioUrl/audioDuration/segmentAudio/segmentAudios/contentType`（L23-33）；`buildSegmentUrl` L707-718（merged=true / token= 分支）与 `replacePageNumberInUrl` L756-758 可复用推导 segments[] | 一致 | - |
| B-05 | 音频鉴权双轨 | **PASS** | `CoursewareQueryController GET /audio/{token}` L75-91：`resolveAudioToken` + READY 检查（非 READY 返 202）+ IDOR courseId 校验（L86-91）；legacy `TtsController GET /pages/{pageNumber}/audio` L44-58：token 走 `validateAudioToken`，无 token 走 `ttsService.verifyAccess`（TtsServiceImpl L1112） | 一致 | P2 |
| B-06 | D5：voice/model 契约冲突 | **PASS（实锤）** | `application.yml` L177-185：`minimax.tts-model=speech-2.8-hd`、`tts-voice=${TTS_VOICE:female-shaonv}`；`TtsServiceImpl` L79/L273/L342-346：MiniMax HTTP `/v1/t2a_v2` body `model=speech-2.8-hd` + `voice_id=female-shaonv`（官方格式）；前端 `AudioManager.vue` L75-84/115-116：`male-young/male-mid/female-young/female-mid` + `MiniMax-speech-01/02`；`ScriptEditor.vue` L152-160：`female-young` + `MiniMax-speech-01`——三套命名并存 | 一致 | P1-C |
| B-07 | 决策 3：后端 flow evaluate | **PASS（且优于前端求值）** | `flow/FlowEngine.java`（decideNextPage 完整实现 + listFlows）；`FlowContext` 字段 currentPageId/userId/userProgress/lastQuizId/lastQuizAnswer；`NextFlowHandler/BranchFlowHandler/SkipIfKnownFlowHandler` 均存在；`decideNextPage(` 调用点全仓仅 FlowEngine 内部（外部 0 调用）；BRANCH 数据源可用：`ExerciseRecordController` POST /submit + GET /my/{exerciseId}/attempt-count、`CourseExerciseController`、`SectionQuizMapper` 均在；新增 `POST /courseware/{sectionId}/flow/evaluate` 与现有路由（Ppt=/ppt、Html=/html、CoursewareQuery=/courseware GET-only）无冲突 | 一致 | P1-C |
| B-08 | P3 ai-generate 路由无冲突 | **PASS** | 完整路由清单：`PptCoursewareController`（/ppt：pages/scripts/audios/flows 11 条）、`HtmlCoursewareController`（/html：unit/segments/audios 9 条）、`NarrationController`（/slides：pages/{n}/narration/generate+PUT、narrations/generate 3 条）、`TtsController`（/slides：pages/{n}/audio/generate、audio/generate、pages/{n}/audio、sections/{sid}/tts/generate+status 5 条）、`CoursewareQueryController`（/courseware：{sectionId}、audio/{token} 2 条）——均无 ai-generate | 一致 | - |
| B-09 | legacy section 合并 TTS 兼容 | **PASS** | `TtsServiceImpl.doGenerateSectionAsync` L423-588：按页 `AUDIO_READY` + `segment_count` + 每页 URL `/slides/pages/{n}/audio?v=2`；merged URL `/pages/1/audio?v=2&merged=true` 写入 task state（`markTaskCompleted` L721 → `state.setMergedAudioUrl`），经 `getSectionTtsStatus` 下发；`buildSegmentUrl` merged=true 分支可正确推导每页 segment URL | 一致 | - |
| B-10 | 数据字典/API 契约同步 | **PASS（实锤契约漂移）** | `docs/数据字典.md` 全文搜索 `slide_ppt_pages/slide_html_units/slide_ppt_flow`：0 命中（rg exit=1）；`docs/API契约-Phase1.md` 无 tts-options/voice 契约登记 | 一致 | P1-I |

## 新增发现（方案未覆盖）

| # | 级别 | 发现 | 证据 | 建议 |
|---|------|------|------|------|
| N-1 | P1-I | 方案 B-02 标题"TtsWorker 与表结构匹配"表述误导：**不存在 TtsWorker**，v2 音频 READY/FAILED 流转完全缺失（即 D3 的 worker 侧）；P0 实施时不能假设"复用 TtsWorker"，需新建消费端 | 全仓 grep `TtsWorker` 0 命中；mapper 无 update 方法 | 方案 B-02 改为"表结构齐备（V302/V305）但 worker 缺失（与 D3 合并为 P0-1 新建 TTS 消费端）" |
| N-2 | P2 | legacy 合并 TTS 的 merged URL 存于 **task state（文件/内存），未落库到任意持久字段**；`getSectionTtsStatus` 需 taskId 才可取——若 task 过期或状态文件清理，教师端无法再取 merged 音频 URL（页面级 URL 已落库不受影响） | `markTaskCompleted` L721-730 仅 `state.setMergedAudioUrl`；`readTaskState` L847-857 读 task 文件 | 方案 P0 聚合时明确：页面级 URL 落库即可满足 segments[] 推导（已由 buildSegmentUrl merged 分支覆盖）；merged task URL 仅作批量进度展示，不作播放依赖 |
| N-3 | P3 | `TtsServiceImpl` 构造器 `@Value` 默认 voice 为 `vivian`（L107），而 application.yml 显式 `female-shaonv`——若未来 yml 未配置，会以非 MiniMax 官方 voice_id 请求；建议与 `tts-options` 契约统一并移除不一致默认 | `TtsServiceImpl.java:107` vs `application.yml:185` | 实施 R-6 时统一默认值与契约 |

## 对方案的修订建议

1. **R-8 增强**：P0 实施清单补"新建 v2 TTS 消费 worker（当前不存在，非复用）"，验收标准含"GENERATING→READY/FAILED 全流转 + 并发≤2 + 超时标记"。
2. **R-5 细化**：`POST /courseware/{sectionId}/flow/evaluate` 请求体建议直接复用 `FlowContext` 字段（currentPageId/userId/userProgress/lastQuizId/lastQuizAnswer），避免二次 DTO；响应 = nextPageId（null=结束/线性兜底）。
3. **B-10 落地动作**：`docs/数据字典.md` 需补 V300-V310 全表（slide_ppt_pages/slide_ppt_page_scripts/slide_ppt_page_audios/slide_html_units/slide_html_segment_scripts/slide_html_segment_audios/slide_ppt_flow + v_*_status 视图）与新增 `tts-options` API 契约条目。
4. 方案 §13.2 表格 B-02 行按 N-1 修正，避免实施者误以为已有 worker 可接线。

## 结论

方案后端部分可落地：数据模型、FlowEngine、音频鉴权、legacy 合并 TTS 均与代码匹配；D3/D4/D5/D6 缺陷属实；无虚构依赖。阻塞项仅为实施（新建 worker、聚合 getPages、flow evaluate、契约登记），与方案 P0 计划一致。
