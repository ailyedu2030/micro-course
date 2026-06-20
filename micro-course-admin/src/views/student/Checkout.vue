<!--
  订单结算页
  购物车 → 确认订单 → 支付
-->
<template>
  <div class="checkout-page">
    <nav class="page-breadcrumb">结算</nav>

    <el-alert v-if="paid" title="支付成功！" type="success" show-icon :closable="false" class="mg-bottom-16" />

    <el-row :gutter="20">
      <el-col :span="16">
        <el-card shadow="never" class="section-card">
          <template #header>确认订单</template>
          <el-table :data="store.items" stripe border>
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
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useCartStore } from '@/store/cart'
import { useUserStore } from '@/store/user'
import { createOrder, payOrder } from '@/api/order'
import { Wallet } from '@element-plus/icons-vue'

const router = useRouter()
const store = useCartStore()
const userStore = useUserStore()
const submitting = ref(false)
const paid = ref(false)
const paymentMethod = ref('BALANCE')

onMounted(() => {
  if (!store.hasItems) {
    ElMessage.info('购物车为空')
    router.push('/student/courses')
  }
})

async function handleSubmit() {
  if (!store.hasItems) return
  try {
    await ElMessageBox.confirm(`确认支付 ¥${store.totalPrice}？`, '确认支付', {
      confirmButtonText: '支付', cancelButtonText: '取消', type: 'info'
    })
  } catch { return }

  submitting.value = true
  try {
    const items = [...store.items]
    for (const item of items) {
      try {
        const { data: order } = await createOrder({ courseId: item.courseId })
        if (order.status !== 'PAID') {
          await payOrder(order.id, paymentMethod.value)
        }
        store.removeItem(item.courseId)
      } catch (e) {
        const msg = e?.response?.data?.message || e?.response?.data?.code || e.message || '支付失败'
        ElMessage.error(`「${item.title}」${msg}`)
      }
    }
    if (store.count === 0) {
      paid.value = true
      ElMessage.success('全部处理完成！')
      setTimeout(() => router.push('/student/my-courses'), 1500)
    }
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.checkout-page { padding: var(--space-6); max-width: 1200px; margin: 0 auto; min-height: 100dvh; background: var(--el-bg-color-page); }
.page-breadcrumb { margin-bottom: var(--space-4); font-size: var(--text-md); font-weight: var(--weight-semibold); }
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
