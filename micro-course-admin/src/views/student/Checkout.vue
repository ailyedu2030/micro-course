<!--
  订单结算页
  购物车 → 确认订单 → 支付
-->
<template>
  <div class="checkout-page">
    <nav class="page-breadcrumb" aria-label="面包屑">
      <router-link to="/student/courses" class="bc-link">课程广场</router-link>
      <span class="bc-sep">/</span>
      <span>结算</span>
    </nav>

    <el-alert v-if="paid" title="支付成功！" type="success" show-icon :closable="false" class="mg-bottom-16" />

    <el-row :gutter="20">
      <el-col :span="16">
        <el-card shadow="never" class="section-card">
          <template #header>确认订单</template>
          <el-table v-loading="loading" :data="store.items" stripe border>
            <el-table-column label="课程" min-width="200">
              <template #default="{ row }">
                <div class="course-cell">
                  <el-image v-if="row.coverUrl" :src="row.coverUrl" class="cell-cover" fit="cover" />
                  <span>{{ row.title }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="teacherName" label="教师" width="120" />
            <el-table-column label="金额" width="100" align="center">
              <template #default="{ row }">
                <span v-if="!row.isFree" class="price">¥{{ row.price }}</span>
                <span v-else class="free">免费</span>
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <el-card shadow="never" class="section-card">
          <template #header>支付方式</template>
          <el-radio-group v-model="paymentMethod" class="payment-methods">
            <el-radio value="BALANCE" border class="payment-option">
              <div class="payment-label">
                <el-icon><Wallet /></el-icon>
                <span>余额支付</span>
              </div>
            </el-radio>
          </el-radio-group>
          <p class="payment-hint">当前仅支持余额支付，其他支付方式即将开放</p>
        </el-card>
      </el-col>

      <el-col :span="8">
        <el-card shadow="never" class="summary-card">
          <template #header>订单摘要</template>
          <div class="summary-row"><span>课程数量</span><span>{{ store.count }} 门</span></div>
          <div class="summary-row"><span>合计</span><span class="total-price">¥{{ store.totalPrice }}</span></div>
          <el-divider />
          <el-button type="primary" size="large" class="full-width" :loading="submitting" :disabled="store.count === 0 || paid" @click="handleSubmit">
            确认支付 ¥{{ store.totalPrice }}
          </el-button>
        </el-card>
      </el-col>
    </el-row>

    <!-- 支付结果明细弹窗 -->
    <el-dialog v-model="showResultDialog" title="支付结果" width="600px" :close-on-click-modal="false">
      <p><strong>成功：{{ resultSummary.success.length }} 门</strong></p>
      <ul v-if="resultSummary.success.length > 0" style="margin-bottom:16px">
        <li v-for="o in resultSummary.success" :key="o.courseTitle">
          {{ o.courseTitle }} - ¥{{ o.amount }}
        </li>
      </ul>
      <p v-if="resultSummary.failed.length > 0" style="color:#F56C6C">
        <strong>失败：{{ resultSummary.failed.length }} 门</strong>
      </p>
      <ul v-if="resultSummary.failed.length > 0">
        <li v-for="o in resultSummary.failed" :key="o.courseTitle" style="color:#F56C6C">
          {{ o.courseTitle }} - {{ o.errorMsg }}
        </li>
      </ul>
      <template #footer>
        <el-button @click="showResultDialog = false" v-if="resultSummary.failed.length > 0">关闭</el-button>
        <el-button type="warning" @click="handleRetryFailed" v-if="resultSummary.failed.length > 0" :loading="retrying">重试失败项</el-button>
        <el-button type="primary" @click="router.push('/student/my-courses')" v-if="resultSummary.success.length > 0">查看我的课程</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useCartStore } from '@/store/cart'
import { useUserStore } from '@/store/user'
import { createOrder, payOrder, batchCreateOrders } from '@/api/order'
import { Wallet } from '@element-plus/icons-vue'

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

onMounted(() => {
  if (!store.hasItems) {
    loading.value = false
    ElMessage.info('购物车为空')
    router.push('/student/courses')
    return
  }
  loading.value = false
})

async function handleSubmit() {
  if (submitting.value) return  // ★ 防重复提交
  if (!store.hasItems) {
    ElMessage.warning('购物车为空或部分商品已下架')
    return
  }
  try {
    await ElMessageBox.confirm(`确认支付 ¥${store.totalPrice}？`, '确认支付', {
      confirmButtonText: '支付', cancelButtonText: '取消', type: 'info'
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
      // 全部成功
      items.forEach(i => {
        successItems.push({ courseTitle: i.title, amount: i.price, status: 'PAID' })
        store.removeItem(i.courseId)
      })
      resultSummary.value = { success: successItems, failed: [] }
      showResultDialog.value = true
      paid.value = true
      return
    } catch (batchError) {
      // 批量失败，降级到逐一处理
      ElMessage.warning('批量处理失败，正在逐一处理…')
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
        const msg = e?.response?.data?.message || e?.response?.data?.code || e.message || '支付失败'
        failedItems.push({ courseId: item.courseId, courseTitle: item.title, amount: item.price, errorMsg: msg, status: 'FAILED' })
        ElMessage.error(`「${item.title}」${msg}`)
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
      retriedSuccess.push({ courseTitle: item.title, amount: item.amount, status: 'PAID' })
      store.removeItem(item.courseId)
    } catch (e) {
      const msg = e?.response?.data?.message || e?.response?.data?.code || e.message || '支付失败'
      retriedFailed.push({ courseTitle: item.title, amount: item.amount, errorMsg: msg, status: 'FAILED' })
      ElMessage.error(`「${item.title}」${msg}`)
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
    ElMessage.warning(`重试完成：${retriedSuccess.length} 成功，${retriedFailed.length} 失败`)
  } else {
    ElMessage.success('所有课程支付成功！')
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
</style>
