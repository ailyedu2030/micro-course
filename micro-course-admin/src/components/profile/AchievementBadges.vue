<!--
  我的成就徽章卡片（Round 11-3 从 Profile.vue 拆分）
  AchievementBadges: 成就徽章网格展示，自包含 fetch
  Author: jackie
-->
<template>
  <el-card class="profile-card achievement-card" shadow="never">
    <template #header>
      <div class="card-header">
        <span>我的成就</span>
        <el-button v-if="!isMobile" type="primary" link @click="router.push('/student/achievements')">
          查看全部 <el-icon><ArrowRight /></el-icon>
        </el-button>
      </div>
    </template>
    <div v-loading="badgeLoading" :aria-busy="badgeLoading" class="badge-grid" :class="{ 'badge-grid--mobile': isMobile }">
      <div
        v-for="badge in allBadges"
        :key="badge.badgeType"
        class="badge-item student-card-item"
        :class="{ 'badge-locked': !badge.earnedAt }"
        role="button"
        tabindex="0"
        :aria-label="badge.badgeName"
        @click="goToAchievements"
        @keydown.enter="goToAchievements"
        @keydown.space.prevent="goToAchievements"
      >
        <div class="badge-icon">
          <el-icon v-if="badge.earnedAt" color="var(--role-primary)" :size="isMobile ? 28 : 32"><Star /></el-icon>
          <el-icon v-else color="var(--el-text-color-placeholder)" :size="isMobile ? 28 : 32"><Lock /></el-icon>
        </div>
        <div class="badge-name">{{ badge.badgeName }}</div>
        <div v-if="badge.earnedAt" class="badge-date">{{ formatBadgeDate(badge.earnedAt) }}</div>
        <div v-else class="badge-tip">未解锁</div>
      </div>
    </div>
    <!-- P2-2026-08-21: allBadges 恒 3 项(锁定态设计)，此空态永不触发 → 移除死代码 -->
  </el-card>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { Star, Lock, ArrowRight } from '@element-plus/icons-vue'
import { getMyBadges } from '@/api/badge'

defineProps({
  isMobile: { type: Boolean, default: false }
})

const router = useRouter()

const badgeLoading = ref(false)
const earnedBadges = ref([])

const allBadges = computed(() => {
  const badgeTypes = [
    { badgeType: 'FIRST_COURSE', badgeName: '初识课程' },
    { badgeType: 'ALL_COURSES', badgeName: '学满全部' },
    { badgeType: 'SEVEN_DAY_STREAK', badgeName: '连续打卡' }
  ]
  return badgeTypes.map(b => {
    // P1-2026-08-21: 后端 AchievementVO 字段是 badgeCode(非 badgeType)，原匹配恒不中 → 已获徽章显示未解锁
    const earned = earnedBadges.value.find(e => e.badgeCode === b.badgeType)
    return earned ? { ...b, ...earned } : { ...b, earnedAt: null }
  })
})

// P2-2026-08-21: earnedAt 原始 ISO 时间戳直出 → 格式化为本地日期
function formatBadgeDate(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  if (isNaN(d.getTime())) return iso
  return d.toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })
}

const goToAchievements = () => {
  router.push('/student/achievements')
}

const fetchBadges = async () => {
  badgeLoading.value = true
  try {
    const res = await getMyBadges()
    earnedBadges.value = res.data || []
  } catch (e) {
    console.warn('[Profile] 获取成就徽章失败:', e)
    earnedBadges.value = []
  } finally {
    badgeLoading.value = false
  }
}

fetchBadges()
</script>

<style scoped>
.profile-card {
  margin-bottom: var(--space-5);
  border-radius: var(--radius-lg);
  transition: transform var(--duration-base) ease, box-shadow var(--duration-base) ease;
}
.profile-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-lg);
}
.card-header {
  font-size: var(--text-base);
  font-weight: var(--weight-medium);
  color: var(--el-text-color-primary);
}

.achievement-card :deep(.el-card__header) {
  padding: 12px var(--space-5);
}

.badge-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--space-4);
}
.badge-grid--mobile {
  grid-template-columns: repeat(2, 1fr);
  gap: var(--space-3);
}

.badge-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-4) var(--space-3);
  border-radius: var(--radius-md);
  background: var(--role-primary-light);
  transition: transform var(--duration-base) ease, box-shadow var(--duration-base) ease;
}
.badge-item:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-lg);
}

.student-card-item {
  border: 1px solid rgba(99, 102, 241, 0.1);
  backdrop-filter: blur(4px);
}
.student-card-item:not(.badge-locked) {
  background: linear-gradient(135deg, var(--role-primary-light) 0%, var(--role-primary-light-9) 100%);
}
.badge-item.badge-locked {
  background: var(--el-fill-color-light);
  /* A11Y(2026-08-05): 锁定态此前用整卡 opacity:0.7，
     axe 按透明度混合后"未解锁"文本有效对比度降至 2.99:1(serious)。
     移除透明度，改为浅灰底 + 虚线边框区分锁定，文本保持满不透明度达标。 */
  border: 1px dashed var(--el-border-color);
  opacity: 1;
}

.badge-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}
.badge-name {
  font-size: var(--text-sm);
  font-weight: var(--weight-medium);
  color: var(--el-text-color-primary);
  text-align: center;
}
.badge-date {
  font-size: var(--text-xs);
  color: var(--role-primary);
  text-align: center;
}
.badge-tip {
  font-size: var(--text-xs);
  /* A11Y: placeholder 色(#a8abb2)在白底对比度仅 ~2.3:1 → 用 regular(#606266 ~5.4:1) */
  color: var(--el-text-color-regular);
  text-align: center;
}

:deep(.el-button) { cursor: pointer; }

@media (max-width: 768px) {
  .profile-card { margin-bottom: var(--space-4); }
}
</style>
