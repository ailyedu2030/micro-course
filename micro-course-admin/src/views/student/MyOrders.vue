<template>
  <div class="my-orders">
    <nav class="page-breadcrumb" aria-label="面包屑">
      <span>我的订单</span>
    </nav>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="orders" class="data-table" stripe border>
        <el-table-column prop="orderNo" label="订单号" min-width="200" show-overflow-tooltip />
        <el-table-column prop="courseTitle" label="课程" min-width="200" show-overflow-tooltip />
        <el-table-column prop="amount" label="金额" width="120" align="center">
          <template #default="{ row }">
            <span v-if="row.amount" style="color:#f56c6c;font-weight:600">¥{{ row.amount }}</span>
            <span v-else style="color:#67c23a">免费</span>
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
            >支付</el-button>
            <el-button
              v-if="row.courseId"
              size="small"
              @click="goCourse(row.courseId)"
            >查看课程</el-button>
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
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getMyOrders, payOrder } from '@/api/order'

const router = useRouter()
const loading = ref(false)
const payingId = ref(null)
const orders = ref([])
const page = ref(0)
const size = ref(20)
const total = ref(0)

const fetchOrders = async () => {
  loading.value = true
  try {
    const { data } = await getMyOrders({ page: page.value, size: size.value })
    orders.value = data.items || []
    total.value = data.totalElements || 0
  } catch { ElMessage.error('加载订单失败') }
  finally { loading.value = false }
}

const handlePay = async (row) => {
  payingId.value = row.id
  try {
    await payOrder(row.id, 'BALANCE')
    ElMessage.success('支付成功')
    fetchOrders()
  } catch { ElMessage.error('支付失败') }
  finally { payingId.value = null }
}

const goCourse = (id) => router.push(`/student/courses/${id}`)

onMounted(() => fetchOrders())
</script>

<style scoped>
.my-orders { padding: 16px; max-width: 1200px; margin: 0 auto; }
.page-breadcrumb { margin-bottom: 16px; font-size: 16px; font-weight: 600; color: #303133; }
.pagination-wrap { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
