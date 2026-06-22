<!--
  邀请列表（教师端）
  路由: /teacher/micro-specialties/invites
-->
<template>
  <div class="ms-invites">
    <el-page-header @back="$router.back()" content="邀请列表" class="mg-bottom-16" />

    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane label="待处理" name="pending" />
      <el-tab-pane label="已归档" name="archived" />
    </el-tabs>

    <div v-loading="loading" class="invite-list">
      <el-empty v-if="!loading && items.length === 0" :description="activeTab === 'pending' ? '暂无待处理邀请' : '暂无归档邀请'" />

      <div v-for="inv in items" :key="inv.id" class="invite-row">
        <div class="invite-main">
          <div class="invite-header">
            <span class="invite-ms">{{ inv.microSpecialtyTitle }}</span>
            <el-tag size="small">{{ inv.role || '教师' }}</el-tag>
          </div>
          <div class="invite-detail">
            <span>邀请人：{{ inv.inviterName }}</span>
            <span>邀请时间：{{ inv.createdAt?.slice(0, 10) || '-' }}</span>
          </div>
        </div>
        <div class="invite-right">
          <div class="invite-deadline" :class="{ 'expiring': inv.expiring }">
            {{ inv.deadlineText || '已过期' }}
          </div>
          <div v-if="activeTab === 'pending'" class="invite-actions">
            <el-button size="small" type="primary" @click="handleAccept(inv)">接受</el-button>
            <el-button size="small" @click="handleDecline(inv)">拒绝</el-button>
          </div>
          <div v-else class="invite-result">
            <el-tag v-if="inv.status === 'ACCEPTED'" type="success" size="small">已接受</el-tag>
            <el-tag v-else-if="inv.status === 'DECLINED'" type="danger" size="small">已拒绝</el-tag>
            <el-tag v-else type="info" size="small">已过期</el-tag>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getPendingInvites, acceptInvite, declineInvite } from '@/api/microSpecialty'

const activeTab = ref('pending')
const loading = ref(false)
const items = ref([])

const fetchData = async (tab) => {
  loading.value = true
  try {
    const { data } = await getPendingInvites({ status: tab === 'archived' ? 'ALL' : 'PENDING' })
    let list = data.items || data || []
    const now = Date.now()
    list = list.map(i => {
      const dl = i.deadline ? new Date(i.deadline).getTime() : null
      const rem = dl ? Math.max(0, Math.ceil((dl - now) / 86400000)) : null
      return { ...i, expiring: rem !== null && rem < 3, deadlineText: dl ? (rem > 0 ? `剩余${rem}天` : '已过期') : '' }
    })
    if (tab === 'pending') list = list.filter(i => i.status === 'PENDING')
    else list = list.filter(i => i.status !== 'PENDING')
    items.value = list
  } catch { ElMessage.error('加载失败') }
  finally { loading.value = false }
}

const handleTabChange = (name) => { activeTab.value = name; fetchData(name) }

const handleAccept = async (inv) => {
  try { await acceptInvite(inv.id); ElMessage.success('已接受'); fetchData(activeTab.value) }
  catch { ElMessage.error('操作失败') }
}

const handleDecline = async (inv) => {
  try { await ElMessageBox.confirm('确定拒绝该邀请？', '提示', { type: 'warning' }) }
  catch { return }
  try { await declineInvite(inv.id); ElMessage.success('已拒绝'); fetchData(activeTab.value) }
  catch { ElMessage.error('操作失败') }
}

onMounted(() => fetchData('pending'))
</script>

<style scoped>
.ms-invites { padding: var(--space-4); max-width: 1000px; margin: 0 auto; }
.mg-bottom-16 { margin-bottom: var(--space-4); }
.invite-list { min-height: 200px; }
.invite-row { display: flex; justify-content: space-between; align-items: center; padding: var(--space-4) var(--space-3); border-bottom: 1px solid var(--el-border-color-lighter); transition: background var(--el-transition-duration) var(--el-transition-function-ease-in-out-bezier); }
.invite-row:hover { background: var(--el-fill-color-light); }
.invite-main { flex: 1; }
.invite-header { display: flex; align-items: center; gap: var(--space-2); margin-bottom: var(--space-1); }
.invite-ms { font-weight: 600; font-size: var(--el-font-size-base); }
.invite-detail { display: flex; gap: var(--space-4); font-size: var(--el-font-size-extra-small); color: var(--el-text-color-secondary); }
.invite-right { display: flex; flex-direction: column; align-items: flex-end; gap: var(--space-2); }
.invite-deadline { font-size: var(--el-font-size-small); color: var(--el-text-color-secondary); }
.invite-deadline.expiring { color: var(--el-color-danger); font-weight: 600; }
.invite-actions { display: flex; gap: var(--space-2); }
</style>
