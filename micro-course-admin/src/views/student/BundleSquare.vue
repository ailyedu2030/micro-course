<!--
  课程套件广场
  路由路径: /student/bundles
  Phase 9
  Author: Phase9-Development-Team
-->
<template>
  <div class="bundle-square">
    <nav class="page-breadcrumb" aria-label="面包屑">
      <span>课程套件</span>
      <span class="sub-hint">多课打包 · 系统学习</span>
    </nav>

    <el-result
      v-if="error"
      icon="error"
      title="加载失败"
      sub-title="网络异常，请稍后重试"
      class="bundle-error"
    >
      <template #extra>
        <el-button type="primary" @click="fetchBundles">重试</el-button>
      </template>
    </el-result>

    <div v-else v-loading="loading" class="bundle-grid">
      <el-row :gutter="24">
        <el-col v-for="bundle in bundles" :key="bundle.id" :xs="24" :sm="12" :md="8" :lg="6">
          <article class="bundle-card student-card-item" @click="goBundle(bundle.id)">
            <div class="bundle-cover">
                <img v-if="bundle.coverUrl" :src="bundle.coverUrl" :alt="bundle.title || '课程套件封面'" class="cover-img" />
              <div v-else class="cover-placeholder">
                <el-icon :size="36"><Collection /></el-icon>
              </div>
              <div class="price-tag" :class="{ free: bundle.isFree || !bundle.price }">
                {{ bundle.isFree || !bundle.price ? '免费' : '¥' + bundle.price }}
              </div>
            </div>
            <div class="bundle-info">
              <h3 class="bundle-title">{{ bundle.title }}</h3>
              <p class="bundle-desc" v-if="bundle.description">{{ bundle.description }}</p>
              <p class="bundle-meta">{{ bundle.studentCount || 0 }} 人学习 · {{ bundle.creatorName }}</p>
            </div>
          </article>
        </el-col>
      </el-row>

      <el-empty v-if="!loading && bundles.length === 0" description="暂无课程套件" />
    </div>

    <div class="pagination-wrap" v-if="total > size">
      <el-pagination
        v-model:current-page="page"
        :page-size="size"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="fetchBundles"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getBundles } from '@/api/bundle'
import { Collection } from '@element-plus/icons-vue'

const router = useRouter()
const loading = ref(false)
const error = ref(false)
const bundles = ref([])
const page = ref(0)
const size = ref(20)
const total = ref(0)

const fetchBundles = async () => {
  loading.value = true
  error.value = false
  try {
    const { data } = await getBundles({ page: page.value, size: size.value })
    bundles.value = data.items || []
    total.value = data.totalElements || 0
  } catch (e) {
    console.error('[BundleSquare] 加载课程套件失败:', e)
    error.value = true
  }
  finally { loading.value = false }
}

const goBundle = (id) => router.push(`/student/bundles/${id}`)

onMounted(() => fetchBundles())
</script>

<style scoped>
.bundle-square { padding: var(--space-6); min-height: 100dvh; max-width: 1400px; margin: 0 auto; background: var(--el-bg-color-page); }
.page-breadcrumb { margin-bottom: var(--space-5); font-size: var(--text-xl); font-weight: var(--weight-bold); color: var(--el-text-color-primary); }
.sub-hint { font-size: var(--text-sm); font-weight: var(--weight-regular); color: var(--el-text-color-secondary); margin-left: var(--space-3); }
.bundle-grid { min-height: 200px; }

.bundle-card { border-radius: var(--radius-lg); overflow: hidden; cursor: pointer; transition: all var(--duration-base) var(--ease-out); background: var(--el-fill-color-blank); box-shadow: var(--shadow-xs), var(--shadow-sm); margin-bottom: var(--space-6); }
.bundle-card:hover { transform: translateY(-4px); box-shadow: var(--shadow-md), var(--shadow-lg); border-color: var(--el-color-primary-light-3); }

.bundle-cover { position: relative; height: 160px; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); display: flex; align-items: center; justify-content: center; }
.cover-placeholder { color: rgba(255,255,255,.5); }
.cover-img { width: 100%; height: 100%; object-fit: cover; }

.price-tag { position: absolute; bottom: var(--space-2-5); right: var(--space-2-5); background: var(--el-color-danger); color: #fff; padding: var(--space-0-5) var(--space-2-5); border-radius: var(--radius-md); font-size: var(--text-sm); font-weight: var(--weight-semibold); }
.price-tag.free { background: var(--el-color-success); }

.bundle-info { padding: var(--space-3-5) var(--space-4) var(--space-4); }
.bundle-title { font-size: var(--text-md); font-weight: var(--weight-semibold); color: var(--el-text-color-primary); margin: 0 0 var(--space-1-5); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.bundle-desc { font-size: var(--text-xs); color: var(--el-text-color-secondary); margin: 0 0 var(--space-2); display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; line-height: 1.4; }
.bundle-meta { font-size: var(--text-xs); color: var(--el-text-color-secondary); margin: 0; }

.pagination-wrap { display: flex; justify-content: center; margin-top: var(--space-4); padding-top: var(--space-4); border-top: 1px solid var(--el-border-color-lighter); }
</style>
