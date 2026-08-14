<!--
  我的订单
  路由路径: /student/orders
  Phase 9
  Author: Phase9-Development-Team
-->
<template>
  <div class="my-orders">
    <nav class="page-breadcrumb" :aria-label="$t('myOrders.title')">
      <span>{{ $t('myOrders.title') }}</span>
    </nav>

    <el-card shadow="never">
      <!-- 订单状态筛选（后端 getMyOrders 支持 status） -->
      <div v-if="orders.length > 0 || statusFilter !== ''" class="order-filter-bar">
        <el-select
          v-model="statusFilter"
          :placeholder="$t('myOrders.allStatuses')"
          clearable
          class="order-status-filter"
          :aria-label="$t('myOrders.filterAria')"
          @change="handleStatusFilterChange"
        >
          <el-option :label="$t('myOrders.all')" value="" />
          <el-option :label="$t('myOrders.statusPending')" value="PENDING" />
          <el-option :label="$t('myOrders.statusPaid')" value="PAID" />
          <el-option :label="$t('myOrders.statusCancelled')" value="CANCELLED" />
          <el-option :label="$t('myOrders.statusRefunded')" value="REFUNDED" />
        </el-select>
      </div>

      <!-- 空状态 -->
      <el-empty
        v-if="!loading && orders.length === 0"
        :description="$t('myOrders.noData')"
        :image-size="120"
      >
        <el-button type="primary" @click="router.push('/student/courses')">{{ $t('myOrders.goCourseSquare') }}</el-button>
        <el-button v-if="statusFilter !== ''" type="default" @click="handleClearFilter">{{ $t('course.clearFilter') }}</el-button>
      </el-empty>

      <!-- 订单列表 -->
      <template v-else>
        <el-table v-loading="loading" :data="orders" class="data-table" stripe border>
        <el-table-column prop="orderNo" :label="$t('myOrders.orderNo')" min-width="200" show-overflow-tooltip />
        <el-table-column prop="courseTitle" :label="$t('myOrders.product')" min-width="200" show-overflow-tooltip />
        <el-table-column prop="amount" :label="$t('cart.amount')" width="120" align="center">
          <template #default="{ row }">
            <span v-if="row.amount" class="price-paid">¥{{ row.amount }}</span>
            <span v-else class="price-free">{{ $t('app.free') }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="$t('app.status')" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'PAID'" type="success" size="small">{{ $t('myOrders.statusPaid') }}</el-tag>
            <el-tag v-else-if="row.status === 'PENDING'" type="warning" size="small">{{ $t('myOrders.statusPending') }}</el-tag>
            <el-tag v-else-if="row.status === 'CANCELLED'" type="info" size="small">{{ $t('myOrders.statusCancelled') }}</el-tag>
            <el-tag v-else-if="row.status === 'REFUNDED'" type="danger" size="small">{{ $t('myOrders.statusRefunded') }}</el-tag>
            <el-tag v-else size="small">{{ row.statusText }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('cart.paymentMethod')" width="100" align="center">
          <template #default="{ row }">
            {{ row.paymentMethod || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" :label="$t('app.time')" width="170" show-overflow-tooltip :formatter="$formatDateTime" />
        <el-table-column :label="$t('app.operation')" width="140" align="center" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'PENDING'"
              size="small" type="primary"
              :loading="payingId === row.id"
              @click="handlePay(row)"
            >
{{ $t('course.payBtn') }}
</el-button>
            <el-button
              v-if="row.status === 'PENDING'"
              size="small" type="warning" plain
              :loading="cancellingId === row.id"
              @click="handleCancel(row)"
            >
{{ $t('myOrders.cancelOrder') }}
</el-button>
            <el-button
              v-if="row.status === 'PAID'"
              size="small" type="danger" plain
              :loading="refundingId === row.id"
              @click="handleRefund(row)"
            >
{{ $t('myOrders.applyRefund') }}
</el-button>
            <el-button
              v-if="row.bundleId"
              size="small"
              @click="goBundle(row.bundleId)"
            >
{{ $t('myOrders.viewBundle') }}
</el-button>
            <el-button
              v-else-if="row.courseId"
              size="small"
              @click="goCourse(row.courseId)"
            >
{{ $t('myOrders.viewCourse') }}
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
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMyOrders, payOrder, cancelOrder, refundOrder } from '@/api/order'
import { useAsyncData } from '@/composables/useAsyncData'
import { useUrlPagination } from '@/composables/useUrlPagination'
import { useErrorHandler } from '@/composables/useErrorHandler'

const router = useRouter()
const { t } = useI18n()
const payingId = ref(null)
const refundingId = ref(null)
const cancellingId = ref(null)
const orders = ref([])
const page = ref(1)
const size = ref(20)
const total = ref(0)
const statusFilter = ref('')

// P2-14: URL 分页同步
const { bindToQuery } = useUrlPagination()
bindToQuery(page, size, null, [])

// Round 11-3: 统一异步加载 + 统一错误处理
const { handleError, handleSuccess } = useErrorHandler()
const { loading, execute } = useAsyncData((p, status) => getMyOrders({ page: p - 1, size: size.value, status }))

const fetchOrders = async () => {
  try {
    const { data } = await execute(page.value, statusFilter.value)
    orders.value = data.items || []
    total.value = data.totalElements || 0
  } catch (e) {
    handleError(e, t('myOrders.fetchFailed'))
  }
}

const handleStatusFilterChange = () => {
  page.value = 1
  fetchOrders()
}

// 空结果时通过"清除筛选"按钮恢复完整订单列表
const handleClearFilter = () => {
  statusFilter.value = ''
  page.value = 1
  fetchOrders()
}

const handlePay = async (row) => {
  payingId.value = row.id
  try {
    await payOrder(row.id, 'BALANCE')
    handleSuccess(t('myOrders.paySuccess'))
    fetchOrders()
  } catch (e) {
    handleError(e, t('order.failed'))
  } finally {
    payingId.value = null
  }
}

const handleRefund = async (row) => {
  try {
    await ElMessageBox.confirm(t('myOrders.refundConfirm'), t('myOrders.refundConfirmTitle'), {
      confirmButtonText: t('myOrders.refundConfirmBtn'),
      cancelButtonText: t('common.cancel'),
      type: 'warning'
    })
    refundingId.value = row.id
    await refundOrder(row.id)
    handleSuccess(t('myOrders.refundSubmitted'))
    fetchOrders()
  } catch (e) {
    if (e !== 'cancel') {
      handleError(e, t('myOrders.refundFailed'))
    }
  } finally {
    refundingId.value = null
  }
}

const handleCancel = async (row) => {
  try {
    await ElMessageBox.confirm(t('myOrders.cancelConfirm'), t('myOrders.cancelOrder'), {
      confirmButtonText: t('myOrders.cancelConfirmBtn'), cancelButtonText: t('myOrders.cancelBtn'), type: 'warning'
    })
    cancellingId.value = row.id
    await cancelOrder(row.id)
    ElMessage.success(t('myOrders.cancelledSuccess'))
    fetchOrders()
  } catch (e) {
    if (e !== 'cancel') handleError(e, t('myOrders.cancelFailed'))
  } finally {
    cancellingId.value = null
  }
}

const goCourse = (id) => router.push(`/student/courses/${id}`)
const goBundle = (id) => router.push(`/student/bundles/${id}`)

// setup 阶段即发起首次加载，execute 同步置 loading=true（保持首屏 loading 行为）
fetchOrders()
</script>

<style scoped>
.my-orders { padding: var(--space-6); min-height: 100dvh; max-width: 1200px; margin: 0 auto; background: var(--el-bg-color-page); }
.page-breadcrumb { margin-bottom: var(--space-4); font-size: var(--text-md); font-weight: var(--weight-semibold); color: var(--el-text-color-primary); }
.pagination-wrap { display: flex; justify-content: flex-end; margin-top: var(--space-4); padding-top: var(--space-4); border-top: 1px solid var(--el-border-color-lighter); }
.order-filter-bar { display: flex; justify-content: flex-end; margin-bottom: var(--space-4); }
.order-status-filter { width: 160px; }
.price-paid { color: var(--el-color-danger); font-weight: var(--weight-semibold); }
.price-free { color: var(--el-color-success); }
</style>
