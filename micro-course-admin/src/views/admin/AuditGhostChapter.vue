<!--
  Admin · 幽灵章节审计（D-1 闭环 · V328 审计 + V332 幂等自动修复）
  路由路径: /admin/audit/ghost-chapters（仅 ADMIN）
  数据来源:
    GET  /api/admin/audit/ghost-chapters → 审计报告 JSON 文本
    POST /api/admin/audit/run-v332-fix  → V332 幂等自动修复（可重跑）
  说明: V310 回填段 COALESCE(chapter_id,1) 硬编码兜底产生"幽灵章节 1"错误归属；
        V332 自动修正可反查部分，无法判定的（课时缺失/跨课程引用）保留待人工 review。
-->
<template>
  <div class="audit-ghost-chapter">
    <div class="page-header">
      <h1>幽灵章节审计</h1>
      <p class="page-desc">排查 V310 回填硬编码 chapter_id=1 产生的错误章节归属（D-1 闭环 · 数据完整性 = 体验保障）</p>
    </div>

    <!-- 操作行 -->
    <div class="action-bar">
      <div class="action-left">
        <el-button
          type="primary"
          :loading="fixing"
          :disabled="loading || reportTotal === 0"
          data-testid="run-v332-fix"
          @click="confirmRunFix"
        >
          <el-icon style="margin-right: 6px"><Tools /></el-icon>
          {{ fixing ? '修复执行中…' : '运行 V332 修复' }}
        </el-button>
        <el-tooltip content="V332 幂等修复：通过课时反查章节修正错误归属；无法判定的记录保留待人工 review" placement="top">
          <el-icon class="hint-icon" aria-label="修复说明"><QuestionFilled /></el-icon>
        </el-tooltip>
        <span v-if="lastAuditAt" class="last-audit">上次审计：{{ lastAuditAt }}（每 5 秒自动刷新）</span>
      </div>
      <el-button :icon="Refresh" circle aria-label="手动刷新审计" :loading="refreshing" @click="refreshNow" />
    </div>

    <!-- 加载骨架 -->
    <el-skeleton v-if="loading" :rows="6" animated class="skeleton-block" />

    <!-- 错误态（L0：告诉用户怎么办，不是只显示原因） -->
    <el-alert v-else-if="error" type="error" :closable="false" class="error-alert">
      <template #title>
        <div class="error-title">{{ error }}</div>
        <div class="error-guidance">
          请确认：① V328 / V332 迁移已由 Flyway 应用（部署后自动执行）；② 当前账号为管理员。
          如仍失败请联系管理员查看后端日志（关键字 GhostChapter-Audit / GhostChapter-Fix）。
        </div>
      </template>
      <template #default>
        <el-button size="small" type="primary" plain @click="fetchAudit(false)">重新加载</el-button>
      </template>
    </el-alert>

    <!-- 空状态（L0：明确告知"无问题"，绿色正向反馈） -->
    <el-empty v-else-if="reportTotal === 0" description="未发现幽灵章节数据，课件章节归属正常">
      <template #image>
        <el-icon :size="64" class="empty-ok-icon"><CircleCheckFilled /></el-icon>
      </template>
      <template #default>
        <p class="empty-sub">V310 硬编码兜底未产生错误归属，或 V332 已修复全部可自动修复项。</p>
        <el-button size="small" type="primary" plain @click="fetchAudit(false)">刷新确认</el-button>
      </template>
    </el-empty>

    <template v-else>
      <!-- KPI 卡片：总幽灵行数 / 受影响课程数 / 修复进度 -->
      <el-row :gutter="16" class="kpi-row">
        <el-col :xs="24" :sm="8">
          <el-card shadow="never" class="kpi-card">
            <div class="kpi-label">幽灵行总数</div>
            <div class="kpi-value kpi-danger" role="status" aria-label="幽灵章节嫌疑行总数">{{ reportTotal }}</div>
            <div class="kpi-sub">PPT + HTML 嫌疑行合计</div>
          </el-card>
        </el-col>
        <el-col :xs="24" :sm="8">
          <el-card shadow="never" class="kpi-card">
            <div class="kpi-label">受影响课程数</div>
            <div class="kpi-value" role="status" aria-label="受影响课程数">{{ affectedCourses }}</div>
            <div class="kpi-sub">按 course_id 去重</div>
          </el-card>
        </el-col>
        <el-col :xs="24" :sm="8">
          <el-card shadow="never" class="kpi-card">
            <div class="kpi-label">修复进度</div>
            <div class="kpi-progress">
              <el-progress
                :percentage="fixProgress"
                :status="fixProgress === 100 ? 'success' : undefined"
                :stroke-width="10"
                role="progressbar"
                :aria-label="`幽灵章节修复进度 ${fixProgress}%`"
              />
            </div>
            <div class="kpi-sub">
              {{ fixedRows }} / {{ beforeTotal }} 行已修复
              <span v-if="reviewLeft > 0" class="review-hint">，{{ reviewLeft }} 行待人工 review（课时缺失/跨课程）</span>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 按课程分布 -->
      <el-card shadow="never" class="table-card">
        <template #header><span>按课程分布</span></template>
        <el-table :data="byCourseRows" size="small" border aria-label="幽灵章节按课程分布表">
          <el-table-column prop="course_id" label="课程 ID" min-width="100" />
          <el-table-column label="来源" min-width="100">
            <template #default="{ row }">
              <el-tag :type="row.source_type === 'PPT' ? 'primary' : 'success'" size="small">{{ row.source_type }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="cnt" label="嫌疑行数" min-width="100" sortable />
        </el-table>
      </el-card>

      <!-- 明细样例（≤200 行） -->
      <el-card shadow="never" class="table-card">
        <template #header>
          <span>嫌疑行明细（样例 ≤200 行）</span>
          <span class="table-sub">修复后自动刷新；actual_chapter_id 为按课时反查的真实章节</span>
        </template>
        <el-table :data="sampleRows" size="small" border max-height="480" aria-label="幽灵章节嫌疑行明细表">
          <el-table-column label="来源" min-width="90">
            <template #default="{ row }">
              <el-tag :type="row.source_type === 'PPT' ? 'primary' : 'success'" size="small">{{ row.source_type }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="row_id" label="行 ID" min-width="80" />
          <el-table-column prop="course_id" label="课程" min-width="80" />
          <el-table-column prop="current_chapter_id" label="当前章节" min-width="90" />
          <el-table-column prop="section_id" label="课时" min-width="80">
            <template #default="{ row }">
              <span>{{ row.section_id ?? '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="actual_chapter_id" label="实际章节" min-width="90">
            <template #default="{ row }">
              <el-tag v-if="row.actual_chapter_id" type="warning" size="small">{{ row.actual_chapter_id }}</el-tag>
              <span v-else>无法反查</span>
            </template>
          </el-table-column>
          <el-table-column prop="section_title" label="课时标题" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">{{ row.section_title ?? '—' }}</template>
          </el-table-column>
          <el-table-column label="跨课程引用" min-width="110">
            <template #default="{ row }">
              <el-tag v-if="row.chapter1_cross_course" type="danger" size="small">是</el-tag>
              <el-tag v-else type="info" size="small">否</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Tools, QuestionFilled, CircleCheckFilled } from '@element-plus/icons-vue'
import { getGhostChapterAudit, runV332Fix } from '@/api/audit'

// ===== 状态 =====
const loading = ref(true)       // 首次加载
const refreshing = ref(false)   // 手动刷新
const fixing = ref(false)       // V332 修复执行中
const error = ref('')           // 错误消息（L0：含怎么办指引）
const report = ref(null)        // 审计报告 { total_ghost_rows, by_course[], sample_rows[], audited_at, note }
const lastAuditAt = ref('')     // 上次审计时间（人类可读）
const beforeTotal = ref(0)      // 首次审计的幽灵总数（修复进度基准）
const fixedRows = ref(0)        // 已修复行数（beforeTotal - 当前）
const reviewLeft = ref(0)       // 待人工 review 行数（section_id 缺失 / 跨课程）
const auditVersion = ref('')

// ===== KPI 计算 =====
const reportTotal = computed(() => (report.value?.total_ghost_rows) ?? 0)
const affectedCourses = computed(() => {
  const list = report.value?.by_course ?? []
  return new Set(list.map(r => r.course_id)).size
})
const byCourseRows = computed(() => report.value?.by_course ?? [])
const sampleRows = computed(() => report.value?.sample_rows ?? [])
const fixProgress = computed(() => {
  if (beforeTotal.value <= 0) return 100
  return Math.max(0, Math.min(100, Math.round((fixedRows.value / beforeTotal.value) * 100)))
})

// ===== 数据获取 =====
function friendlyTime(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return String(iso)
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function parseReport(payload) {
  // 后端返回 JSON 文本，可能为对象或字符串
  if (!payload) return null
  if (typeof payload === 'string') {
    try {
      return JSON.parse(payload)
    } catch (e) {
      console.warn('[AuditGhostChapter] 审计报告 JSON 解析失败，按原始文本展示', e)
      return { total_ghost_rows: 0, by_course: [], sample_rows: [], raw: payload }
    }
  }
  return payload
}

function applyReport(parsed) {
  report.value = parsed
  lastAuditAt.value = friendlyTime(parsed?.audited_at) || '—'
  auditVersion.value = parsed?.audit_version || ''
  const current = parsed?.total_ghost_rows ?? 0
  if (beforeTotal.value === 0) {
    beforeTotal.value = current
    fixedRows.value = 0
  } else {
    fixedRows.value = Math.max(0, beforeTotal.value - current)
  }
  // 剩余待人工 review = sample_rows 中无法反查（section_id 缺失）或跨课程引用
  reviewLeft.value = (parsed?.sample_rows ?? []).filter(r => !r.section_id || r.chapter1_cross_course).length
}

async function fetchAudit(silent = true) {
  if (silent) {
    refreshing.value = true
  } else {
    loading.value = true
  }
  error.value = ''
  try {
    const res = await getGhostChapterAudit()
    applyReport(parseReport(res.data))
  } catch (e) {
    // L0 铁律：错误消息告诉用户怎么办
    const msg = e?.response?.data?.message || e?.message || '幽灵章节审计加载失败'
    error.value = `审计加载失败：${msg}。请确认 V328/V332 迁移已由 Flyway 应用（部署后自动执行），且当前账号为管理员。`
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

function refreshNow() {
  fetchAudit(true)
}

// ===== V332 幂等修复 =====
async function confirmRunFix() {
  try {
    await ElMessageBox.confirm(
      '将按课时反查章节，修正 chapter_id=1 的错误归属；无法判定的记录（课时缺失/跨课程）保持原样并标记待人工 review。V332 幂等可重跑，是否继续？',
      '确认运行 V332 修复',
      { confirmButtonText: '运行修复', cancelButtonText: '取消', type: 'warning' }
    )
  } catch (e) {
    return // 用户取消
  }
  fixing.value = true
  try {
    const res = await runV332Fix()
    const parsed = parseReport(res.data)
    if (beforeTotal.value === 0) {
      beforeTotal.value = parsed?.total_ghost_rows ?? 0
    }
    applyReport(parsed)
    const remaining = parsed?.total_ghost_rows ?? 0
    ElMessage.success(`V332 修复完成：剩余幽灵行 ${remaining}（已修复 ${fixedRows.value} 行）`)
  } catch (e) {
    const msg = e?.response?.data?.message || e?.message || '修复执行失败'
    ElMessage.error(`V332 修复失败：${msg}。请确认 V328 诊断对象已应用（v_ghost_chapter_audit 视图存在），可联系管理员查看日志`)
  } finally {
    fixing.value = false
  }
}

// ===== 每 5 秒轮询（修复进度实时刷新）=====
let pollTimer = null
function startPolling() {
  pollTimer = setInterval(() => {
    if (fixing.value) return // 修复执行中由完成后一次性刷新
    fetchAudit(true)
  }, 5000)
}

onMounted(() => {
  fetchAudit(false).then(startPolling)
})
onBeforeUnmount(() => {
  if (pollTimer) clearInterval(pollTimer)
})
</script>

<style scoped>
.audit-ghost-chapter {
  padding: 20px;
  max-width: 1200px;
}
.page-header h1 {
  margin: 0 0 6px;
  font-size: 22px;
}
.page-desc {
  margin: 0 0 16px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.action-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.action-left {
  display: flex;
  align-items: center;
  gap: 8px;
}
.hint-icon {
  color: var(--el-text-color-secondary);
  cursor: help;
}
.last-audit {
  margin-left: 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.skeleton-block {
  margin-top: 8px;
}
.error-alert {
  margin-bottom: 16px;
}
.error-title {
  font-weight: 600;
  margin-bottom: 4px;
}
.error-guidance {
  font-size: 13px;
  color: var(--el-color-error);
  line-height: 1.6;
}
.empty-ok-icon {
  color: var(--el-color-success);
}
.empty-sub {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  margin: 0 0 8px;
}
.kpi-row {
  margin-bottom: 16px;
}
.kpi-card {
  text-align: center;
  margin-bottom: 8px;
}
.kpi-label {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.kpi-value {
  font-size: 30px;
  font-weight: 700;
  line-height: 1.4;
}
.kpi-danger {
  color: var(--el-color-danger);
}
.kpi-sub {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
}
.kpi-progress {
  padding: 6px 8px 0;
}
.review-hint {
  color: var(--el-color-warning);
}
.table-card {
  margin-bottom: 16px;
}
.table-sub {
  margin-left: 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
