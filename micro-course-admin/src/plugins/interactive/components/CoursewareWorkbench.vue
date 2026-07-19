<!--
  CoursewareWorkbench.vue · 课件四面板工作台 (新版本)

  这是 spec 5.1 设计的 SlideManage.vue 重构版本, 客户体验至上:
  - 顶部 PPT/HTML 双类型选择 (进入不同工作流)
  - 内容/脚本/音频 三面板切换
  - 额外: 页间跳转 (PPT) / 预览与发布

  与旧 SlideManage.vue 并存, 通过 feature flag 控制:
  - mc:feature:courseware_v2=true 使用新版
  - 默认 false (老用户不打扰)

  Props:
    courseId, chapterId, sectionId
-->
<template>
  <div class="courseware-workbench">
    <!-- Step 1: 课件类型选择 -->
    <div class="cw-step cw-type-select">
      <el-radio-group v-model="coursewareType" size="large">
        <el-radio-button label="PPT">
          <el-icon><Picture /></el-icon>
          PPT 课件
        </el-radio-button>
        <el-radio-button label="HTML">
          <el-icon><Document /></el-icon>
          HTML 课件
        </el-radio-button>
      </el-radio-group>
      <el-tag v-if="tree" :type="statusTagType(tree.narrationStatus)" size="large" effect="plain" class="cw-status-tag">
        {{ statusLabel(tree.narrationStatus) }} · {{ tree.audioReadyCount }} 音频就绪
      </el-tag>
    </div>

    <!-- PPT 工作流 -->
    <div v-if="coursewareType === 'PPT' && tree?.type === 'PPT'" class="cw-ppt">
      <!-- 页选择 -->
      <div class="cw-page-list">
        <h4 class="cw-section-title">页面列表 ({{ tree.pages.length }})</h4>
        <el-radio-group v-model="activePageIdx" class="cw-page-radios">
          <el-radio-button
            v-for="page in tree.pages"
            :key="page.pageId"
            :value="page.pageId"
            class="cw-page-radio"
          >
            <div class="cw-page-radio-content">
              <span>第 {{ page.pageNumber }} 页</span>
              <el-tag :type="statusTagType(page.narrationStatus)" size="small">
                {{ statusLabel(page.narrationStatus) }}
              </el-tag>
            </div>
          </el-radio-button>
        </el-radio-group>
      </div>

      <!-- 当前页面板 -->
      <div v-if="activePage" class="cw-panels">
        <el-tabs v-model="activePanel" type="card" class="cw-tabs">
          <!-- Panel 1: 内容 -->
          <el-tab-pane name="content" label="内容">
            <PptPageEditor :course-id="courseId" :page-id="activePage.pageId" />
          </el-tab-pane>

          <!-- Panel 2: 脚本 -->
          <el-tab-pane name="script" label="讲述稿">
            <ScriptEditor
              :course-id="courseId"
              page-type="PPT"
              :page-id="activePage.pageId"
              :current-script-id="activePage.activeScript?.id || null"
            />
          </el-tab-pane>

          <!-- Panel 3: 音频 -->
          <el-tab-pane name="audio" label="音频">
            <AudioManager
              :course-id="courseId"
              page-type="PPT"
              :owner-id="activePage.pageId"
              :script-id="activePage.activeScript?.id || null"
            />
          </el-tab-pane>

          <!-- Panel 4: 页间跳转 -->
          <el-tab-pane name="flow" label="跳转逻辑">
            <PptFlowEditor :course-id="courseId" :section-id="sectionId" :pages="tree.pages.map(p => ({ id: p.pageId, pageNumber: p.pageNumber, pageTitle: p.pageTitle }))" />
          </el-tab-pane>
        </el-tabs>
      </div>
    </div>

    <!-- HTML 工作流 -->
    <div v-if="coursewareType === 'HTML' && tree?.type === 'HTML'" class="cw-html">
      <div class="cw-panels">
        <el-tabs v-model="activePanel" type="card" class="cw-tabs">
          <el-tab-pane name="content" label="HTML 内容">
            <HtmlBlockEditor :course-id="courseId" :section-id="sectionId" />
          </el-tab-pane>
          <el-tab-pane name="segment" label="分段脚本">
            <div v-for="(seg, idx) in tree.htmlUnit.detectedSegments ? Array.from({ length: tree.htmlUnit.detectedSegments }, (_, i) => ({ idx: i + 1 })) : []" :key="idx" class="cw-segment-block">
              <h5 class="cw-segment-title">第 {{ seg.idx }} 段</h5>
              <ScriptEditor
                :course-id="courseId"
                page-type="HTML"
                :unit-id="tree.htmlUnit.id"
                :segment-index="seg.idx"
                :current-script-id="null"
              />
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </div>

    <!-- 空状态 -->
    <div v-else-if="tree?.type === 'EMPTY'" class="cw-empty">
      <el-empty :description="`该 section 暂无${coursewareType === 'PPT' ? 'PPT' : 'HTML'}课件`">
        <el-button type="primary" :icon="UploadFilled" @click="handleUpload">
          上传 {{ coursewareType }} 课件
        </el-button>
      </el-empty>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Picture, Document, UploadFilled } from '@element-plus/icons-vue'
