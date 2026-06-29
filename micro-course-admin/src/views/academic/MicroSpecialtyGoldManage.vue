<!--
  金标管理（教务处端）
  路由: /academic/micro-specialties/gold
-->
<template>
  <div class="ms-gold">
    <el-page-header @back="$router.back()" content="金标管理" class="mg-bottom-16" />

    <el-alert
      :title="`金标位已用 ${goldCount} / 2（上限 2）`"
      :type="goldCount >= 2 ? 'warning' : 'info'"
      show-icon
      :closable="false"
      class="mg-bottom-16"
    />

    <el-card shadow="never">
      <el-alert v-if="error" title="加载失败" type="error" show-icon :closable="false" class="mg-bottom-12">
        <template #default><el-button size="small" @click="fetchMicroSpecialties">重试</el-button></template>
      </el-alert>
      <el-table v-loading="loading" :data="items" stripe border>
        <template #empty><el-empty description="暂无微专业可设金标" /></template>
        <el-table-column prop="title" label="微专业" min-width="200" show-overflow-tooltip />
        <el-table-column prop="collegeName" label="学院" width="120" />
        <el-table-column label="当前金标" width="120" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isGoldFeatured" type="warning" size="small">金标</el-tag>
            <span v-else class="no-tag">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="enrollmentCount" label="选课人数" width="100" align="center" />
        <el-table-column prop="featuredAt" label="置顶时间" width="130" align="center">
          <template #default="{ row }">{{ row.featuredAt?.slice(0, 10) || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" align="center" fixed="right">
          <template #default="{ row }">
            <template v-if="row.isGoldFeatured">
              <el-button size="small" type="danger" :loading="actingId === row.id" @click="handleUnsetGold(row)">取消金标</el-button>
            </template>
            <template v-else>
              <el-button
                size="small"
                type="warning"
                :loading="actingId === row.id"
                :disabled="goldCount >= 2"
                @click="handleSetGold(row)"
              >
                {{ goldCount >= 2 ? '已达上限' : '设为金标' }}
              </el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination mg-top-12">
        <el-pagination
          v-model:current-page="page"
          :page-size="size"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="fetchMicroSpecialties"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getMicroSpecialtyList, setGoldFeatured, unsetGoldFeatured } from '@/api/microSpecialty'

const loading = ref(false)
const actingId = ref(null)
const items = ref([])
const page = ref(0)
const size = ref(20)
const total = ref(0)
const error = ref(false)

const goldCount = computed(() => items.value.filter(i => i.isGoldFeatured).length)

const fetchMicroSpecialties = async () => {
  loading.value = true
  error.value = false
  try {
    const res = await getMicroSpecialtyList({
      // 移除 isGoldFeatured=false 过滤, 需要拿全量 RECRUITING 数据来统计金标数量

      page: 0,
      size: 100
    })
    // Filter for RECRUITING status
    items.value = (res.data.items || res.data || [])
      .filter(item => item.status === 'RECRUITING')
    total.value = res.data.totalElements || 0
  } catch (e) {
    error.value = true
    ElMessage.error('获取微专业列表失败')
  } finally {
    loading.value = false
  }
}

const handleSetGold = async (row) => {
  actingId.value = row.id
  try { await setGoldFeatured(row.id); ElMessage.success('已设为金标'); fetchMicroSpecialties() }
  catch (e) { ElMessage.error(e?.response?.data?.message || '操作失败') }
  finally { actingId.value = null }
}

const handleUnsetGold = async (row) => {
  actingId.value = row.id
  try { await unsetGoldFeatured(row.id); ElMessage.success('已取消金标'); fetchMicroSpecialties() }
  catch (e) { ElMessage.error(e?.response?.data?.message || '操作失败') }
  finally { actingId.value = null }
}

onMounted(fetchMicroSpecialties)
</script>

<style scoped>
.ms-gold { padding: var(--space-4); max-width: 1200px; margin: 0 auto; }
.mg-bottom-16 { margin-bottom: var(--space-4); }
.mg-bottom-12 { margin-bottom: var(--space-3); }
.mg-top-12 { margin-top: var(--space-3); }
.pagination { display: flex; justify-content: flex-end; }
.no-tag { color: var(--el-text-color-placeholder); }
</style>
