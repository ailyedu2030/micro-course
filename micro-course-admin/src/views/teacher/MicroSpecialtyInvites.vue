<!--
  邀请列表（教师端）
  路由: /teacher/micro-specialties/invites
-->
<template>
  <div class="ms-invites">
    <el-page-header @back="$router.back()" :content="$t('menu.invites')" class="mg-bottom-16" />

    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane :label="$t('microSpecialtyInvites.tabPending')" name="pending" />
      <el-tab-pane :label="$t('microSpecialtyInvites.tabArchived')" name="archived" />
    </el-tabs>

    <div v-loading="loading" class="invite-list">
      <el-result
        v-if="error"
        icon="error"
        :title="$t('microSpecialtyManage.loadFailed')"
        :sub-title="$t('microSpecialtyManage.loadFailedSubtitle')"
      >
        <template #extra>
          <el-button type="primary" @click="fetchData(activeTab)">{{ $t('common.retry') }}</el-button>
        </template>
      </el-result>
      <el-empty v-else-if="!loading && items.length === 0" :description="activeTab === 'pending' ? $t('microSpecialtyList.emptyInvites') : $t('microSpecialtyInvites.emptyArchived')" />

      <div v-for="inv in items" :key="inv.id" class="invite-row">
        <div class="invite-main">
          <div class="invite-header">
            <span class="invite-ms">{{ inv.microSpecialtyTitle }}</span>
            <el-tag size="small">{{ roleMap[inv.role] || inv.role || $t('role.TEACHER') }}</el-tag>
          </div>
          <div class="invite-detail">
            <span>{{ $t('microSpecialtyInvites.inviter', { name: inv.inviterName }) }}</span>
            <span>{{ $t('microSpecialtyInvites.inviteTime', { time: $formatDate(inv.createdAt) || '-' }) }}</span>
          </div>
        </div>
        <div class="invite-right">
          <div class="invite-deadline" :class="{ 'expiring': inv.expiring }">
            {{ inv.deadlineText || $t('microSpecialtyList.deadlineExpired') }}
          </div>
          <div v-if="activeTab === 'pending'" class="invite-actions">
            <el-button size="small" type="primary" @click="handleAccept(inv)">{{ $t('microSpecialtyList.accept') }}</el-button>
            <el-button size="small" @click="handleDecline(inv)">{{ $t('microSpecialtyList.decline') }}</el-button>
          </div>
          <div v-else class="invite-result" style="display:flex;align-items:center;gap:8px;">
            <!-- P1-C 修复 (2026-08-04): 后端字段为 inviteStatus，原用 inv.status → 状态标签/过滤全部失效 -->
            <el-tag v-if="inv.inviteStatus === 'ACTIVE'" type="success" size="small">{{ $t('microSpecialtyInvites.statusAccepted') }}</el-tag>
            <el-tag v-else-if="inv.inviteStatus === 'DECLINED'" type="danger" size="small">{{ $t('microSpecialtyInvites.statusDeclined') }}</el-tag>
            <el-tag v-else-if="inv.inviteStatus === 'REMOVED'" type="warning" size="small">{{ $t('microSpecialtyInvites.statusRemoved') }}</el-tag>
            <el-tag v-else-if="inv.inviteStatus === 'PENDING_ACADEMIC'" type="warning" size="small">{{ $t('microSpecialtyInvites.statusCrossDept') }}</el-tag>
            <el-tag v-else type="info" size="small">{{ $t('microSpecialtyList.deadlineExpired') }}</el-tag>
            <el-button v-if="inv.inviteStatus === 'ACTIVE'" size="small" type="danger" plain @click="handleLeave(inv)">{{ $t('microSpecialtyInvites.leaveTeam') }}</el-button>
          </div>
        </div>
      </div>
    </div>

    <!-- Phase 3: 章节来源决策 Drawer -->
    <el-drawer v-model="acceptDrawerVisible" :title="$t('microSpecialtyInvites.acceptDrawerTitle')" direction="rtl" size="55%" @closed="chapterSearchResults=[];chapterSearchKeyword=''">
      <template v-if="currentInvite">
        <el-alert :title="$t('microSpecialtyInvites.msTitle', { name: currentInvite.microSpecialtyTitle || '' })" type="info" :closable="false" show-icon class="mg-bottom-16" />
        <el-alert :title="$t('microSpecialtyInvites.inviterTitle', { name: currentInvite.inviterName || '' })" type="success" :closable="false" show-icon class="mg-bottom-16" />
        <el-divider content-position="left">{{ $t('microSpecialtyInvites.assignedChapters', { count: chapterDecisions.length }) }}</el-divider>

        <el-table :data="chapterDecisions" border size="small">
          <el-table-column label="#" width="50" align="center">
            <template #default="{ $index }">{{ $index + 1 }}</template>
          </el-table-column>
          <el-table-column :label="$t('course.chapterName')" prop="chapterTitle" min-width="160">
            <template #default="{ row }">
              <div>
                <strong>{{ row.chapterTitle }}</strong>
                <div v-if="row.source === 'existing' && row.sourceChapterId" class="muted-12">
                  {{ $t('microSpecialtyInvites.selectedSource', { name: row.sourceChapterTitle || row.sourceChapterId }) }}
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column :label="$t('microSpecialtyInvites.source')" width="160">
            <template #default="{ row }">
              <el-radio-group v-model="row.source" size="small" @change="row.sourceChapterId=null;row.sourceChapterTitle=null;row.newChapterTitle=''">
                <el-radio value="existing">{{ $t('microSpecialtyInvites.sourceExisting') }}</el-radio>
                <el-radio value="new">{{ $t('microSpecialtyInvites.sourceNew') }}</el-radio>
              </el-radio-group>
            </template>
          </el-table-column>
          <el-table-column :label="$t('app.operation')" width="220">
            <template #default="{ row }">
              <template v-if="row.source === 'existing'">
                <el-input v-model="chapterSearchKeyword" :placeholder="$t('microSpecialtyInvites.searchChapterPlaceholder')" size="small" class="mg-bottom-8">
                  <template #append><el-button :loading="chapterSearchLoading" size="small" @click="searchPlatformChapters">{{ $t('common.search') }}</el-button></template>
                </el-input>
                <div v-if="chapterSearchResults.length" class="search-results">
                  <div
                    v-for="r in chapterSearchResults" :key="r.chapterId" class="search-result-item"
                    role="button" tabindex="0"
                    :aria-label="$t('microSpecialtyInvites.searchResultAria', { course: r.courseTitle, chapter: r.chapterTitle, hours: r.duration })"
                    @click="row.sourceChapterId=r.chapterId;row.sourceChapterTitle=r.chapterTitle;chapterSearchResults=[]"
                    @keydown.enter="row.sourceChapterId=r.chapterId;row.sourceChapterTitle=r.chapterTitle;chapterSearchResults=[]"
                    @keydown.space.prevent="row.sourceChapterId=r.chapterId;row.sourceChapterTitle=r.chapterTitle;chapterSearchResults=[]">
                    <span>{{ r.courseTitle }} / {{ r.chapterTitle }}</span>
                    <el-tag size="small">{{ $t('microSpecialtyInvites.hoursUnit', { count: r.duration }) }}</el-tag>
                  </div>
                </div>
              </template>
              <template v-else-if="row.source === 'new'">
                <el-input v-model="row.newChapterTitle" :placeholder="$t('microSpecialtyInvites.newChapterTitlePlaceholder')" size="small" class="mg-bottom-4" />
                <el-input-number v-model="row.newChapterHours" :min="1" :max="200" size="small" :placeholder="$t('microSpecialtyInvites.hours')" controls-position="right" />
              </template>
            </template>
          </el-table-column>
        </el-table>
      </template>
      <template #footer>
        <el-button @click="acceptDrawerVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="accepting" :disabled="accepting" @click="confirmAcceptWithChapters">{{ $t('microSpecialtyInvites.confirmAccept') }}</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getPendingInvites, acceptInvite, declineInvite, leaveTeam, acceptWithChapters } from '@/api/microSpecialty'
