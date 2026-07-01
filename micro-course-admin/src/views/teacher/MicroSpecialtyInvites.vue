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

    <!-- Phase 3: 章节来源决策 Drawer -->
    <el-drawer v-model="acceptDrawerVisible" title="接受邀请 — 章节来源决策" direction="rtl" size="55%" @closed="chapterSearchResults=[];chapterSearchKeyword=''">
      <template v-if="currentInvite">
        <el-alert :title="'微专业: ' + (currentInvite.microSpecialtyTitle || '')" type="info" :closable="false" show-icon class="mg-bottom-16" />
        <el-alert :title="'邀请人: ' + (currentInvite.inviterName || '')" type="success" :closable="false" show-icon class="mg-bottom-16" />
        <el-divider content-position="left">已分配章节 ({{ chapterDecisions.length }})</el-divider>

        <el-table :data="chapterDecisions" border size="small">
          <el-table-column label="#" width="50" align="center">
            <template #default="{ $index }">{{ $index + 1 }}</template>
          </el-table-column>
          <el-table-column label="章节名称" prop="chapterTitle" min-width="160">
            <template #default="{ row }">
              <div>
                <strong>{{ row.chapterTitle }}</strong>
                <div v-if="row.source === 'existing' && row.sourceChapterId" class="muted-12">
                  已选: {{ row.sourceChapterTitle || row.sourceChapterId }}
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="来源" width="160">
            <template #default="{ row }">
              <el-radio-group v-model="row.source" size="small" @change="row.sourceChapterId=null;row.sourceChapterTitle=null;row.newChapterTitle=''">
                <el-radio value="existing">已有</el-radio>
                <el-radio value="new">新建</el-radio>
              </el-radio-group>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="220">
            <template #default="{ row }">
              <template v-if="row.source === 'existing'">
                <el-input v-model="chapterSearchKeyword" placeholder="搜索章节..." size="small" class="mg-bottom-8">
                  <template #append><el-button :loading="chapterSearchLoading" size="small" @click="searchPlatformChapters">搜索</el-button></template>
                </el-input>
                <div v-if="chapterSearchResults.length" class="search-results">
                  <div v-for="r in chapterSearchResults" :key="r.chapterId" class="search-result-item" @click="row.sourceChapterId=r.chapterId;row.sourceChapterTitle=r.chapterTitle;chapterSearchResults=[]">
                    <span>{{ r.courseTitle }} / {{ r.chapterTitle }}</span>
                    <el-tag size="small">{{ r.duration }}学时</el-tag>
                  </div>
                </div>
              </template>
              <template v-else-if="row.source === 'new'">
                <el-input v-model="row.newChapterTitle" placeholder="章节标题" size="small" class="mg-bottom-4" />
                <el-input-number v-model="row.newChapterHours" :min="1" :max="200" size="small" placeholder="学时" controls-position="right" />
              </template>
            </template>
          </el-table-column>
        </el-table>
      </template>
      <template #footer>
        <el-button @click="acceptDrawerVisible = false">取消</el-button>
        <el-button type="primary" :loading="accepting" @click="confirmAcceptWithChapters">确认接受并保存决策</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getPendingInvites, acceptInvite, declineInvite, leaveTeam, acceptWithChapters } from '@/api/microSpecialty'
import request from '@/utils/request'

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

// Phase 3: 章节来源决策
const acceptDrawerVisible = ref(false)
const currentInvite = ref(null)  // 当前处理的邀请
const chapterDecisions = ref([])  // [{chapterId, source:'existing'|'new'|null, sourceChapterId, newChapterTitle, newChapterHours}]
const chapterSearchResults = ref([])
const chapterSearchKeyword = ref('')
const chapterSearchLoading = ref(false)
const accepting = ref(false)

// 打开接受向导
function handleAccept(inv) {
  currentInvite.value = inv
  chapterDecisions.value = (inv.assignedChapters || []).map(ch => ({
    chapterId: ch.chapterId,
    chapterTitle: ch.chapterTitle || ch.title,
    source: null,  // null=未选择, 'existing'|'new'
    sourceChapterId: null,
    sourceChapterTitle: null,
    newChapterTitle: '',
    newChapterHours: ch.hours || 8
  }))
  acceptDrawerVisible.value = true
}

// 已有章节搜索
async function searchPlatformChapters() {
  if (!chapterSearchKeyword.value) return
  chapterSearchLoading.value = true
  try {
    const { data } = await request({ method:'GET', url:'/courses/chapters/search', params:{ keyword: chapterSearchKeyword.value, page:0, size:10 }})
    chapterSearchResults.value = data.items || []
  } catch (e) { ElMessage.error('搜索失败') }
  finally { chapterSearchLoading.value = false }
}

// 确认接受
async function confirmAcceptWithChapters() {
  const inv = currentInvite.value
  if (!inv) return
  // 验证: 所有章节必须选择来源
  const hasUnset = chapterDecisions.value.some(d => !d.source)
  if (hasUnset) { ElMessage.warning('所有章节都必须选择来源'); return }
  
  accepting.value = true
  try {
    await acceptWithChapters(inv.id, {
      chapterDecisions: chapterDecisions.value.map(d => ({
        chapterId: d.chapterId,
        source: d.source,
        sourceChapterId: d.sourceChapterId,
        newChapterTitle: d.newChapterTitle
      }))
    })
    ElMessage.success('已接受邀请, 章节决策已保存')
    acceptDrawerVisible.value = false
    fetchData(activeTab.value)
  } catch (e) { ElMessage.error(e?.response?.data?.message || '接受失败') }
  finally { accepting.value = false }
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
.muted-12 { font-size: 12px; color: var(--el-text-color-secondary); }
.search-results { max-height: 200px; overflow-y: auto; border: 1px solid #ebeef5; border-radius: 4px; }
.search-result-item { padding: 6px 10px; cursor: pointer; border-bottom: 1px solid #ebeef5; }
.search-result-item:hover { background: #f5f7fa; }
.mg-bottom-4 { margin-bottom: 4px; }
.mg-bottom-8 { margin-bottom: 8px; }
</style>