import { getCoursewareTree } from '../api/queryCourseware'
import PptPageEditor from './PptPageEditor.vue'
import HtmlBlockEditor from './HtmlBlockEditor.vue'
import ScriptEditor from './ScriptEditor.vue'
import AudioManager from './AudioManager.vue'
import PptFlowEditor from './PptFlowEditor.vue'

const props = defineProps({
  courseId: { type: Number, required: true },
  chapterId: { type: Number, default: null },
  sectionId: { type: Number, required: true }
})

const coursewareType = ref('PPT')
const tree = ref(null)
const activePageIdx = ref(null)
const activePanel = ref('content')

const activePage = computed(() => {
  if (!tree.value?.pages) return null
  return tree.value.pages.find(p => p.pageId === activePageIdx.value) || tree.value.pages[0]
})

async function loadTree() {
  try {
    const res = await getCoursewareTree(props.courseId, props.sectionId)
    tree.value = res.data || res
    // 自动选择第一个页
    if (tree.value?.pages?.length > 0) {
      activePageIdx.value = tree.value.pages[0].pageId
    }
  } catch (e) {
    ElMessage.error('加载课件失败: ' + (e.message || '未知错误'))
  }
}

function statusLabel(s) {
  return { PENDING: '待生成', AUDIO_GENERATING: '生成中', AUDIO_READY: '就绪' }[s] || s
}

function statusTagType(s) {
  return { PENDING: 'info', AUDIO_GENERATING: 'warning', AUDIO_READY: 'success' }[s] || 'info'
}

function handleUpload() {
  ElMessage.info('请使用底部上传按钮上传新课件 (兼容旧 UI)')
  // 实际跳转到上传面板 (此处留给用户操作)
}

watch(() => [props.courseId, props.sectionId], loadTree, { immediate: true })
watch(() => tree.value?.type, (t) => {
  if (t === 'HTML') coursewareType.value = 'HTML'
  else if (t === 'PPT') coursewareType.value = 'PPT'
})

onMounted(loadTree)
</script>

<style scoped>
.courseware-workbench { padding: 20px; max-width: 1400px; margin: 0 auto; }
.cw-step { margin-bottom: 20px; display: flex; justify-content: space-between; align-items: center; }
.cw-status-tag { margin-left: 12px; }
.cw-page-list { background: var(--el-fill-color-light); padding: 16px; border-radius: 8px; margin-bottom: 16px; }
.cw-section-title { margin: 0 0 12px; font-size: 14px; color: var(--el-text-color-secondary); }
.cw-page-radios { display: flex; flex-wrap: wrap; gap: 8px; }
.cw-page-radio { margin-right: 0 !important; margin-bottom: 4px; }
.cw-page-radio-content { display: flex; align-items: center; gap: 6px; }
.cw-panels { background: var(--el-fill-color-blank); border-radius: 8px; padding: 0; }
.cw-tabs { background: transparent; }
.cw-segment-block { margin-bottom: 16px; padding: 12px; background: var(--el-fill-color-light); border-radius: 6px; }
.cw-segment-title { margin: 0 0 8px; font-size: 14px; font-weight: 600; }
.cw-empty { padding: 60px 0; }
</style>