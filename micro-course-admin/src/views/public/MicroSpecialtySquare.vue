<template>
  <div class="ms-square fade-in">
    <section class="hero-section">
      <div class="hero-content">
        <h1 class="hero-title">微专业</h1>
        <p class="hero-subtitle">跨学科培养方案 · 提升核心竞争力</p>
      </div>
    </section>

    <section v-if="loading" class="section">
      <div class="loading-row">
        <div v-for="n in 6" :key="n" class="skeleton-card">
          <el-skeleton animated>
            <template #template>
              <div class="skel-cover" />
              <div class="skel-body">
                <el-skeleton-item variant="text" style="width: 70%" />
                <el-skeleton-item variant="text" style="width: 50%; margin-top: 8px" />
              </div>
            </template>
          </el-skeleton>
        </div>
      </div>
    </section>

    <section v-else-if="error" class="section">
      <el-result status="error" title="加载失败" sub-title="请稍后重试">
        <template #extra>
          <el-button type="primary" @click="fetchData">重新加载</el-button>
        </template>
      </el-result>
    </section>

    <template v-else>
      <section v-if="goldFeatured.length" class="section">
        <h2 class="section-title">学校重点推荐</h2>
        <div class="gold-row">
          <div
            v-for="item in goldFeatured"
            :key="'gold-'+item.id"
            class="gold-card"
            role="button"
            tabindex="0"
            @click="handleClick(item.id)"
            @keydown.enter.prevent="handleClick(item.id)"
            @keydown.space.prevent="handleClick(item.id)"
          >
            <span class="gold-badge">🏆 重点推荐</span>
            <div class="gold-cover">
              <img v-if="item.coverUrl" :src="item.coverUrl" :alt="item.title" loading="lazy" class="gold-cover-img" />
              <div v-else class="gold-cover-placeholder"><el-icon :size="40"><Notebook /></el-icon></div>
            </div>
            <div class="gold-info">
              <h3 class="gold-title">{{ item.title }}</h3>
              <p class="gold-meta">{{ item.departmentName }} · {{ item.leadTeacherName }}</p>
              <p class="gold-stats">{{ item.totalCredits || 0 }} 学分 | {{ item.courseCount || 0 }} 门课</p>
              <el-button type="primary" size="small" round class="gold-cta">了解详情</el-button>
            </div>
          </div>
        </div>
      </section>

      <section v-if="featured.length" class="section">
        <h2 class="section-title">推荐微专业</h2>
        <div class="card-grid">
          <div
            v-for="item in featured"
            :key="'feat-'+item.id"
            class="ms-card"
            role="button"
            tabindex="0"
            @click="handleClick(item.id)"
            @keydown.enter.prevent="handleClick(item.id)"
            @keydown.space.prevent="handleClick(item.id)"
          >
            <div class="ms-cover">
              <img v-if="item.coverUrl" :src="item.coverUrl" :alt="item.title" loading="lazy" class="ms-cover-img" />
              <div v-else class="ms-cover-placeholder"><el-icon :size="28"><Notebook /></el-icon></div>
              <span v-if="item.isNew" class="new-badge">NEW</span>
            </div>
            <div class="ms-body">
              <h4 class="ms-title">{{ item.title }}</h4>
              <p class="ms-dept">{{ item.departmentName }}</p>
              <p class="ms-stats">{{ item.totalCredits || 0 }} 学分 · {{ item.courseCount || 0 }} 门课</p>
            </div>
          </div>
        </div>
      </section>

      <section v-if="recruiting.length" class="section">
        <h2 class="section-title">招生中</h2>
        <div class="card-grid">
          <div
            v-for="item in recruiting"
            :key="'rec-'+item.id"
            class="ms-card"
            role="button"
            tabindex="0"
            @click="handleClick(item.id)"
            @keydown.enter.prevent="handleClick(item.id)"
            @keydown.space.prevent="handleClick(item.id)"
          >
            <div class="ms-cover">
              <img v-if="item.coverUrl" :src="item.coverUrl" :alt="item.title" loading="lazy" class="ms-cover-img" />
              <div v-else class="ms-cover-placeholder"><el-icon :size="28"><Notebook /></el-icon></div>
            </div>
            <div class="ms-body">
              <h4 class="ms-title">{{ item.title }}</h4>
              <p class="ms-dept">{{ item.departmentName }}</p>
              <p class="ms-stats">{{ item.totalCredits || 0 }} 学分 · {{ item.courseCount || 0 }} 门课</p>
            </div>
          </div>
        </div>
      </section>

      <el-empty v-if="!goldFeatured.length && !featured.length && !recruiting.length" description="暂无微专业项目，敬请期待" />
    </template>

    <section v-if="!isLoggedIn" class="login-prompt">
      <p>已有账号？<router-link to="/login">登录</router-link> 查看完整的课程内容和学习记录</p>
    </section>
  </div>
</template>

<script setup>
import { computed, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Notebook } from '@element-plus/icons-vue'
import { getSquareData } from '@/api/microSpecialty'
import { isAuthenticated } from '@/utils/auth'

