<template>
  <div class="achievement-wall">
    <!-- 面包屑导航 -->
    <el-breadcrumb class="page-breadcrumb">
      <el-breadcrumb-item :to="{ path: '/student/courses' }">首页</el-breadcrumb-item>
      <el-breadcrumb-item>成就墙</el-breadcrumb-item>
    </el-breadcrumb>

    <!-- 头部 -->
    <div class="achievement-wall-header">
      <h2>我的成就</h2>
      <span class="badge-count">
        已获得 {{ earnedBadges.length }} 个徽章 / 共 {{ allDefinitions.length }} 种徽章
      </span>
    </div>

    <!-- 骨架屏加载态 -->
    <template v-if="loading">
      <el-card class="earned-section" shadow="never">
        <template #header><span>已获得</span></template>
        <el-row :gutter="16">
          <el-col v-for="n in 4" :key="n" :xs="12" :sm="8" :md="6">
            <el-skeleton animated>
              <template #template>
                <div class="badge-card-skeleton">
                  <el-skeleton-item variant="circle" style="width: 40px; height: 40px;" />
                  <el-skeleton-item variant="text" style="width: 60%; margin-top: 12px;" />
                  <el-skeleton-item variant="text" style="width: 40%; margin-top: 8px;" />
                </div>
              </template>
            </el-skeleton>
          </el-col>
        </el-row>
      </el-card>
    </template>

    <!-- 加载失败 -->
    <template v-else-if="error">
      <el-result icon="error" title="加载失败" sub-title="成就数据加载异常，请稍后重试">
        <template #extra>
          <el-button type="primary" @click="fetchData">重新加载</el-button>
        </template>
      </el-result>
    </template>

    <!-- 全部为空 -->
    <template v-else-if="allDefinitions.length === 0">
      <el-empty description="暂无成就徽章数据" :image-size="100" />
    </template>

    <!-- 数据就绪 -->
    <template v-else>
      <!-- 已获得徽章区 -->
      <el-card class="earned-section" shadow="never">
        <template #header>
          <span>已获得</span>
        </template>
        <el-row :gutter="16">
          <el-col
            v-for="badge in earnedBadges"
            :key="badge.code"
            :xs="12" :sm="8" :md="6"
          >
            <el-card class="badge-card earned student-card-item" shadow="hover">
              <div class="badge-icon">
                <el-icon color="var(--role-primary)" :size="40"><Star /></el-icon>
              </div>
              <div class="badge-name">{{ badge.name }}</div>
              <div class="badge-date">获得于 {{ formatDate(badge.earnedAt) }}</div>
            </el-card>
          </el-col>
        </el-row>
        <el-empty
          v-if="earnedBadges.length === 0"
          description="暂无已获得徽章，继续加油！"
          :image-size="60"
        />
      </el-card>

      <!-- 未获得徽章区 -->
      <el-card class="locked-section" shadow="never">
        <template #header>
          <span>未解锁</span>
        </template>
        <el-row :gutter="16">
          <el-col
            v-for="badge in lockedBadges"
            :key="badge.code"
            :xs="12" :sm="8" :md="6"
          >
            <el-card class="badge-card locked student-card-item" shadow="never">
              <div class="badge-icon">
                <el-icon color="var(--el-text-color-placeholder)" :size="40"><Lock /></el-icon>
              </div>
              <div class="badge-name">{{ badge.name }}</div>
              <div class="badge-desc">{{ badge.description }}</div>
              <div class="badge-criteria">条件：{{ formatCriteria(badge.criteria) }}</div>
            </el-card>
          </el-col>
        </el-row>
        <el-empty
          v-if="lockedBadges.length === 0"
          description="所有徽章已解锁！"
          :image-size="60"
        />
      </el-card>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Star, Lock } from '@element-plus/icons-vue'
import { getBadgeDefinitions, getMyAchievements } from '@/api/badge'

const loading = ref(false)
const error = ref(false)
const allDefinitions = ref([])
const myAchievements = ref([])

// 已获得徽章（合并定义与成就数据）
const earnedBadges = computed(() => {
  return allDefinitions.value
    .filter(d => myAchievements.value.some(a => a.badgeCode === d.code))
    .map(d => {
      const ach = myAchievements.value.find(a => a.badgeCode === d.code)
      return { ...d, ...ach, earnedAt: ach?.earnedAt }
    })
})

// 未解锁徽章
const lockedBadges = computed(() =>
  allDefinitions.value.filter(d =>
    !myAchievements.value.some(a => a.badgeCode === d.code)
  )
)

function formatDate(dateStr) {
  if (!dateStr) return ''
  return dateStr.slice(0, 10)
}

function formatCriteria(criteria) {
  if (!criteria) return '未知条件'
  try {
    const obj = typeof criteria === 'string' ? JSON.parse(criteria) : criteria
    const typeMap = {
      streak_days: '连续学习打卡',
      total_courses: '完成课程数',
      total_videos: '观看视频数',
      total_exercises: '完成习题数',
      review_count: '复习次数',
      discussion_count: '发贴讨论数',
    }
    const type = typeMap[obj.type] || obj.type
    return `${type} ${obj.value} 次`
  } catch {
    return criteria
  }
}

onMounted(() => {
  fetchData()
})

async function fetchData() {
  loading.value = true
  error.value = false
  try {
    const [defRes, achRes] = await Promise.all([
      getBadgeDefinitions(),
      getMyAchievements(),
    ])
    allDefinitions.value = defRes.data?.items || defRes.data || []
    myAchievements.value = achRes.data?.items || achRes.data || []
  } catch (e) {
    console.error('加载成就数据失败', e)
    error.value = true
    ElMessage.error('加载成就数据失败，请稍后重试')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.achievement-wall {
  padding: var(--space-5);
  max-width: 1200px;
  margin: 0 auto;
}

.page-breadcrumb {
  margin-bottom: var(--space-4);
}

.badge-card-skeleton {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: var(--space-4) var(--space-2);
}

.achievement-wall-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-5);
}

.achievement-wall-header h2 {
  font-size: var(--text-xl);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
  margin: 0;
}

.badge-count {
  font-size: var(--text-sm);
  color: var(--el-text-color-secondary);
}

.earned-section,
.locked-section {
  margin-bottom: var(--space-5);
}

.badge-card {
  text-align: center;
  padding: var(--space-4) var(--space-2);
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  transition: transform var(--duration-base) var(--ease-out),
              box-shadow var(--duration-base) var(--ease-out);
}

.badge-card.earned {
  background: linear-gradient(135deg, #f0f3ff 0%, #e8edff 100%);
  border: 1px solid var(--role-primary-light-5, #c7d2fe);
}

.badge-card.locked {
  background: var(--el-fill-color-lighter, #f5f7fa);
  border: 1px dashed var(--el-border-color-lighter, #ebeef5);
  opacity: 0.8;
}

.badge-card:hover {
  transform: translateY(-2px);
}

.badge-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: var(--space-1);
}

.badge-name {
  font-size: var(--text-base);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
  text-align: center;
}

.badge-date {
  font-size: var(--text-xs);
  color: var(--el-text-color-secondary);
}

.badge-desc {
  font-size: var(--text-sm);
  color: var(--el-text-color-secondary);
  text-align: center;
  line-height: 1.4;
}

.badge-criteria {
  font-size: var(--text-xs);
  color: var(--el-text-color-placeholder);
  text-align: center;
}

@media (max-width: 768px) {
  .achievement-wall {
    padding: var(--space-3);
  }

  .achievement-wall-header {
    flex-direction: column;
    align-items: flex-start;
    gap: var(--space-2);
  }
}
</style>
