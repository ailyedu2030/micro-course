<template>
  <div class="preview-page" v-loading="loading">
    <!-- 顶部操作栏 -->
    <div class="toolbar">
      <el-button @click="$router.back()">{{ $t('app.back') }}</el-button>
      <el-button type="primary" :loading="exportingType === 'word'" :disabled="!!exportingType" @click="handleExport('word')">
        {{ exportingType === 'word' ? $t('storagePreview.exportingWord') : $t('storagePreview.downloadWord') }}
      </el-button>
      <el-button type="primary" :loading="exportingType === 'pdf'" :disabled="!!exportingType" @click="handleExport('pdf')">
        {{ exportingType === 'pdf' ? $t('storagePreview.exportingPdf') : $t('storagePreview.downloadPdf') }}
      </el-button>
    </div>

    <!-- 错误状态 -->
    <el-result v-if="error" icon="error" :title="$t('storagePreview.loadFailed')" :sub-title="errorMessage">
      <template #extra><el-button type="primary" @click="loadData">{{ $t('common.retry') }}</el-button></template>
    </el-result>

    <!-- A4 纸样式预览 -->
    <div v-else-if="data" class="a4-paper" id="preview-content">
  <!-- 状态徽章 — 用户一眼看出当前阶段 -->
  <div v-if="data?.status" class="status-banner" :class="`status-${(data.status || '').toLowerCase()}`">
    <el-icon><DocumentCopy v-if="data.status === 'DRAFT'" /><Loading v-else-if="data.status === 'PENDING_REVIEW'" /><CircleCheck v-else-if="data.status === 'APPROVED'" /><CircleClose v-else-if="data.status === 'REJECTED'" /><Refresh v-else /></el-icon>
    <span class="status-text">{{ statusLabel(data.status) }}</span>
    <span v-if="data.status === 'DRAFT'" class="status-tip">{{ $t('storagePreview.statusTipDraft') }}</span>
    <span v-else-if="data.status === 'PENDING_REVIEW'" class="status-tip">{{ $t('storagePreview.statusTipPendingReview') }}</span>
    <span v-else-if="data.status === 'REJECTED' && data.reviewComment" class="status-tip">{{ $t('storagePreview.rejectReason', { reason: data.reviewComment }) }}</span>
    <span v-else-if="data.status === 'WITHDRAWN'" class="status-tip">{{ $t('storagePreview.statusTipWithdrawn') }}</span>
  </div>

  <!-- 标题 -->
  <h1 class="preview-title">{{ $t('storagePreview.pageTitle') }}</h1>

      <!-- 模块1：基本信息 -->
      <table class="preview-table">
        <tr>
          <td class="label-cell">{{ $t('microSpecialtyProposal.applyingUniversity') }}</td>
          <td class="value-cell">{{ data.title || '' }}</td>
          <td class="label-cell">{{ $t('microSpecialtyProposal.microSpecialtyName') }}</td>
          <td class="value-cell">{{ data.microSpecialtyName || '' }}</td>
        </tr>
        <tr>
          <td class="label-cell">{{ $t('microSpecialtyProposal.professionalLeader') }}</td>
          <td class="value-cell">{{ data.leadName || '' }}</td>
          <td class="label-cell">{{ $t('microSpecialtyProposal.contactPhone') }}</td>
          <td class="value-cell">{{ data.contactPhone || '' }}</td>
        </tr>
        <tr>
          <td class="label-cell">{{ $t('microSpecialtyProposal.applyDate') }}</td>
          <td class="value-cell" colspan="3">{{ data.applyDate || '' }}</td>
        </tr>
      </table>

      <!-- 模块2：微专业基本情况 -->
      <h2 class="section-title">{{ $t('storagePreview.section1Title') }}</h2>
      <table class="preview-table">
        <tr>
          <td class="label-cell">{{ $t('app.type') }}</td>
          <td class="value-cell">{{ data.type || $t('microSpecialtyProposal.typeUrgent') }}</td>
          <td class="label-cell">{{ $t('microSpecialtyProposal.targetAudience') }}</td>
          <td class="value-cell">{{ data.targetAudience || '' }}</td>
        </tr>
        <tr>
          <td class="label-cell">{{ $t('storagePreview.targetDisciplines') }}</td>
          <td class="value-cell">{{ data.targetDisciplines || '' }}</td>
          <td class="label-cell">{{ $t('microSpecialtyProposal.totalCredits') }}</td>
          <td class="value-cell">{{ data.totalCredits || '' }}</td>
        </tr>
        <tr>
          <td class="label-cell">{{ $t('storagePreview.courseCount') }}</td>
          <td class="value-cell">{{ data.courseCount || '' }}</td>
          <td class="label-cell">{{ $t('storagePreview.enrollmentQuota') }}</td>
          <td class="value-cell">{{ data.enrollmentQuota || '' }}</td>
        </tr>
        <tr>
          <td class="label-cell">{{ $t('storagePreview.classSize') }}</td>
          <td class="value-cell">{{ data.classSize || '' }}</td>
          <td class="label-cell">{{ $t('microSpecialtyProposal.duration') }}</td>
          <td class="value-cell">{{ data.duration || '' }}</td>
        </tr>
        <tr>
          <td class="label-cell">{{ $t('microSpecialtyProposal.startDate') }}</td>
          <td class="value-cell">{{ data.startDate || '' }}</td>
          <td class="label-cell">{{ $t('storagePreview.isIndustryAcademic') }}</td>
          <td class="value-cell">{{ data.isIndustryAcademic ? $t('app.yes') : $t('app.no') }}</td>
        </tr>
        <tr v-if="data.industryPartners">
          <td class="label-cell">{{ $t('storagePreview.industryPartners') }}</td>
          <td class="value-cell" colspan="3">{{ data.industryPartners }}</td>
        </tr>
        <tr v-if="data.coBuildUniversities">
          <td class="label-cell">{{ $t('microSpecialtyProposal.coBuildUniversities') }}</td>
          <td class="value-cell" colspan="3">{{ data.coBuildUniversities }}</td>
        </tr>
        <tr v-if="data.plannedShareUniversities">
          <td class="label-cell">{{ $t('microSpecialtyProposal.plannedShareUniversities') }}</td>
          <td class="value-cell" colspan="3">{{ data.plannedShareUniversities }}</td>
        </tr>
      </table>

      <!-- 微专业介绍（富文本） -->
      <div v-if="data.introduction" class="rich-section">
        <strong class="block-label">{{ $t('microSpecialtyProposal.introduction') }}：</strong>
        <div v-html="sanitizeHtml(data.introduction)" class="rich-content"></div>
      </div>

      <!-- 市场需求分析 -->
      <div v-if="data.marketDemandAnalysis" class="rich-section">
        <strong class="block-label">{{ $t('storagePreview.marketDemandAnalysis') }}：</strong>
        <div v-html="sanitizeHtml(data.marketDemandAnalysis)" class="rich-content"></div>
      </div>

      <!-- 专业概述 -->
      <div v-if="data.specialtyOverview" class="rich-section">
        <strong class="block-label">{{ $t('storagePreview.specialtyOverview') }}：</strong>
        <div v-html="sanitizeHtml(data.specialtyOverview)" class="rich-content"></div>
      </div>

      <!-- 课程设计 -->
      <div v-if="data.curriculumDesign" class="rich-section">
        <strong class="block-label">{{ $t('storagePreview.curriculumDesign') }}：</strong>
        <div v-html="sanitizeHtml(data.curriculumDesign)" class="rich-content"></div>
      </div>

      <!-- 课程表 -->
      <div v-if="data.courses && data.courses.length" class="table-section">
        <strong class="block-label">{{ $t('storagePreview.courseSetup') }}：</strong>
        <table class="preview-table">
          <thead>
            <tr>
              <th>{{ $t('microSpecialtyProposal.module') }}</th>
              <th>{{ $t('microSpecialtyProposal.courseName') }}</th>
              <th style="width:60px">{{ $t('microSpecialtyProposal.hours') }}</th>
              <th style="width:60px">{{ $t('microSpecialtyProposal.credits') }}</th>
              <th>{{ $t('course.semester') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="c in data.courses" :key="c.id">
              <td>{{ c.moduleName || '' }}</td>
              <td>{{ c.courseName || '' }}</td>
              <td class="text-center">{{ c.hours || '' }}</td>
              <td class="text-center">{{ c.credits || '' }}</td>
              <td>{{ c.semester || '' }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- 建设保障 -->
      <div v-if="data.constructionGuarantee" class="rich-section">
        <strong class="block-label">{{ $t('microSpecialtyProposal.constructionGuarantee') }}：</strong>
        <div v-html="sanitizeHtml(data.constructionGuarantee)" class="rich-content"></div>
      </div>

      <!-- 模块3：教学团队情况 -->
      <h2 class="section-title">{{ $t('storagePreview.section2Title') }}</h2>

      <!-- 负责人信息 -->
      <table class="preview-table">
        <tr>
          <td class="label-cell">{{ $t('microSpecialtyProposal.leader') }}</td>
          <td class="value-cell">{{ data.leadName || '' }}</td>
          <td class="label-cell">{{ $t('microSpecialtyProposal.title') }}</td>
          <td class="value-cell">{{ data.leadTitle || '' }}</td>
        </tr>
        <tr>
          <td class="label-cell">{{ $t('microSpecialtyProposal.position') }}</td>
          <td class="value-cell">{{ data.leadPosition || '' }}</td>
          <td class="label-cell">{{ $t('microSpecialtyProposal.researchDirection') }}</td>
          <td class="value-cell">{{ data.leadResearchDirection || '' }}</td>
        </tr>
      </table>

      <!-- 负责人授课课程 -->
      <div v-if="data.leadCourses && data.leadCourses.length" class="table-section">
        <strong class="block-label">{{ $t('storagePreview.leadCoursesLabel') }}</strong>
        <table class="preview-table">
          <thead>
            <tr>
              <th>{{ $t('microSpecialtyProposal.courseName') }}</th>
              <th style="width:80px">{{ $t('microSpecialtyProposal.credits') }}</th>
              <th style="width:80px">{{ $t('microSpecialtyProposal.hours') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="c in data.leadCourses" :key="c.id">
              <td>{{ c.courseName || '' }}</td>
              <td class="text-center">{{ c.credits || '' }}</td>
              <td class="text-center">{{ c.hours || '' }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- 团队成员 -->
      <div v-if="data.teamMembers && data.teamMembers.length" class="table-section">
        <strong class="block-label">{{ $t('microSpecialtyProposal.teamMembers') }}：</strong>
        <table class="preview-table">
          <thead>
            <tr>
              <th style="width:50px">{{ $t('course.index') }}</th>
              <th>{{ $t('microSpecialtyProposal.name') }}</th>
              <th style="width:60px">{{ $t('microSpecialtyProposal.age') }}</th>
              <th>{{ $t('microSpecialtyProposal.title') }}</th>
              <th>{{ $t('microSpecialtyProposal.organization') }}</th>
              <th>{{ $t('microSpecialtyProposal.profession') }}</th>
              <th>{{ $t('microSpecialtyProposal.taughtCourses') }}</th>
              <th>{{ $t('microSpecialtyProposal.plannedCourses') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="m in data.teamMembers" :key="m.id">
              <td class="text-center">{{ m.seq || '' }}</td>
              <td>{{ m.name || '' }}</td>
              <td class="text-center">{{ m.age || '' }}</td>
              <td>{{ m.title || '' }}</td>
              <td>{{ m.organization || '' }}</td>
              <td>{{ m.profession || '' }}</td>
              <td>{{ m.taughtCourses || '' }}</td>
              <td>{{ m.plannedCourses || '' }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- 模块4：签字/盖章 -->
      <h2 class="section-title">{{ $t('storagePreview.section3Title') }}</h2>
      <div v-if="data.signatures && data.signatures.length" class="signatures-section">
        <table class="preview-table">
          <thead>
            <tr>
              <th style="width:50px">{{ $t('course.index') }}</th>
              <th style="width:100px">{{ $t('storagePreview.approvalLevel') }}</th>
              <th>{{ $t('storagePreview.approvalOpinion') }}</th>
              <th style="width:90px">{{ $t('microSpecialtyProposal.signature') }}</th>
              <th style="width:90px">{{ $t('microSpecialtyProposal.seal') }}</th>
              <th style="width:90px">{{ $t('course.date') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="s in data.signatures" :key="s.id">
              <td class="text-center">{{ s.unitSeq || '' }}</td>
              <td>{{ signLevelLabel(s.signLevel) }}</td>
              <td>
                <template v-if="s.opinionText">{{ s.opinionText }}</template>
                <template v-if="s.remark"><br />{{ $t('storagePreview.remarkLabel') }}{{ s.remark }}</template>
              </td>
              <td class="text-center">
                <img v-if="s.signatureImageUrl" :src="s.signatureImageUrl" class="signature-img" :alt="$t('microSpecialtyProposal.signature')" />
                <span v-else-if="s.signatureText">{{ s.signatureText }}</span>
                <span v-else>-</span>
              </td>
              <td class="text-center">
                <img v-if="s.sealImageUrl" :src="s.sealImageUrl" class="seal-img" :alt="$t('microSpecialtyProposal.seal')" />
                <span v-else>-</span>
              </td>
              <td class="text-center">{{ s.signDate || '' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else class="empty-section">
        <p class="empty-text">{{ $t('storagePreview.noApprovalRecords') }}</p>
      </div>

      <!-- 模块5：共享单位 -->
      <h2 class="section-title">{{ $t('storagePreview.section4Title') }}</h2>
      <div v-if="data.sharedUnits && data.sharedUnits.length" class="table-section">
        <table class="preview-table">
          <thead>
            <tr>
              <th style="width:50px">{{ $t('course.index') }}</th>
              <th>{{ $t('microSpecialtyProposal.unitName') }}</th>
              <th style="width:120px">{{ $t('microSpecialtyProposal.unitType') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="u in data.sharedUnits" :key="u.id">
              <td class="text-center">{{ u.sortOrder || '' }}</td>
              <td>{{ u.unitName || '' }}</td>
              <td>{{ unitTypeLabel(u.unitType) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else class="empty-section">
        <p class="empty-text">{{ $t('storagePreview.noSharedUnits') }}</p>
      </div>

      <!-- 页脚 -->
      <div class="preview-footer">
        {{ $t('storagePreview.footerIssuer') }} &nbsp;&nbsp; {{ $t('storagePreview.footerFileDate') }}
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { DocumentCopy, Loading, CircleCheck, CircleClose, Refresh } from '@element-plus/icons-vue'
import { getStoragePreview, exportStorageWord, exportStoragePdf } from '@/api/storageApplication'

import DOMPurify from 'dompurify'

const { t } = useI18n()

const sanitizeHtml = (html) => {
  if (!html) return ''
  return DOMPurify.sanitize(html, { ALLOWED_TAGS: ['p', 'br', 'strong', 'b', 'em', 'i', 'u', 'ul', 'ol', 'li', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'span', 'div', 'a', 'blockquote', 'pre', 'code'], ALLOWED_ATTR: ['href', 'target', 'rel'] })
}

const route = useRoute()
const loading = ref(true)
const exportingType = ref(null)  // P1-UX: 跟踪当前导出格式（word/pdf）用于按钮 loading 态
const error = ref(false)
const errorMessage = ref('')  // P2-F: 具体错误信息
const data = ref(null)

const loadData = async () => {
  loading.value = true
  error.value = false
  try {
    const res = await getStoragePreview(route.params.id)
    data.value = res.data
  } catch (e) {
    error.value = true
    errorMessage.value = e?.response?.data?.message || e?.message || t('storagePreview.loadFailedMsg')  // P2-F: 显示具体错误
  } finally {
    loading.value = false
  }
}

const handleExport = async (type) => {
  const fn = type === 'word' ? exportStorageWord : exportStoragePdf
  exportingType.value = type  // P1-UX: 立即设置 loading 态，避免双击
  try {
    const res = await fn(route.params.id)
    // B4 fix: check if response is actually a JSON error disguised as blob
    if (res.data && res.data.type === 'application/json') {
      const text = await new Response(res.data).text()
      const err = JSON.parse(text)
      // P1-UX: 校验失败显示具体错误清单，而非"导出失败"模糊提示
      if (err.errors && Array.isArray(err.errors) && err.errors.length) {
        ElMessageBox.alert(
          err.errors.map(e => `• ${e}`).join('\n'),
          err.message || t('storagePreview.completeRequiredBeforeExport'),
          { type: 'warning', confirmButtonText: t('storagePreview.gotIt') }
        )
      } else {
        ElMessage.error(err.message || t('storagePreview.exportValidateFailed'))
      }
      return
    }
    const blob = res.data instanceof Blob ? res.data : new Blob([res.data])
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    const ext = type === 'word' ? 'docx' : 'pdf'
    // P1-UX: 文件名更专业 - 含微专业名+日期
    const title = data.value?.title || data.value?.microSpecialtyName || t('course.microSpecialty')
    const date = new Date().toISOString().slice(0, 10)
    a.download = t('storagePreview.exportFileName', { title, date, ext })
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success(t('storagePreview.exportSuccess', { format: ext.toUpperCase() }))
  } catch (e) {
    const msg = e?.response?.data?.message || e?.message || t('storagePreview.exportFailed')  // P2-F: 显示具体错误
    ElMessage.error(msg)
  } finally {
    // P1-UX: 恢复按钮可点状态
    exportingType.value = null
  }
}

const memberTypeLabel = (type) => {
  const map = { INTERNAL: t('storagePreview.memberTypeInternal'), EXTERNAL: t('storagePreview.memberTypeExternal'), LEAD: t('microSpecialtyProposal.leader') }
  return map[type] || type || ''
}

const signLevelLabel = (level) => {
  const map = {
    DEPARTMENT: t('storagePreview.signLevelDepartment'),
    COLLEGE: t('storagePreview.signLevelCollege'),
    ACADEMIC: t('storagePreview.signLevelAcademic'),
    UNIVERSITY: t('storagePreview.signLevelUniversity'),
    SHARED_UNIT: t('storagePreview.signLevelSharedUnit')
  }
  return map[level] || level || ''
}

// P1-UX: 状态徽章文本映射
const statusLabel = (s) => {
  const map = { DRAFT: t('course.draft'), PENDING_REVIEW: t('storagePreview.statusPendingReview'), APPROVED: t('course.approved'), REJECTED: t('storagePreview.statusRejected'), WITHDRAWN: t('storagePreview.statusWithdrawn') }
  return map[s] || s || ''
}

const unitTypeLabel = (type) => {
  const map = { UNIVERSITY: t('storagePreview.unitTypeUniversity'), ENTERPRISE: t('storagePreview.unitTypeEnterprise'), RESEARCH: t('storagePreview.unitTypeResearch'), SHARE_UNIV: t('microSpecialtyProposal.plannedShareUniversities'), CO_BUILD_UNIV: t('microSpecialtyProposal.coBuildUniversities'), OTHER: t('storagePreview.unitTypeOther') }
  return map[type] || type || ''
}

onMounted(loadData)
</script>

<style scoped>
.preview-page {
  max-width: 230mm;
  margin: 0 auto;
  padding: 16px;
}

.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
  position: sticky;
  top: 0;
  z-index: 100;
  background: #f5f7fa;
  padding: 12px 16px;
  border-radius: 4px;
}

.a4-paper {
  width: 210mm;
  min-height: 297mm;
  padding: 20mm 18mm 25mm 18mm;
  background: #fff;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.12);
  margin: 0 auto;
  box-sizing: border-box;
}

.preview-title {
  text-align: center;
  font-size: 18px;
  font-weight: bold;
  margin: 0 0 20px 0;
  line-height: 1.4;
}

.section-title {
  font-size: 15px;
  font-weight: bold;
  margin: 20px 0 10px 0;
  line-height: 1.4;
}

.preview-table {
  width: 100%;
  border-collapse: collapse;
  margin-bottom: 8px;
  table-layout: fixed;
}

.preview-table td,
.preview-table th {
  border: 1px solid #000;
  padding: 5px 8px;
  font-size: 12px;
  line-height: 1.5;
  vertical-align: middle;
  word-break: break-word;
}

.preview-table th {
  background: #f0f0f0;
  font-weight: bold;
  text-align: center;
}

.label-cell {
  width: 15%;
  background: #f5f5f5;
  font-weight: bold;
  text-align: center;
}

.value-cell {
  width: 35%;
}

.text-center {
  text-align: center;
}

.rich-section {
  margin: 10px 0;
  font-size: 12px;
  line-height: 1.7;
}

.block-label {
  display: block;
  margin-bottom: 4px;
  font-size: 12px;
  font-weight: bold;
}

.rich-content {
  margin: 4px 0;
  font-size: 12px;
  line-height: 1.7;
}

.rich-content :deep(p) {
  margin: 4px 0;
}

.rich-content :deep(img) {
  max-width: 100%;
}

.table-section {
  margin: 10px 0;
}

.signatures-section {
  margin: 8px 0;
}

.signature-img,
.seal-img {
  max-width: 80px;
  max-height: 50px;
  object-fit: contain;
}

.empty-section {
  padding: 12px 0;
}

.empty-text {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  text-align: center;
}

.preview-footer {
  text-align: center;
  font-size: 9px;
  margin-top: 30px;
  color: #666;
  line-height: 1.5;
}

/* 打印样式 */
@media print {
  .toolbar {
    display: none !important;
  }
  .preview-page {
    padding: 0;
    max-width: none;
  }
  .a4-paper {
    box-shadow: none;
    width: 100%;
    padding: 15mm 12mm;
  }
}

/* P1-UX: 状态徽章 — 用户预览时一眼看出当前阶段 */
.status-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  border-radius: 4px;
  margin-bottom: 12px;
  font-size: 14px;
  border: 1px solid;
}
.status-banner .el-icon {
  font-size: 18px;
  flex-shrink: 0;
}
.status-banner .status-text {
  font-weight: 600;
  font-size: 15px;
}
.status-banner .status-tip {
  margin-left: 8px;
  color: #606266;
  font-size: 13px;
}
.status-banner.status-draft {
  background: #f4f4f5;
  border-color: #d3d4d6;
  color: #606266;
}
.status-banner.status-pending_review {
  background: #fdf6ec;
  border-color: #faecd8;
  color: #e6a23c;
}
.status-banner.status-approved {
  background: #f0f9eb;
  border-color: #d9ead3;
  color: #67c23a;
}
.status-banner.status-rejected {
  background: #fef0f0;
  border-color: #fde2e2;
  color: #f56c6c;
}
.status-banner.status-withdrawn {
  background: #ecf5ff;
  border-color: #d9ecff;
  color: #909399;
}
</style>