const router = useRouter()
const loading = ref(true)
const error = ref(false)
const goldFeatured = ref([])
const featured = ref([])
const recruiting = ref([])
const isLoggedIn = computed(() => isAuthenticated())

const fetchData = async () => {
  loading.value = true
  error.value = false
  try {
    const { data } = await getSquareData()
    goldFeatured.value = data?.goldFeatured || []
    featured.value = data?.featured || []
    recruiting.value = data?.recruiting || []
  } catch (e) {
    console.error('[MicroSpecialtySquare] 加载失败:', e)
    error.value = true
  } finally {
    loading.value = false
  }
}

const handleClick = (id) => {
  if (isAuthenticated()) {
    router.push(`/student/micro-specialties/${id}`)
  } else {
    router.push(`/login?redirect=/student/micro-specialties/${id}`)
  }
}

onMounted(fetchData)
</script>

<style scoped>
.ms-square { max-width: 1200px; margin: 0 auto; padding: 24px 32px; min-height: 100dvh; }
.hero-section {
  padding: 48px 32px; border-radius: 16px;
  background: linear-gradient(155deg, #409eff, #2c6ddb, #1a4fa0);
  text-align: center; margin-bottom: 32px;
}
.hero-title { margin: 0; font-size: 36px; font-weight: 700; color: #fff; }
.hero-subtitle { margin: 8px 0 0; font-size: 16px; color: rgba(255,255,255,.7); }
.section { margin-bottom: 40px; }
.section-title { font-size: 22px; font-weight: 700; margin: 0 0 20px; display: flex; align-items: center; gap: 8px; }
.section-title::before { content: ''; width: 4px; height: 22px; background: #409eff; border-radius: 4px; }

.gold-row { display: flex; gap: 20px; }
.gold-card {
  flex: 1; display: flex; gap: 20px; padding: 24px;
  background: linear-gradient(135deg, #fefce8, #fef9c3);
  border: 1px solid #fde68a; border-radius: 16px;
  cursor: pointer; outline: none; position: relative; overflow: hidden;
  transition: transform .2s, box-shadow .2s;
}
.gold-card:hover { transform: translateY(-3px); box-shadow: 0 8px 24px rgba(0,0,0,.1); }
.gold-badge {
  position: absolute; top: 8px; right: -8px;
  background: linear-gradient(135deg, #f59e0b, #fbbf24); color: #fff;
  font-size: 12px; font-weight: 700; padding: 4px 14px 4px 10px;
  border-radius: 0 0 0 10px; z-index: 2;
}
.gold-cover { width: 200px; min-width: 200px; height: 140px; border-radius: 12px; overflow: hidden; background: linear-gradient(135deg, #eef2ff, #e0e7ff); }
.gold-cover-img { width: 100%; height: 100%; object-fit: cover; }
.gold-cover-placeholder { width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; color: #a5b4fc; }
.gold-info { flex: 1; display: flex; flex-direction: column; justify-content: center; }
.gold-title { font-size: 20px; font-weight: 700; margin: 0 0 6px; }
.gold-meta { font-size: 13px; color: #666; margin: 0 0 4px; }
.gold-stats { font-size: 14px; margin: 0 0 12px; font-weight: 500; }
.gold-cta { align-self: flex-start; }

.card-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 20px; }
.ms-card {
  border-radius: 14px; overflow: hidden; cursor: pointer; outline: none;
  background: #fff; box-shadow: 0 2px 12px rgba(0,0,0,.06);
  transition: transform .2s, box-shadow .2s;
}
.ms-card:hover { transform: translateY(-4px); box-shadow: 0 8px 24px rgba(0,0,0,.1); }
.ms-cover { position: relative; width: 100%; aspect-ratio: 16/9; overflow: hidden; background: linear-gradient(135deg, #eef2ff, #e0e7ff); }
.ms-cover-img { width: 100%; height: 100%; object-fit: cover; }
.ms-cover-placeholder { width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; color: #a5b4fc; }
.new-badge { position: absolute; top: 6px; right: 6px; background: #10b981; color: #fff; font-size: 10px; font-weight: 700; padding: 2px 6px; border-radius: 4px; z-index: 2; }
.ms-body { padding: 12px 14px; }
.ms-title { font-size: 15px; font-weight: 600; margin: 0 0 4px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.ms-dept { font-size: 12px; color: #999; margin: 0 0 4px; }
.ms-stats { font-size: 12px; color: #666; }

.loading-row { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 20px; }
.skeleton-card { background: #f5f7fa; border-radius: 14px; overflow: hidden; }
.skel-cover { aspect-ratio: 16/9; background: #e8eaed; }
.skel-body { padding: 12px; }

.login-prompt { text-align: center; padding: 24px; color: #999; font-size: 14px; border-top: 1px solid #eee; margin-top: 40px; }
.login-prompt a { color: #409eff; text-decoration: none; }
.login-prompt a:hover { text-decoration: underline; }
</style>
