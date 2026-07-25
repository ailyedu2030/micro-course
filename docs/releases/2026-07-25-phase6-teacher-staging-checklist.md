# Phase 6 教师模块 Staging 执行清单

> 日期：2026-07-25
> 适用版本：Phase 6 教师模块候选发布
> 关联主线提交：执行时以 `origin/main` HEAD 为准
> 负责人：项目负责人人工执行，AI 提供执行包与验收口径
> 结论前提：本地验证、CI、`deploy-gate`、`deploy-dryrun` 已完成；当前允许进入 staging，**不允许跳过 staging 直接生产**

---

## 一、当前放行前结论

### 1.1 已完成门禁

- `local-dev-deploy.sh --keep`：`16/16` 通过
- GitHub CI：`backend / frontend / e2e / docker / monitoring-lint` 全绿
- Bot 审批：`microcourse-pr-bot` 已对 PR #124 自动 approve
- 发布交接包：PR #124 已 squash merge 到 `main`
- `bash scripts/deploy-gate.sh check`：已通过，门禁窗口有效
- `bash scripts/deploy-dryrun.sh --env=staging`：`0 fail / 10 warn`
- `bash scripts/deploy-dryrun.sh --env=prod`：`0 fail / 10 warn`

### 1.2 当前不阻断但必须在 staging 重点观察的 warning

| 类别 | 现象 | 处理原则 |
|---|---|---|
| Node 版本 | 本地 `v26.4.0`，脚本建议 `v18/v20` | staging 以目标环境实际版本为准，记录版本差异 |
| `REDIS_PASSWORD` 为空 | 本地默认无密码 | staging 必须按环境真实配置验证 |
| 视频上传限制 | 当前检测为 `500MB`，建议 `2GB` | staging 验证实际教师视频上传能力 |
| `/api/auth/login` 与前端首页健康探针 | dry-run 下提示不可用 | staging 必须实测登录与页面可达 |
| `PAY_CALLBACK_SECRET` | 提示未设置或为占位符 | 若 staging 不测支付可登记；若要走支付链路则先补配置 |
| Hikari / PG 连接数 | 当前为保守值告警 | 只做记录，不阻断教师模块 staging |

### 1.3 执行边界

- AI 不执行 staging / production 容器操作
- AI 不 curl 真实 staging / production URL
- AI 不修改 staging / production 数据
- staging 操作必须由项目负责人或运维人工执行

---

## 二、Staging 执行前核查

### 2.1 环境身份确认

```bash
# 必须确认不是生产
# 生产标识：100.74.122.13 / microcourse.ailyedu.cn
# staging 标识：由运维提供，必须与生产隔离
```

### 2.2 发布材料核查

- [x] `CHANGELOG.md` 已补当前候选发布
- [x] `ROLLBACK_PLAN.md` 已补当前候选发布回滚说明
- [x] `docs/deferred-items.md` 已登记允许延期项
- [x] 当前候选发布无新的 Flyway schema 变更
- [x] 当前候选发布已明确：先 staging，后决定是否 gray

### 2.3 staging 执行前备份

- [ ] 后端当前 jar 备份
- [ ] 前端当前静态资源备份
- [ ] staging 数据库备份
- [ ] 记录当前 staging 运行版本

### 2.4 执行前取值快照

> 建议先在本地仓库根目录执行以下命令，并把结果抄送到 execution record。

```bash
git fetch origin --prune
git rev-parse origin/main
git log --oneline -1 origin/main
date "+%Y-%m-%d %H:%M:%S %Z"
```

建议记录以下 4 项：

- [ ] 本次拟部署提交 SHA
- [ ] 本次拟部署提交标题
- [ ] 执行开始时间
- [ ] 执行人与 staging 环境标识

---

## 三、部署到 Staging 的人工执行步骤

> 以下步骤由项目负责人或运维人工执行，AI 不代操作。

### 3.1 推荐命名约定

```bash
export RELEASE_TS="$(date +%Y%m%d_%H%M%S)"
export DEPLOY_COMMIT="$(git rev-parse origin/main)"
export BACKUP_TAG="phase6_teacher_${RELEASE_TS}_${DEPLOY_COMMIT:0:8}"
```