import { searchChapters } from '@/api/chapter'

const { t } = useI18n()
const roleMap = { LEAD: t('microSpecialtyInvites.roleLead'), MEMBER: t('microSpecialtyInvites.roleMember'), ASSISTANT: t('microSpecialtyInvites.roleAssistant') }

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
      return {
        ...i,
        // 审计 2026-08-14 修复: deadline 为空(null/undefined)= 无期限邀请,
        // 必须显示"永久有效", 不得因 dl=null 落到"已过期"分支
        expiring: dl ? (remMs > 0 && remMs < 3 * 86400000) : false,
        deadlineText: dl
          ? (remMs > 0 ? t('microSpecialtyInvites.deadlineRemaining', { days: remDays, hours: remHours }) : t('microSpecialtyList.deadlineExpired'))
          : t('microSpecialtyInvites.deadlinePermanent')
      }
    })
    if (tab === 'pending') list = list.filter(i => i.inviteStatus === 'INVITED' || i.inviteStatus === 'PENDING_ACADEMIC')
    else list = list.filter(i => i.inviteStatus !== 'INVITED' && i.inviteStatus !== 'PENDING_ACADEMIC')
    items.value = list
  } catch (e) { ElMessage.error(e?.response?.data?.message || t('microSpecialtyList.fetchInvitesFailed')); error.value = true }
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
    const { data } = await searchChapters({ keyword: chapterSearchKeyword.value, page: 0, size: 10 })
    chapterSearchResults.value = data.items || []
  } catch (e) { ElMessage.error(t('microSpecialtyInvites.searchFailed')) }
  finally { chapterSearchLoading.value = false }
}

// 确认接受
async function confirmAcceptWithChapters() {
  const inv = currentInvite.value
  if (!inv) return
  // 验证: 所有章节必须选择来源
  const hasUnset = chapterDecisions.value.some(d => !d.source)
  if (hasUnset) { ElMessage.warning(t('microSpecialtyInvites.allChaptersNeedSource')); return }
  
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
    ElMessage.success(t('microSpecialtyInvites.acceptSuccess'))
    acceptDrawerVisible.value = false
    fetchData(activeTab.value)
  } catch (e) { ElMessage.error(e?.response?.data?.message || t('microSpecialtyInvites.acceptFailed')) }
  finally { accepting.value = false }
}

const handleDecline = async (inv) => {
  try { await ElMessageBox.confirm(t('microSpecialtyList.confirmDecline'), t('course.hintTitle'), { type: 'warning' }) }
  catch { return }
  try { await declineInvite(inv.id); ElMessage.success(t('microSpecialtyList.declineSuccess')); fetchData(activeTab.value) }
  catch (e) { ElMessage.error(e?.response?.data?.message || t('course.operationFailed')) }
}

const handleLeave = async (inv) => {
  try { await ElMessageBox.confirm(t('microSpecialtyInvites.confirmLeave'), t('course.hintTitle'), { type: 'warning' }) }
  catch { return }
  try { await leaveTeam(inv.id); ElMessage.success(t('microSpecialtyInvites.leaveSuccess')); fetchData(activeTab.value) }
  catch (e) { ElMessage.error(e?.response?.data?.message || t('course.operationFailed')) }
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
