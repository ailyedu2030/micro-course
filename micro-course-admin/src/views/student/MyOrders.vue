<template>
  <div class="my-orders">
    <nav class="page-breadcrumb" aria-label="面包屑">
      <span>我的订单</span>
    </nav>

    <el-card shadow="never">
      <!-- 空状态 -->
      <el-empty
        v-if="!loading && orders.length === 0"
        description="暂无订单"
        :image-size="120"
      >
        <el-button type="primary" @click="router.push('/student/courses')">去课程广场</el-button>
      </el-empty>

      <!-- 订单列表 -->
      <template v-else>
        <el-table v-loading="loading" :data="orders" class="data-table" stripe border>
        <el-table-column prop="orderNo" label="订单号" min-width="200" show-overflow-tooltip />
        <el-table-column prop="courseTitle" label="课程" min-width="200" show-overflow-tooltip />
        <el-table-column prop="amount" label="金额" width="120" align="center">
          <template #default="{ row }">
            <span v-if="row.amount" class="price-paid">¥{{ row.amount }}</span>
            <span v-else class="price-free">免费</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'PAID'" type="success" size="small">已支付</el-tag>
            <el-tag v-else-if="row.status === 'PENDING'" type="warning" size="small">待支付</el-tag>
            <el-tag v-else-if="row.status === 'CANCELLED'" type="info" size="small">已取消</el-tag>
            <el-tag v-else size="small">{{ row.statusText }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="支付方式" width="100" align="center">
          <template #default="{ row }">
            {{ row.paymentMethod || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="时间" width="170" show-overflow-tooltip />
        <el-table-column label="操作" width="140" align="center" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'PENDING'"
              size="small" type="primary"
              :loading="payingId === row.id"
              @click="handlePay(row)"
            >
支付
</el-button>
            <el-button
              v-if="row.courseId"
              size="small"
              @click="goCourse(row.courseId)"
            >
查看课程
</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="page"
          :page-size="size"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="fetchOrders"
        />
      </div>
      </template>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { getMyOrders, payOrder } from '@/api/order'
import { useAsyncData } from '@/composables/useAsyncData'
import { useErrorHandler } from '@/composables/useErrorHandler'

const router = useRouter()
const payingId = ref(null)
const orders = ref([])
const page = ref(0)
const size = ref(20)
const total = ref(0)

// Round 11-3: 统一异步加载 + 统一错误处理
const { handleError, handleSuccess } = useErrorHandler()
const { loading, execute } = useAsyncData((p) => getMyOrders({ page: p, size: size.value }))

const fetchOrders = async () => {
  try {
    const { data } = await execute(page.value)
    orders.value = data.items || []
    total.value = data.totalElements || 0
  } catch (e) {
    handleError(e, '加载订单失败')
  }
}

const handlePay = async (row) => {
  payingId.value = row.id
  try {
    await payOrder(row.id, 'BALANCE')
    handleSuccess('支付成功')
    fetchOrders()
  } catch (e) {
    handleError(e, '支付失败')
  } finally {
    payingId.value = null
  }
}

const goCourse = (id) => router.push(`/student/courses/${id}`)

// setup 阶段即发起首次加载，execute 同步置 loading=true（保持首屏 loading 行为）
fetchOrders()
</script>

<style scoped>
.my-orders { padding: var(--space-6); min-height: 100dvh; max-width: 1200px; margin: 0 auto; background: var(--el-bg-color-page); }
.page-breadcrumb { margin-bottom: var(--space-4); font-size: var(--text-md); font-weight: var(--weight-semibold); color: var(--el-text-color-primary); }
.pagination-wrap { display: flex; justify-content: flex-end; margin-top: var(--space-4); padding-top: var(--space-4); border-top: 1px solid var(--el-border-color-lighter); }
.price-paid { color: var(--el-color-danger); font-weight: var(--weight-semibold); }
.price-free { color: var(--el-color-success); }
</style>
