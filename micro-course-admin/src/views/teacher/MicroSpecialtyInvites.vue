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
      <el-result
        v-if="error"
        icon="error"
        title="加载失败"
        sub-title="请稍后重试"
      >
        <template #extra>
          <el-button type="primary" @click="fetchData(activeTab)">重试</el-button>
        </template>
      </el-result>
      <el-empty v-else-if="!loading && items.length === 0" :description="activeTab === 'pending' ? '暂无待处理邀请' : '暂无归档邀请'" />

      <div v-for="inv in items" :key="inv.id" class="invite-row">
        <div class="invite-main">
          <div class="invite-header">
            <span class="invite-ms">{{ inv.microSpecialtyTitle }}</span>
            <el-tag size="small">{{ roleMap[inv.role] || inv.role || '教师' }}</el-tag>
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
          <div v-else class="invite-result" style="display:flex;align-items:center;gap:8px;">
            <el-tag v-if="inv.status === 'ACTIVE'" type="success" size="small">已接受</el-tag>
            <el-tag v-else-if="inv.status === 'DECLINED'" type="danger" size="small">已拒绝</el-tag>
            <el-tag v-else-if="inv.status === 'REMOVED'" type="warning" size="small">已移除</el-tag>
            <el-tag v-else-if="inv.status === 'PENDING_ACADEMIC'" type="warning" size="small">跨学院审批中</el-tag>
            <el-tag v-else type="info" size="small">已过期</el-tag>
            <el-button v-if="inv.status === 'ACTIVE'" size="small" type="danger" plain @click="handleLeave(inv)">退出团队</el-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getPendingInvites, acceptInvite, declineInvite, leaveTeam } from '@/api/microSpecialty'

const roleMap = { LEAD: '负责人', MEMBER: '团队成员', ASSISTANT: '助教' }

const activeTab = ref('pending')
const loading = ref(false)
const error = ref(false)
const items = ref([])

const fetchData = async (tab) => {
  error.value = false
  loading.value = true
  try {
    const { data } = await getPendingInvites({ status: tab === 'archived' ? 'ALL' : 'PENDING' })
    let list = data.items || data || []
    const now = Date.now()
    list = list.map(i => {
      const dl = i.inviteExpiresAt ? new Date(i.inviteExpiresAt).getTime() : null
      const remMs = dl ? Math.max(0, dl - now) : 0
      const remDays = Math.floor(remMs / 86400000)
      const remHours = Math.floor((remMs % 86400000) / 3600000)
      return { ...i, expiring: remMs > 0 && remMs < 3 * 86400000, deadlineText: dl ? (remMs > 0 ? `剩余${remDays} 天 ${remHours} 小时` : '已过期') : '' }
    })
    if (tab === 'pending') list = list.filter(i => i.status === 'INVITED' || i.status === 'PENDING_ACADEMIC')
    else list = list.filter(i => i.status !== 'INVITED' && i.status !== 'PENDING_ACADEMIC')
    items.value = list
  } catch (e) { ElMessage.error(e?.response?.data?.message || '获取邀请列表失败'); error.value = true }
  finally { loading.value = false }
}

const handleTabChange = (name) => { activeTab.value = name; fetchData(name) }

const handleAccept = async (inv) => {
  try { await acceptInvite(inv.id); ElMessage.success('已接受'); fetchData(activeTab.value) }
  catch (e) { ElMessage.error(e?.response?.data?.message || '操作失败') }
}

const handleDecline = async (inv) => {
  try { await ElMessageBox.confirm('确定拒绝该邀请？', '提示', { type: 'warning' }) }
  catch { return }
  try { await declineInvite(inv.id); ElMessage.success('已拒绝'); fetchData(activeTab.value) }
  catch (e) { ElMessage.error(e?.response?.data?.message || '操作失败') }
}

const handleLeave = async (inv) => {
  try { await ElMessageBox.confirm('确定退出该微专业团队？', '提示', { type: 'warning' }) }
  catch { return }
  try { await leaveTeam(inv.id); ElMessage.success('已退出'); fetchData(activeTab.value) }
  catch (e) { ElMessage.error(e?.response?.data?.message || '操作失败') }
}

onMounted(() => fetchData('pending'))
</script>

<style scoped>
.ms-invites { padding: var(--space-4); max-width: 1000px; margin: 0 auto; }
.mg-bottom-16 { margin-bottom: var(--space-4); }
.invite-list { min-height: 200px; }
.invite-row { display: flex; justify-content: space-between; align-items: center; padding: var(--space-4) var(--space-3); border-bottom: 1px solid var(--el-border-color-lighter); transition: background var(--el-transition-duration) var(--el-transition-function-ease-in-out-bezier); }
.invite-row:hover { background: var(--el-fill-color-light); }
/* Phase 2: 键盘焦点可访问性 */
.invite-row:focus-within {
  background: #f5f7fa;
  outline: 2px solid #409eff;
}
.invite-main { flex: 1; }
.invite-header { display: flex; align-items: center; gap: var(--space-2); margin-bottom: var(--space-1); }
.invite-ms { font-weight: 600; font-size: var(--el-font-size-base); }
.invite-detail { display: flex; gap: var(--space-4); font-size: var(--el-font-size-extra-small); color: var(--el-text-color-secondary); }
.invite-right { display: flex; flex-direction: column; align-items: flex-end; gap: var(--space-2); }
.invite-deadline { font-size: var(--el-font-size-small); color: var(--el-text-color-secondary); }
.invite-deadline.expiring { color: var(--el-color-danger); font-weight: 600; }
.invite-actions { display: flex; gap: var(--space-2); }
</style>
