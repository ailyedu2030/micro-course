<!--
  学生端-我的收藏
  路由: /student/favorites
  P1C-015: 学生端收藏列表页面
-->
<template>
  <div class="student-favorites-page">
    <div class="page-header">
      <h1>我的收藏</h1>
    </div>

    <el-card v-if="!loading && items.length > 0" shadow="never" class="favorites-card">
      <div
        v-for="item in items"
        :key="item.id"
        class="favorite-row"
        tabindex="0"
        role="button"
        :aria-label="'课程：' + item.courseTitle"
        @click="goCourse(item.courseId)"
        @keydown.enter="goCourse(item.courseId)"
      >
        <div class="course-cover" v-if="item.coverUrl">
          <el-image :src="item.coverUrl" fit="cover" class="thumb-img" lazy />
        </div>
        <div class="course-cover placeholder" v-else>
          <el-icon><VideoCamera /></el-icon>
        </div>
        <div class="course-info">
          <span class="course-title">{{ item.courseTitle || '未命名课程' }}</span>
          <span class="course-meta" v-if="item.teacherName">{{ item.teacherName }}</span>
        </div>
        <el-button
          type="danger"
          link
          size="small"
          @click.stop="handleRemove(item)"
        >
          取消收藏
        </el-button>
      </div>
    </el-card>

    <el-empty v-else-if="!loading" description="暂无收藏的课程" :image-size="100" />

    <div v-loading="loading" class="loading-container" v-if="loading" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { VideoCamera } from '@element-plus/icons-vue'
import { getMyFavorites, cancelFavorite } from '@/api/favorite'

const router = useRouter()
const loading = ref(true)
const items = ref([])

async function fetchFavorites() {
  loading.value = true
  try {
    const { data } = await getMyFavorites()
    items.value = Array.isArray(data) ? data : (data?.items || [])
  } catch {
    ElMessage.error('获取收藏列表失败')
  } finally {
    loading.value = false
  }
}

function goCourse(courseId) {
  if (courseId) router.push(`/student/courses/${courseId}`)
}

async function handleRemove(item) {
  try {
    await ElMessageBox.confirm('确定取消收藏？', '提示', { type: 'warning' })
    await cancelFavorite(item.id)
    ElMessage.success('已取消收藏')
    items.value = items.value.filter(i => i.id !== item.id)
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('操作失败')
  }
}

onMounted(fetchFavorites)
</script>

<style scoped>
.student-favorites-page {
  max-width: 800px;
  margin: 0 auto;
  padding: var(--space-5);
  min-height: 100dvh;
  background: var(--el-bg-color-page);
}
.page-header h1 {
  font-size: var(--text-xl);
  font-weight: var(--weight-bold);
  color: var(--el-text-color-primary);
  margin: 0 0 var(--space-5);
}
.favorites-card {
  border-radius: var(--radius-lg);
}
.favorite-row {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-2);
  border-bottom: 1px solid var(--el-border-color-lighter);
  cursor: pointer;
  transition: background var(--duration-base) var(--ease-out);
}
.favorite-row:last-child {
  border-bottom: none;
}
.favorite-row:hover {
  background: var(--el-fill-color-light);
}
.course-cover {
  width: 80px;
  height: 50px;
  border-radius: var(--radius-sm);
  overflow: hidden;
  flex-shrink: 0;
  background: var(--el-fill-color);
  display: flex;
  align-items: center;
  justify-content: center;
}
.thumb-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.placeholder .el-icon {
  font-size: 24px;
  color: var(--el-text-color-placeholder);
}
.course-info {
  flex: 1;
  min-width: 0;
}
.course-title {
  display: block;
  font-size: var(--text-md);
  font-weight: var(--weight-medium);
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.course-meta {
  display: block;
  font-size: var(--text-xs);
  color: var(--el-text-color-secondary);
  margin-top: 2px;
}
.loading-container {
  min-height: 200px;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
