<!--
  订单结算页
  购物车 → 确认订单 → 支付
-->
<template>
  <div class="checkout-page">
    <nav class="page-breadcrumb" :aria-label="$t('cart.breadcrumbAria')">
      <router-link to="/student/courses" class="bc-link">{{ $t('course.square') }}</router-link>
      <span class="bc-sep">/</span>
      <span>{{ $t('cart.checkout') }}</span>
    </nav>

    <el-alert v-if="paid" :title="$t('order.success')" type="success" show-icon :closable="false" class="mg-bottom-16" />

    <el-row :gutter="20">
      <el-col :xs="24" :sm="16">
        <el-card shadow="never" class="section-card">
          <template #header>{{ $t('cart.confirmOrder') }}</template>
          <el-table v-loading="loading" :data="store.items" stripe border>
            <el-table-column :label="$t('course.title')" min-width="200">
              <template #default="{ row }">
                <div class="course-cell">
                  <el-image v-if="row.coverUrl" :src="row.coverUrl" :alt="$t('cart.coverAlt', { title: row.title || $t('course.title') })" class="cell-cover" fit="cover" />
                  <span>{{ row.title }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="teacherName" :label="$t('course.teacher')" width="120" />
            <el-table-column :label="$t('cart.amount')" width="100" align="center">
              <template #default="{ row }">
                <span v-if="!row.isFree" class="price">¥{{ row.price }}</span>
                <span v-else class="free">{{ $t('app.free') }}</span>
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <el-card shadow="never" class="section-card">
          <template #header>{{ $t('cart.paymentMethod') }}</template>
          <el-radio-group v-model="paymentMethod" class="payment-methods">
            <el-radio value="BALANCE" border class="payment-option">
              <div class="payment-label">
                <el-icon><Wallet /></el-icon>
                <span>{{ $t('cart.balancePayment') }}</span>
              </div>
            </el-radio>
          </el-radio-group>
          <p class="payment-hint">{{ $t('cart.paymentHint') }}</p>
        </el-card>
      </el-col>

      <el-col :xs="24" :sm="8">
        <el-card shadow="never" class="summary-card">
          <template #header>{{ $t('cart.orderSummary') }}</template>
          <div class="summary-row"><span>{{ $t('cart.courseCount') }}</span><span>{{ store.count }} {{ $t('course.title') }}</span></div>
          <div class="summary-row"><span>{{ $t('cart.totalPrice') }}</span><span class="total-price">¥{{ store.totalPrice }}</span></div>
          <el-divider />
          <el-button type="primary" size="large" class="full-width" :loading="submitting" :disabled="store.count === 0 || paid" @click="handleSubmit">
            {{ $t('order.pay') }} ¥{{ store.totalPrice }}
          </el-button>
        </el-card>
      </el-col>
    </el-row>

    <!-- 支付结果明细弹窗 -->
    <el-dialog v-model="showResultDialog" :title="$t('order.paymentResult')" width="600px" class="checkout-result-dialog" :close-on-click-modal="false" :aria-label="$t('order.paymentResult')">
      <p><strong>{{ $t('order.successCount') }}：{{ resultSummary.success.length }}</strong></p>
      <ul v-if="resultSummary.success.length > 0" style="margin-bottom:16px">
        <li v-for="o in resultSummary.success" :key="o.courseTitle">
          {{ o.courseTitle }} - ¥{{ o.amount }}
        </li>
      </ul>
      <p v-if="resultSummary.failed.length > 0" style="color:var(--el-color-danger-dark-2)">
        <strong>{{ $t('order.failedCount') }}：{{ resultSummary.failed.length }}</strong>
      </p>
      <ul v-if="resultSummary.failed.length > 0">
        <li v-for="o in resultSummary.failed" :key="o.courseTitle" style="color:var(--el-color-danger-dark-2)">
          {{ o.courseTitle }} - {{ o.errorMsg }}
        </li>
      </ul>
      <template #footer>
        <el-button @click="showResultDialog = false" v-if="resultSummary.failed.length > 0">{{ $t('common.close') }}</el-button>
        <el-button type="warning" @click="handleRetryFailed" v-if="resultSummary.failed.length > 0" :loading="retrying">{{ $t('order.retryFailed') }}</el-button>
        <el-button type="primary" @click="router.push('/student/my-courses')" v-if="resultSummary.success.length > 0">{{ $t('order.viewMyCourses') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useCartStore } from '@/store/cart'
import { useUserStore } from '@/store/user'
import { createOrder, payOrder, batchCreateOrders } from '@/api/order'
import { Wallet } from '@element-plus/icons-vue'

const { t } = useI18n()
const router = useRouter()
const store = useCartStore()
const userStore = useUserStore()
const loading = ref(true)
const submitting = ref(false)
const paid = ref(false)
const paymentMethod = ref('BALANCE')
const resultSummary = ref({ success: [], failed: [] })
const showResultDialog = ref(false)
const retrying = ref(false)

onMounted(async () => {
  await store.loadFromServer()
  if (!store.hasItems) {
    loading.value = false
    ElMessage.info(t('cart.cartEmpty'))
    router.push('/student/courses')
    return
  }
  loading.value = false
})

async function handleSubmit() {
  if (submitting.value) return  // ★ 防重复提交
  if (!store.hasItems) {
    ElMessage.warning(t('cart.cartEmptyOrOffline'))
    return
  }
  try {
    await ElMessageBox.confirm(t('cart.confirmPayMessage', { amount: store.totalPrice }), t('cart.pendingPayment'), {
      confirmButtonText: t('cart.pay'), cancelButtonText: t('common.cancel'), type: 'info'
    })
  } catch { return }

  submitting.value = true
  resultSummary.value = { success: [], failed: [] }
  const successItems = []
  const failedItems = []
  try {
    // 优先使用批量下单接口（事务原子性）
    const items = [...store.items]
    try {
      const { data: orders } = await batchCreateOrders(
        items.map(i => i.courseId),
        paymentMethod.value
      )
      // LD-005 修复: 逐项检查订单状态，非PAID订单放入失败列表
      const failedOrders = []
      orders.forEach((order, idx) => {
        if (order && order.status === 'PAID') {
          successItems.push({ courseTitle: items[idx]?.title || order.courseTitle || '未知课程', amount: order.amount, status: 'PAID' })
          store.removeItem(items[idx]?.courseId)
        } else {
          failedOrders.push({
            courseId: items[idx]?.courseId,
            courseTitle: items[idx]?.title || order?.courseTitle || t('course.unknown'),
            amount: order?.amount ?? items[idx]?.price,
            errorMsg: order?.status === 'PENDING' ? t('cart.orderPendingMsg') : t('order.failed'),
            status: order?.status || 'FAILED'
          })
        }
      })
      resultSummary.value = { success: successItems, failed: failedOrders }
      showResultDialog.value = true
      if (failedOrders.length === 0) {
        paid.value = true
      } else {
        ElMessage.warning(t('cart.failedOrdersWarning', { count: failedOrders.length }))
      }
      return
    } catch (batchError) {
      // 批量失败，降级到逐一处理
      ElMessage.warning(t('cart.batchFailedMsg'))
    }

    // 降级：逐一创建订单并支付
    for (const item of items) {
      try {
        const { data: order } = await createOrder({ courseId: item.courseId })
        if (order.status !== 'PAID') {
          await payOrder(order.id, paymentMethod.value)
        }
        successItems.push({ courseTitle: item.title, amount: item.price, status: 'PAID' })
        store.removeItem(item.courseId)
      } catch (e) {
        const msg = e?.response?.data?.message || e?.response?.data?.code || e.message || t('order.failed')
        failedItems.push({ courseId: item.courseId, courseTitle: item.title, amount: item.price, errorMsg: msg, status: 'FAILED' })
        ElMessage.error(t('cart.itemPayError', { title: item.title, msg }))
      }
    }
    resultSummary.value = { success: successItems, failed: failedItems }
    showResultDialog.value = true
    if (successItems.length > 0 && failedItems.length === 0) {
      paid.value = true
    }
  } finally {
    submitting.value = false
  }
}

// P1C-010: 重试失败项
async function handleRetryFailed() {
  if (retrying.value) return
  retrying.value = true
  const failed = [...resultSummary.value.failed]
  const retriedSuccess = []
  const retriedFailed = []
  for (const item of failed) {
    try {
      const { data: order } = await createOrder({ courseId: item.courseId })
      if (order.status !== 'PAID') {
        await payOrder(order.id, paymentMethod.value)
      }
      retriedSuccess.push({ courseTitle: item.courseTitle, amount: item.amount, status: 'PAID' })
      store.removeItem(item.courseId)
    } catch (e) {
      const msg = e?.response?.data?.message || e?.response?.data?.code || e.message || t('order.failed')
      retriedFailed.push({ courseId: item.courseId, courseTitle: item.courseTitle, amount: item.amount, errorMsg: msg, status: 'FAILED' })
      ElMessage.error(t('cart.itemPayError', { title: item.courseTitle, msg }))
    }
  }
  resultSummary.value = {
    success: [...resultSummary.value.success, ...retriedSuccess],
    failed: retriedFailed
  }
  if (retriedSuccess.length > 0 && retriedFailed.length === 0) {
    paid.value = true
  }
  if (retriedFailed.length > 0) {
    ElMessage.warning(t('cart.retrySummary', { success: retriedSuccess.length, failed: retriedFailed.length }))
  } else {
    ElMessage.success(t('cart.allPaidSuccess'))
  }
  retrying.value = false
}
</script>

<style scoped>
.checkout-page { padding: var(--space-6); max-width: 1200px; margin: 0 auto; min-height: 100dvh; background: var(--el-bg-color-page); }
.page-breadcrumb { margin-bottom: var(--space-4); font-size: var(--text-md); font-weight: var(--weight-semibold); }
.bc-link { color: var(--el-text-color-secondary); text-decoration: none; transition: color var(--duration-base) var(--ease-out); }
.bc-link:hover { color: var(--role-primary); }
.bc-sep { margin: 0 var(--space-2); color: var(--el-border-color); font-weight: var(--weight-regular); }
.section-card { margin-bottom: var(--space-4); }
.course-cell { display: flex; align-items: center; gap: 12px; }
.cell-cover { width: 48px; height: 48px; border-radius: 6px; flex-shrink: 0; }
.price { color: var(--el-color-danger); font-weight: var(--weight-semibold); }
.free { color: var(--el-color-success); }
.payment-methods { display: flex; flex-direction: column; gap: 12px; }
.payment-option { width: 100%; margin-right: 0 !important; }
.payment-label { display: flex; align-items: center; gap: 8px; }
.payment-hint { font-size: var(--text-xs); color: var(--el-text-color-secondary); margin-top: 8px; }
.summary-card { position: sticky; top: var(--space-4); }
.summary-row { display: flex; justify-content: space-between; padding: 8px 0; font-size: var(--text-base); }
.total-price { color: var(--el-color-danger); font-size: 20px; font-weight: var(--weight-bold); }
.full-width { width: 100%; }
.mg-bottom-16 { margin-bottom: 16px; }
/* P2-2026-08-21: 支付结果弹窗 375px 不溢出 */
:deep(.checkout-result-dialog) { width: 92vw !important; max-width: 600px; }
</style>
