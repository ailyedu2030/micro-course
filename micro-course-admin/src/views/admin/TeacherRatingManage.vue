<!--
  管理员 - 教师评级管理
  /admin/teacher-ratings
-->
<template>
  <div class="teacher-rating-container">
    <!-- 顶部说明卡片 -->
    <el-card class="info-card shadow-hover" shadow="never">
      <div class="info-content">
        <el-icon :size="20" class="info-icon"><Medal /></el-icon>
        <div>
          <p class="info-title">{{ $t('teacherRating.title') }}</p>
          <p class="info-desc">{{ $t('teacherRating.desc') }}</p>
        </div>
      </div>
    </el-card>

    <!-- 筛选栏 -->
    <el-card class="filter-card" shadow="never">
      <div class="filter-bar">
        <el-select v-model="tierFilter" :placeholder="$t('teacherRating.allTiers')" clearable @change="fetchList" style="width: 140px">
          <el-option :label="$t('teacherRating.allTiers')" value="" />
          <el-option :label="'🌱 ' + $t('teacherDashboard.newTeacher')" value="NEW" />
          <el-option :label="'🥉 ' + $t('teacherDashboard.tierBronze')" value="BRONZE" />
          <el-option :label="'🥈 ' + $t('teacherDashboard.tierSilver')" value="SILVER" />
          <el-option :label="'🥇 ' + $t('teacherDashboard.tierGold')" value="GOLD" />
          <el-option :label="'💎 ' + $t('teacherDashboard.tierPlatinum')" value="PLATINUM" />
        </el-select>
        <el-button type="primary" :loading="recalculatingAll" @click="handleRecalculateAll">
          <el-icon><Refresh /></el-icon> {{ $t('teacherRating.recalculateAll') }}
        </el-button>
      </div>
    </el-card>

    <!-- 表格 -->
    <el-card class="table-card" shadow="never" v-loading="loading" :element-loading-text="$t('common.loading')">
      <el-table :data="ratingList" stripe style="width: 100%" @row-click="handleRowClick">
        <el-table-column :label="$t('course.teacher')" min-width="160">
          <template #default="{ row }">
            <div class="teacher-cell">
              <el-avatar :size="32" :src="row.teacherAvatar">
                {{ (row.teacherName || '?').charAt(0) }}
              </el-avatar>
              <span class="teacher-name">{{ row.teacherName || $t('course.unknown') }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column :label="$t('teacherRating.tier')" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="getTierTagType(row.tier)" effect="dark" size="small" class="tier-tag">
              {{ tierIcon(row.tier) }} {{ row.tierLabel || row.tier }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="ratingScore" :label="$t('teacherDashboard.ratingScore')" width="100" align="center">
          <template #default="{ row }">
            <span class="score-value">{{ row.ratingScore ?? 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="avgStudentRating" :label="$t('teacherDashboard.studentEval')" width="90" align="center" />
        <el-table-column prop="completionRate" :label="$t('teacherDashboard.completionRate')" width="80" align="center">
          <template #default="{ row }">{{ row.completionRate ?? 0 }}%</template>
        </el-table-column>
        <el-table-column prop="totalStudents" :label="$t('teacherDashboard.totalStudents')" width="80" align="center" />
        <el-table-column prop="totalCourses" :label="$t('teacherDashboard.totalCourses')" width="80" align="center" />
        <el-table-column prop="calculatedAt" :label="$t('teacherRating.calculatedAt')" width="160">
          <template #default="{ row }">{{ formatTime(row.calculatedAt) }}</template>
        </el-table-column>
        <el-table-column :label="$t('app.operation')" width="120" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click.stop="handleAdjust(row)">
              {{ $t('teacherRating.adjustTier') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && ratingList.length === 0" :description="$t('teacherRating.noData')" />
    </el-card>

    <!-- 调整等级弹窗 -->
    <el-dialog
      v-model="adjustDialogVisible"
      :title="$t('teacherRating.adjustDialogTitle')"
      width="450px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-form label-width="80px" label-position="right">
        <el-form-item :label="$t('course.teacher')">
          <span class="dialog-teacher">{{ adjustingTeacher?.teacherName }}</span>
        </el-form-item>
        <el-form-item :label="$t('teacherRating.currentTier')">
          <el-tag :type="getTierTagType(adjustingTeacher?.tier)" effect="dark">
            {{ adjustingTeacher?.tierLabel || adjustingTeacher?.tier }}
          </el-tag>
        </el-form-item>
        <el-form-item :label="$t('teacherRating.newTier')" prop="newTier">
          <el-select v-model="adjustForm.newTier" :placeholder="$t('teacherRating.selectTierPlaceholder')" style="width: 100%">
            <el-option :label="'🌱 NEW ' + $t('teacherDashboard.newTeacher')" value="NEW" />
            <el-option :label="'🥉 BRONZE ' + $t('teacherDashboard.tierBronze')" value="BRONZE" />
            <el-option :label="'🥈 SILVER ' + $t('teacherDashboard.tierSilver')" value="SILVER" />
            <el-option :label="'🥇 GOLD ' + $t('teacherDashboard.tierGold')" value="GOLD" />
            <el-option :label="'💎 PLATINUM ' + $t('teacherDashboard.tierPlatinum')" value="PLATINUM" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('teacherRating.reason')">
          <el-input
            v-model="adjustForm.reason"
            type="textarea"
            :rows="2"
            :placeholder="$t('teacherRating.reasonPlaceholder')"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adjustDialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="adjustSaving" @click="handleSaveAdjust">
          {{ $t('teacherRating.confirmAdjust') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Medal, Refresh } from '@element-plus/icons-vue'
import { getAllRatings, getRatingsByTier, adjustTeacherTier, recalculateAllTeacherRatings } from '@/api/teacher-rating'

const { t } = useI18n()
const loading = ref(false)
const ratingList = ref([])
const tierFilter = ref('')
const recalculatingAll = ref(false)

// 调整弹窗
const adjustDialogVisible = ref(false)
const adjustSaving = ref(false)
const adjustingTeacher = ref(null)
const adjustForm = ref({ newTier: '', reason: '' })

function getTierTagType(tier) {
  const map = { PLATINUM: 'danger', GOLD: 'warning', SILVER: 'info', BRONZE: '', NEW: 'success' }
  return map[tier] || 'info'
}

function tierIcon(tier) {
  const icons = { PLATINUM: '💎', GOLD: '🥇', SILVER: '🥈', BRONZE: '🥉', NEW: '🌱' }
  return icons[tier] || '📊'
}

function formatTime(time) {
  if (!time) return '-'
  try {
    const d = new Date(time)
    const pad = (n) => String(n).padStart(2, '0')
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
  } catch {
    return time
  }
}

async function fetchList() {
  loading.value = true
  try {
    const res = tierFilter.value
      ? await getRatingsByTier(tierFilter.value)
      : await getAllRatings()
    ratingList.value = res.data || []
  } catch (e) {
    console.warn('[TeacherRatingManage] fetch failed', e)
    ElMessage.error(t('teacherRating.loadFailed'))
    ratingList.value = []
  } finally {
    loading.value = false
  }
}

function handleRowClick(row) {
  handleAdjust(row)
}

function handleAdjust(row) {
  adjustingTeacher.value = row
  adjustForm.value = { newTier: row.tier || 'NEW', reason: '' }
  adjustDialogVisible.value = true
}

async function handleSaveAdjust() {
  if (!adjustForm.value.newTier) {
    ElMessage.warning(t('teacherRating.selectTierRequired'))
    return
  }
  adjustSaving.value = true
  try {
    await adjustTeacherTier(
      adjustingTeacher.value.teacherId,
      adjustForm.value.newTier,
      adjustForm.value.reason || t('teacherRating.manualAdjustReason')
    )
    ElMessage.success(t('teacherRating.adjustSuccess'))
    adjustDialogVisible.value = false
    await fetchList()
  } catch (e) {
    console.warn('[TeacherRatingManage] adjust failed', e)
    ElMessage.error(e?.response?.data?.message || t('teacherRating.adjustFailed'))
  } finally {
    adjustSaving.value = false
  }
}

async function handleRecalculateAll() {
  try {
    await ElMessageBox.confirm(t('teacherRating.confirmRecalculateMsg'), t('app.confirm'), {
      type: 'warning',
      confirmButtonText: t('teacherRating.confirmRecalculateBtn'),
      cancelButtonText: t('common.cancel')
    })
  } catch {
    return
  }
  recalculatingAll.value = true
  try {
    // P1-I 修复: 用批量端点替代串行循环
    const res = await recalculateAllTeacherRatings()
    ElMessage.success(t('teacherRating.recalculateResult', { count: res.data || 0 }))
    await fetchList()
  } catch (e) {
    ElMessage.error(t('teacherRating.recalculateFailed'))
  } finally {
    recalculatingAll.value = false
  }
}

onMounted(() => {
  fetchList()
})
</script>

<style scoped>
.teacher-rating-container {
  padding: var(--space-6);
  background: var(--el-bg-color-page);
  min-height: 100dvh;
  max-width: 1200px;
  margin: 0 auto;
}

.info-card, .filter-card, .table-card {
  margin-bottom: var(--space-6);
  background: var(--el-fill-color-blank);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs), var(--shadow-sm);
}

.info-content {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
}

.info-icon {
  color: var(--role-primary);
  flex-shrink: 0;
  margin-top: 2px;
}

.info-title {
  margin: 0 0 var(--space-1);
  font-size: var(--text-md);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
}

.info-desc {
  margin: 0;
  font-size: var(--text-base);
  color: var(--el-text-color-secondary);
}

.filter-bar {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.teacher-cell {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.teacher-name {
  font-weight: var(--weight-medium);
}

.tier-tag {
  font-weight: var(--weight-medium);
}

.score-value {
  font-size: var(--text-md);
  font-weight: var(--weight-bold);
  color: var(--role-primary);
  font-variant-numeric: tabular-nums;
}

.dialog-teacher {
  font-weight: var(--weight-medium);
}
</style>
