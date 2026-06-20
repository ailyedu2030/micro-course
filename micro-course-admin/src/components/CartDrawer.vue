<!--
  购物车抽屉
  学生端侧边滑出购物车
-->
<template>
  <el-drawer v-model="visible" title="购物车" size="380px" :with-header="true">
    <template #title>
      <span class="drawer-title">购物车 <el-tag v-if="store.count" type="danger" size="small">{{ store.count }}</el-tag></span>
    </template>

    <div v-if="!store.hasItems" class="empty-state">
      <el-empty description="购物车空空如也" :image-size="100" />
    </div>

    <div v-else class="cart-items">
      <div v-for="item in store.items" :key="item.courseId" class="cart-item">
        <el-image v-if="item.coverUrl" :src="item.coverUrl" fit="cover" class="item-cover" />
        <div class="item-cover-placeholder" v-else>
          <el-icon :size="24"><Notebook /></el-icon>
        </div>
        <div class="item-info">
          <div class="item-title">{{ item.title }}</div>
          <div class="item-teacher">{{ item.teacherName || '未知教师' }}</div>
        </div>
        <div class="item-price">
          <span v-if="!item.isFree" class="price">¥{{ item.price }}</span>
          <span v-else class="free">免费</span>
        </div>
        <el-button text type="danger" size="small" @click="store.removeItem(item.courseId)">
          <el-icon><Delete /></el-icon>
        </el-button>
      </div>
    </div>

    <template #footer>
      <div v-if="store.hasItems" class="drawer-footer">
        <div class="footer-total">
          <span class="total-label">合计</span>
          <span class="total-price">¥{{ store.totalPrice }}</span>
        </div>
        <el-button type="primary" size="large" class="checkout-btn" @click="goCheckout" :disabled="!store.hasItems">
          去结算
        </el-button>
      </div>
    </template>
  </el-drawer>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useCartStore } from '@/store/cart'
import { Delete, Notebook } from '@element-plus/icons-vue'

const router = useRouter()
const store = useCartStore()
const visible = ref(false)

function open() { visible.value = true }
function close() { visible.value = false }
function goCheckout() {
  visible.value = false
  router.push('/student/checkout')
}

defineExpose({ open, close })
</script>

<style scoped>
.drawer-title { display: flex; align-items: center; gap: 8px; font-weight: var(--weight-semibold); }
.empty-state { display: flex; justify-content: center; align-items: center; height: 300px; }
.cart-items { display: flex; flex-direction: column; gap: 12px; }
.cart-item { display: flex; align-items: center; gap: 12px; padding: 12px; border-radius: 8px; background: var(--el-fill-color-light); }
.item-cover { width: 60px; height: 60px; border-radius: 6px; object-fit: cover; flex-shrink: 0; }
.item-cover-placeholder { width: 60px; height: 60px; border-radius: 6px; background: var(--el-fill-color); display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.item-info { flex: 1; min-width: 0; }
.item-title { font-size: var(--text-sm); font-weight: var(--weight-medium); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.item-teacher { font-size: var(--text-xs); color: var(--el-text-color-secondary); margin-top: 2px; }
.item-price { flex-shrink: 0; }
.price { color: var(--el-color-danger); font-weight: var(--weight-semibold); }
.free { color: var(--el-color-success); font-size: var(--text-xs); }
.drawer-footer { padding-top: 12px; border-top: 1px solid var(--el-border-color-lighter); }
.footer-total { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.total-label { font-size: var(--text-base); }
.total-price { font-size: 20px; font-weight: var(--weight-bold); color: var(--el-color-danger); }
.checkout-btn { width: 100%; }
</style>
