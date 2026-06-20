<template>
  <div class="bundle-square">
    <nav class="page-breadcrumb" aria-label="面包屑">
      <span>课程套件</span>
      <span class="sub-hint">多课打包 · 系统学习</span>
    </nav>

    <div v-loading="loading" class="bundle-grid">
      <el-row :gutter="24">
        <el-col v-for="bundle in bundles" :key="bundle.id" :xs="24" :sm="12" :md="8" :lg="6">
          <article class="bundle-card student-card-item" @click="goBundle(bundle.id)">
            <div class="bundle-cover">
              <img v-if="bundle.coverUrl" :src="bundle.coverUrl" class="cover-img" />
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
const bundles = ref([])
const page = ref(0)
const size = ref(20)
const total = ref(0)

const fetchBundles = async () => {
  loading.value = true
  try {
    const { data } = await getBundles({ page: page.value, size: size.value })
    bundles.value = data.items || []
    total.value = data.totalElements || 0
  } catch {}
  finally { loading.value = false }
}

const goBundle = (id) => router.push(`/student/bundles/${id}`)

onMounted(() => fetchBundles())
</script>

<style scoped>
.bundle-square { padding: 16px; max-width: 1400px; margin: 0 auto; }
.page-breadcrumb { margin-bottom: 20px; font-size: 20px; font-weight: 700; color: #303133; }
.sub-hint { font-size: 13px; font-weight: 400; color: #909399; margin-left: 12px; }
.bundle-grid { min-height: 200px; }

.bundle-card { border-radius: 12px; overflow: hidden; cursor: pointer; transition: all .25s; border: 1px solid #ebeef5; background: #fff; margin-bottom: 24px; }
.bundle-card:hover { transform: translateY(-3px); box-shadow: 0 8px 25px rgba(0,0,0,.1); border-color: var(--el-color-primary-light-3); }

.bundle-cover { position: relative; height: 160px; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); display: flex; align-items: center; justify-content: center; }
.cover-placeholder { color: rgba(255,255,255,.5); }
.cover-img { width: 100%; height: 100%; object-fit: cover; }

.price-tag { position: absolute; bottom: 10px; right: 10px; background: #f56c6c; color: #fff; padding: 3px 10px; border-radius: 6px; font-size: 13px; font-weight: 600; }
.price-tag.free { background: #67c23a; }

.bundle-info { padding: 14px 16px 16px; }
.bundle-title { font-size: 15px; font-weight: 600; color: #303133; margin: 0 0 6px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.bundle-desc { font-size: 12px; color: #909399; margin: 0 0 8px; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; line-height: 1.4; }
.bundle-meta { font-size: 12px; color: #c0c4cc; margin: 0; }

.pagination-wrap { display: flex; justify-content: center; margin-top: 16px; }
</style>