建议所有备份名都带上 `${BACKUP_TAG}`，便于回滚和回填审计。

### 3.2 推荐执行顺序

1. 备份当前 staging 后端 jar 与前端 dist
2. 上传当前候选发布构建产物
3. 替换 staging 后端与前端运行内容
4. 优雅重启应用 / reload nginx
5. 记录启动日志中的 `Started`、`ERROR`、`Exception`
6. 保持至少 5 分钟监控窗口

### 3.3 推荐取证内容

执行结束后，建议至少留存以下证据并写入 execution record：

- [ ] 部署提交 SHA 与提交标题
- [ ] 后端备份路径 / 文件名
- [ ] 前端备份路径 / 文件名
- [ ] 数据库备份文件名
- [ ] 启动完成日志时间点
- [ ] 5 分钟观察窗口开始 / 结束时间

---

## 四、Phase 6 教师模块 staging 验收清单

### 4.1 教师看板

- [ ] 教师登录后可进入教师看板首页
- [ ] “成绩明细”快捷入口跳转到 `/teacher/grades`
- [ ] “学员提问”快捷入口跳转到 `/teacher/discussions`
- [ ] “我教的课程”卡片在存在封面时显示封面图，不退化为占位图
- [ ] 课程卡片支持 `Tab` 聚焦与 `Enter / Space` 进入
- [ ] 待办列表只包含“未批改练习”和“未回复讨论”

### 4.2 成绩明细

- [ ] 未选课程时提示“请选择课程查看成绩”
- [ ] 带 `courseId` 进入时自动进入对应课程上下文
- [ ] `ACADEMIC` 角色打开待批改记录时，统一显示“查看成绩”只读语义
- [ ] 清空院系筛选后，课程列表恢复为全量课程
- [ ] 已选课程但无数据时，显示该课程暂无成绩数据

### 4.3 教学班 / 课件工作台

- [ ] 教学班页面可稳定选择课程并展示课程级空态
- [ ] 教学班课程卡片支持键盘进入
- [ ] 课件工作台未选课程时显示中性空态
- [ ] 缺失教师身份时给出显式失败保护，不出现静默白屏

### 4.4 视频管理

- [ ] 批量上传队列正常展示
- [ ] 章节上下文进入视频页时，列表只显示当前章节视频
- [ ] 执行重置后不丢失 `chapterId`
- [ ] 封面上传成功后弹窗状态被正确清空
- [ ] 失败视频可见“重试转码”入口

---

## 五、Staging 观察项

### 5.1 5 分钟观察

- [ ] 健康检查持续正常
- [ ] 无持续新增 `ERROR` / `Exception`
- [ ] 无教师主链路 401 / 500 / 白屏
- [ ] 容器资源未出现异常飙升

### 5.2 24 小时观察

- [ ] 无新增教师模块 P0 / P1-C 反馈
- [ ] 无新错误模式进入日志
- [ ] 若存在 warning 相关问题，完成记录与归类

---

## 六、Staging 通过后的决策门槛

只有满足以下条件，才允许进入 production gray 准备：

1. 本清单所有 `4.x` 核心验收项全部通过
2. `5.1` 观察项全部通过
3. 没有新增 P0 / P1-C
4. 项目负责人明确授权进入 gray 准备

---

## 七、Staging 失败时的处理

若 staging 任一核心链路失败：

1. 立即停止进入 production gray 的讨论
2. 记录失败场景、账号、页面、日志时间点
3. 按 `ROLLBACK_PLAN.md` 恢复 staging 到上一个稳定版本
4. 重新回到代码修复 -> 本地验证 -> CI -> 再次 staging

---

## 八、交接结论

当前候选发布已经具备进入 staging 的条件，但尚未具备直接生产部署的条件。

当前仓库侧发布准备已闭环，剩余动作仅为人工执行 staging 并回填执行记录。

本文件作为 staging 人工执行与验收的正式交接清单使用。
