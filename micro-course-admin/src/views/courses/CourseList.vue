<!--
  课程列表
  路由路径: /courses
  Phase 1
  Author: jackie
-->
<template>
  <div class="course-list-page">
    <!-- 面包屑导航 -->
    <el-breadcrumb separator="→" class="page-breadcrumb">
      <el-breadcrumb-item>{{ $t('teacher.courseManagement') }}</el-breadcrumb-item>
      <el-breadcrumb-item>{{ $t('course.courseList') }}</el-breadcrumb-item>
    </el-breadcrumb>

    <!-- 筛选区 -->
    <el-card class="filter-card" shadow="never">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item :label="$t('course.keyword')">
          <el-input v-model="searchForm.keyword" :placeholder="$t('course.courseName')" clearable @clear="handleSearch" @keyup.enter="handleSearch" class="filter-input-w160" :aria-label="$t('course.keyword')" />
        </el-form-item>
        <el-form-item :label="$t('course.category')">
          <el-select v-model="searchForm.categoryId" :placeholder="$t('course.pleaseSelectCategory')" clearable class="filter-input-w160" :aria-label="$t('course.category')">
            <el-option v-for="cat in categories" :key="cat.id" :label="cat.name" :value="cat.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('course.teacher')">
          <el-input v-model="searchForm.teacherName" :placeholder="$t('course.teacherName')" clearable class="filter-input-w120" :aria-label="$t('course.teacher')" @clear="handleSearch" />
        </el-form-item>
        <el-form-item :label="$t('course.status')">
          <el-select v-model="searchForm.status" :placeholder="$t('userSearch.pleaseSelect')" clearable class="filter-input-w120" :aria-label="$t('course.status')">
            <el-option :label="$t('course.draft')" :value="0" />
            <el-option :label="$t('course.pendingReview')" :value="1" />
            <el-option :label="$t('course.statusApproved')" :value="2" />
            <el-option :label="$t('course.reject')" :value="3" />
            <el-option :label="$t('course.published')" :value="4" />
            <el-option :label="$t('course.unpublish')" :value="5" />
            <el-option :label="$t('course.archived')" :value="6" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('course.courseType')">
          <el-select v-model="searchForm.courseType" :placeholder="$t('course.allTypes')" clearable class="filter-input-w140" :disabled="!!fixedCourseType" @change="handleSearch" :aria-label="$t('course.courseType')">
            <el-option v-for="opt in courseTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">{{ $t('common.search') }}</el-button>
          <el-button @click="handleReset">{{ $t('common.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 数据表 -->
    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ pageTitle }}</span>
          <div class="header-actions">
            <el-button
              type="warning"
              size="default"
              :disabled="tableData.length === 0"
              @click="handleExport"
              :aria-label="$t('course.exportData')"
            >
              <el-icon><Download /></el-icon>{{ $t('course.export') }}
            </el-button>
            <el-button type="primary" v-if="userRole === 'TEACHER' || userRole === 'ADMIN'" @click="handleCreate">{{ $t('course.createCourse') }}</el-button>
            <el-button type="primary" v-if="route.query.courseType === 'OFFLINE'" @click="showOfflineDialog = true" :icon="Plus">{{ $t('course.addArrange') }}</el-button>
            <el-button v-if="route.query.courseType" @click="handleBackToFullList">{{ $t('course.backToList') }}</el-button>
          </div>
        </div>
      </template>
      <el-skeleton v-if="loading" :rows="5" animated />
      <el-empty v-else-if="tableData.length === 0" :description="$t('course.noMatch')" :image-size="120">
        <template #default>
          <el-button type="primary" @click="handleReset">{{ $t('course.clearFilter') }}</el-button>
        </template>
      </el-empty>
      <el-table v-else :data="tableData" stripe border class="data-table" ref="tableRef" @row-click="handleRowClick" @row-keydown.enter="handleRowClick">
        <el-table-column type="index" :label="$t('course.index')" width="70" align="center" />
        <el-table-column :label="$t('course.cover')" width="80" align="center">
          <template #default="{ row }">
            <el-image
              v-if="row.coverUrl"
              :src="row.coverUrl"
              :alt="$t('course.coverAlt', { title: row.title || $t('course.title') })"
              fit="cover"
              class="table-thumb"
              :preview-src-list="[row.coverUrl]"
              lazy
            />
            <span v-else class="no-thumb">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="title" :label="$t('course.tableTitle')" min-width="180" show-overflow-tooltip />
        <el-table-column :label="$t('app.type')" width="110" align="center">
          <template #default="{ row }">
            <el-tag
              v-if="getCourseTypeConfig(row.courseType)"
              :type="getCourseTypeConfig(row.courseType).tagType"
              size="small"
              effect="plain"
            >
{{ courseTypeLabel(row.courseType) }}
</el-tag>
            <el-tag v-else type="primary" size="small" effect="plain">{{ row.courseType || $t('course.typeVideo') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="categoryName" :label="$t('course.category')" width="120" />
        <el-table-column prop="teacherName" :label="$t('course.teacher')" width="100" />
        <el-table-column prop="studentCount" :label="$t('course.studentCount')" width="90" align="center" />
        <el-table-column prop="status" :label="$t('course.status')" width="120" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.status === 0" type="info" size="small">{{ $t('course.draft') }}</el-tag>
            <el-tag v-else-if="row.status === 1" type="warning" size="small">{{ $t('course.pendingReview') }}</el-tag>
            <el-tag v-else-if="row.status === 2" type="success" size="small">{{ $t('course.approved') }}</el-tag>
            <el-tag v-else-if="row.status === 3" type="danger" size="small">{{ $t('course.reject') }}</el-tag>
            <el-tag v-else-if="row.status === 4" type="success" size="small">{{ $t('course.published') }}</el-tag>
            <el-tag v-else-if="row.status === 5" type="warning" size="small">{{ $t('course.unpublish') }}</el-tag>
            <el-tag v-else type="info" size="small">{{ $t('course.archived') }}</el-tag>
            <div v-if="row.status === 1" class="review-hint">{{ $t('course.reviewingHint') }}</div>
          </template>
        </el-table-column>
        <el-table-column :label="$t('app.operation')" width="280" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click.stop="handleEdit(row)">{{ $t('app.edit') }}</el-button>
            <el-button v-if="isCoursewareCourseType(row.courseType)" type="success" link size="small" @click.stop="goSlides(row)">{{ $t('course.courseware') }}</el-button>
            <el-button v-if="row.courseType === 'OFFLINE'" type="info" link size="small" @click.stop="handleManageOffline(row)">{{ $t('course.arrange') }}</el-button>
            <el-button v-if="row.status === 1 && (userRole === 'ADMIN' || userRole === 'ACADEMIC')" type="success" link size="small" :loading="actingId === row.id" @click.stop="handleApprove(row)">{{ $t('course.approve') }}</el-button>
            <el-button v-if="row.status === 1 && (userRole === 'ADMIN' || userRole === 'ACADEMIC')" type="danger" link size="small" :loading="actingId === row.id" @click.stop="handleReject(row)">{{ $t('course.reject') }}</el-button>
            <el-button v-if="[2, 5].includes(row.status) && userRole === 'ADMIN'" type="primary" link size="small" :loading="actingId === row.id" @click.stop="handlePublish(row)">{{ row.status === 5 ? $t('course.republish') : $t('course.publish') }}</el-button>
            <el-button v-if="row.status === 5 && userRole === 'ADMIN'" type="info" link size="small" :loading="actingId === row.id" @click.stop="handleArchive(row)">{{ $t('course.archive') }}</el-button>
            <el-button v-if="row.status === 4 && userRole === 'ADMIN'" type="warning" link size="small" :loading="actingId === row.id" @click.stop="handleUnpublish(row)">{{ $t('course.unpublish') }}</el-button>
            <el-button type="info" link size="small" @click.stop="handleView(row)">{{ $t('course.view') }}</el-button>
            <el-button type="primary" link size="small" @click.stop="handleCopy(row)">{{ $t('course.copy') }}</el-button>
            <el-button type="danger" link size="small" :loading="actingId === row.id" @click.stop="handleDelete(row)">{{ $t('app.delete') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="!loading && tableData.length > 0" class="pagination-wrap">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="totalElements"
          :page-sizes="[10, 20, 50, 100]"
          layout="total,prev,pager,next"
          @size-change="handleSizeChange"
          @current-change="handlePageChange" :aria-label="$t('course.paginationAria')"
/>
        <div class="page-size-wrap">
          <span class="el-pagination__total">{{ $t('course.rows', { count: totalElements }) }}</span>
          <label for="page-size-select" class="sr-only">{{ $t('course.perPage') }}</label>
          <el-select id="page-size-select" :model-value="size" class="page-size-select" @change="onPageSizeChange" :aria-label="$t('course.perPage')">
            <el-option v-for="s in [10, 20, 50, 100]" :key="s" :label="$t('course.perPageOption', { count: s })" :value="s" />
          </el-select>
        </div>
      </div>
    </el-card>

    <!-- 弹窗表单 -->
    <el-dialog v-model="dialogVisible" :title="$t('course.createCourse')" width="700px" @close="handleDialogClose" :close-on-press-escape="true">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="90px">
        <el-form-item :label="$t('course.courseTitle')" prop="title">
          <el-input v-model="formData.title" :placeholder="$t('course.inputCourseTitle')" :aria-label="$t('course.courseTitle')" />
        </el-form-item>
        <el-form-item :label="$t('course.courseType')" prop="courseType" v-if="!fixedCourseType">
          <el-select v-model="formData.courseType" class="full-width" :aria-label="$t('course.courseType')">
            <el-option v-for="opt in courseTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item :label="$t('course.category')" prop="categoryId">
              <el-select v-model="formData.categoryId" :placeholder="$t('userSearch.pleaseSelect')" class="full-width" :aria-label="$t('course.category')">
                <el-option v-for="cat in categories" :key="cat.id" :label="cat.name" :value="cat.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$t('course.teachingTeacher')" prop="teacherId">
              <el-select v-model="formData.teacherId" :placeholder="$t('course.searchTeacherPlaceholder')" class="full-width" filterable remote :remote-method="remoteSearchTeachers" :loading="teacherLoading" :disabled="userStore.role === 'TEACHER'" :aria-label="$t('course.teachingTeacher')">
                <el-option v-for="t in teacherOptions" :key="t.id" :label="t.realName || t.username" :value="t.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item :label="$t('course.courseDescription')" prop="description">
          <div class="quill-editor-wrapper" role="textbox" :aria-label="$t('course.courseDescription')" aria-multiline="true">
            <QuillEditor v-model:content="formData.description" content-type="html" toolbar="essential" :placeholder="$t('course.descriptionPlaceholder')" :style="{ minHeight: '150px' }" />
          </div>
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item :label="$t('course.credit')">
              <el-input-number v-model="formData.creditHours" :min="0" :max="20" class="full-width" :aria-label="$t('course.credit')" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$t('course.semester')">
              <el-input v-model="formData.semester" :placeholder="$t('course.semesterPlaceholder')" :aria-label="$t('course.semester')" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item :label="$t('course.difficulty')">
              <el-select v-model="formData.difficulty" :placeholder="$t('userSearch.pleaseSelect')" class="full-width" clearable :aria-label="$t('course.difficulty')">
                <el-option :label="$t('course.beginner')" :value="1" />
                <el-option :label="$t('course.intermediate')" :value="2" />
                <el-option :label="$t('course.advanced')" :value="3" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$t('course.priceLabel')">
              <el-input-number v-model="formData.price" :min="0" :precision="2" :placeholder="$t('course.pricePlaceholder')" class="full-width" :aria-label="$t('course.priceLabelShort')" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-divider content-position="left">{{ $t('course.pricingRule') }}</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item :label="$t('course.freeAccess')">
              <el-select v-model="formData.freeAccessScope" class="full-width" :aria-label="$t('course.freeAccess')">
                <el-option :label="$t('course.none')" value="none" />
                <el-option :label="$t('course.sameDepartment')" value="same_department" />
                <el-option :label="$t('course.sameCollege')" value="same_college" />
                <el-option :label="$t('course.sameSchool')" value="same_school" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$t('course.discountScope')">
              <el-select v-model="formData.discountScope" class="full-width" :aria-label="$t('course.discountScope')">
                <el-option :label="$t('course.none')" value="none" />
                <el-option :label="$t('course.sameCollege')" value="same_college" />
                <el-option :label="$t('course.sameSchool')" value="same_school" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$t('course.discountPercent')">
              <el-input-number v-model="formData.discountPercent" :min="0" :max="100" :step="5" class="full-width" :aria-label="$t('course.discountPercent')" />%
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item :label="$t('course.courseCover')">
              <template v-if="!coverPreviewUrl">
                <el-upload ref="coverUploadRef" :auto-upload="false" :limit="1" accept="image/jpeg,image/png" :before-upload="handleBeforeCoverUpload" :on-change="handleCoverChange" :aria-label="$t('course.uploadCover')">
                  <el-button size="small" type="primary"><el-icon><Plus /></el-icon>{{ $t('course.selectImage') }}</el-button>
                </el-upload>
                <div class="form-tip">{{ $t('course.coverTip') }}</div>
              </template>
              <div v-else class="cover-preview-wrap">
                <img :src="coverPreviewUrl" class="cover-preview-img" :alt="$t('course.coverPreviewAlt')" />
                <el-button size="small" @click="handleRemoveCover">{{ $t('app.delete') }}</el-button>
              </div>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="submitLoading" :disabled="submitLoading" @click="handleSubmit">{{ $t('course.dialogConfirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 线下课新增安排弹窗 -->
    <el-dialog v-model="showOfflineDialog" :title="$t('course.addOfflineSchedule')" width="500px" @close="resetOfflineForm">
      <el-form ref="offlineFormRef" :model="offlineForm" :rules="offlineRules" label-width="100px">
        <el-form-item :label="$t('course.title')">
          <el-select v-model="offlineForm.courseId" :placeholder="$t('course.selectCourse')" class="full-width" filterable @change="onOfflineCourseChange" :aria-label="$t('course.typeOfflineCourse')">
            <el-option v-for="c in courseOptions" :key="c.id" :label="c.title" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('course.chapter')" prop="chapterId">
          <el-select v-model="offlineForm.chapterId" :placeholder="$t('course.selectChapter')" class="full-width" :disabled="!offlineForm.courseId || offlineChapterOptions.length === 0" :aria-label="$t('course.chapter')">
            <el-option v-for="ch in offlineChapterOptions" :key="ch.id" :label="ch.title" :value="ch.id" />
          </el-select>
          <div v-if="offlineForm.courseId && offlineChapterOptions.length === 0" class="form-tip" style="color:var(--el-color-danger);margin-top:4px">{{ $t('course.offlineChapterHint') }}</div>
        </el-form-item>
        <el-form-item :label="$t('course.date')" prop="sessionDate">
          <el-date-picker v-model="offlineForm.sessionDate" type="date" :placeholder="$t('course.selectDate')" value-format="YYYY-MM-DD" class="full-width" :aria-label="$t('course.date')" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item :label="$t('course.startTime')" prop="startTime">
              <el-time-picker v-model="offlineForm.startTime" :placeholder="$t('course.startTime')" value-format="HH:mm:ss" class="full-width" :aria-label="$t('course.startTime')" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$t('course.endTime')" prop="endTime">
              <el-time-picker v-model="offlineForm.endTime" :placeholder="$t('course.endTime')" value-format="HH:mm:ss" class="full-width" :aria-label="$t('course.endTime')" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item :label="$t('course.location')" prop="location">
          <el-input v-model="offlineForm.location" :placeholder="$t('course.locationPlaceholder')" :aria-label="$t('course.location')" />
        </el-form-item>
        <el-form-item :label="$t('course.remark')">
          <el-input v-model="offlineForm.teacherNotes" type="textarea" :rows="2" :placeholder="$t('course.optional')" :aria-label="$t('course.remark')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showOfflineDialog = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="offlineSubmitting" :disabled="offlineSubmitting" @click="submitOffline">{{ $t('course.add') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useCourseWorkspaceRoutes } from '@/composables/useCourseWorkspaceRoutes'
import { useUrlPagination } from '@/composables/useUrlPagination'
import { swrCache } from '@/composables/useStaleWhileRevalidate'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Download, Plus } from '@element-plus/icons-vue'
import { Workbook } from 'exceljs'
import { QuillEditor } from '@vueup/vue-quill'
import '@vueup/vue-quill/dist/vue-quill.snow.css'
import { useUserStore } from '@/store/user'
import { COURSE_TYPE_OPTIONS, COURSE_TYPE_LABELS, getCourseTypeConfig, isCoursewareCourseType } from '@/config/courseTypeConfig'
import { getCourses, createCourse, updateCourseStatus, deleteCourse, approveCourse, rejectCourse, copyCourse, updateCourseCover, publishCourse, unpublishCourse } from '@/api/course'
import { getChapters } from '@/api/chapter'
import { createOfflineSession } from '@/api/offline-session'
import { getCategories } from '@/api/course-category'
import { getUsers } from '@/api/user'

const router = useRouter()
const route = useRoute()
const { t: i18nT } = useI18n()
// 【V333 简化方案】HTML 课件 / PPT 课件独立管理页复用本组件，fixedCourseType 强制类型维度
const props = defineProps({
  fixedCourseType: { type: String, default: '' }
})
const courseTypeOptions = computed(() => {
  if (props.fixedCourseType) {
    return COURSE_TYPE_OPTIONS.filter(o => o.value === props.fixedCourseType)
  }
  return COURSE_TYPE_OPTIONS
})
const { bindToQuery } = useUrlPagination()
const userStore = useUserStore()
const userRole = computed(() => userStore.role)
const {
  courseListPath,
  courseDetailPath,
  courseEditPath,
  slideManagePath
} = useCourseWorkspaceRoutes({
  userRoleRef: userRole
})

// NN/g IA 原则: 标签精度比覆盖更重要。courseType filter 由 URL/固定类型 驱动,
// 落地直接显示"我的HTML课件/PPT课件/视频课/线下课"避免泛词
const courseTypeLabels = COURSE_TYPE_LABELS
const COURSE_TYPE_I18N = {
  HTML_COURSEWARE: 'course.typeHtmlCourseware',
  PPT_COURSEWARE: 'course.typePptCourseware',
  VIDEO: 'course.videoCourse',
  OFFLINE: 'course.typeOfflineCourse'
}
const courseTypeLabel = (type) => COURSE_TYPE_I18N[type] ? t(COURSE_TYPE_I18N[type]) : (type || '')
const pageTitle = computed(() => {
  const base = userRole.value === 'TEACHER' ? t('course.my') : ''
  const activeType = searchForm.courseType || props.fixedCourseType
  if (activeType && courseTypeLabels[activeType]) {
    return `${base}${courseTypeLabel(activeType)}`
  }
  return `${base}${t('course.title')}`
})

const loading = ref(false)
const submitLoading = ref(false)
const actingId = ref(null)
const tableData = ref([])
const totalElements = ref(0)
const page = ref(1)
const size = ref(10)
const categories = ref([])
const teacherOptions = ref([])
const teacherLoading = ref(false)

const searchForm = reactive({
  keyword: '',
  categoryId: '',
  teacherName: '',
  status: '',
  courseType: props.fixedCourseType || ''
})

// P2-14: URL 分页同步
bindToQuery(page, size, searchForm, ['keyword', 'categoryId', 'teacherName', 'status', 'courseType'])
// 【V333】固定类型页：URL 无 courseType 时兜底保持固定类型，防止重置/直达被清空
if (props.fixedCourseType && !searchForm.courseType) {
  searchForm.courseType = props.fixedCourseType
}

const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref(null)

const formData = reactive({
  title: '',
  categoryId: null,
  teacherId: null,
  description: '',
  creditHours: 1,
  semester: '',
  difficulty: null,
  courseType: props.fixedCourseType || 'VIDEO',
  price: null,
  freeAccessScope: 'none',
  freeDeptIds: '[]',
  discountScope: 'none',
  discountPercent: 0
})

// 封面上传相关
const coverUploadRef = ref(null)
const coverPreviewUrl = ref('')
const coverFile = ref(null)

const formRules = {
  title: [{ required: true, message: t('course.inputCourseTitle'), trigger: 'blur' }],
  categoryId: [{ required: true, message: t('course.pleaseSelectCategory'), trigger: 'change' }],
  teacherId: [{ required: true, message: t('course.selectTeacher'), trigger: 'change' }],
  courseType: [{ required: true, message: t('course.courseType'), trigger: 'change' }],
  price: [{ type: 'number', min: 0, message: t('course.priceNonNegative'), trigger: 'blur' }],
  creditHours: [{ type: 'number', min: 0, max: 20, message: t('course.creditRange'), trigger: 'blur' }]
}

const fetchCategories = async () => {
  try {
    const { data } = await getCategories({ size: 1000 })
    categories.value = data.items || []
  } catch {
    ElMessage.error(t('course.fetchCategoriesFailed'))
  }
}
const fetchTeachers = async () => {
  // TEACHER 角色无需下拉选自己（也无权限查用户列表）
  if (userStore.role === 'TEACHER') { teacherOptions.value = []; return }
  try {
    const { data } = await getUsers({ role: 'TEACHER', size: 1000 })
    teacherOptions.value = data.items || []
  } catch { teacherOptions.value = [] }
}

// 远程搜索教师
const remoteSearchTeachers = async (query) => {
  if (!query) {
    // 无关键词时加载全部（保持下拉可选）
    await fetchTeachers()
    return
  }
  teacherLoading.value = true
  try {
    const { data } = await getUsers({ role: 'TEACHER', keyword: query, size: 20 })
    teacherOptions.value = data.items || []
  } catch { teacherOptions.value = [] }
  finally { teacherLoading.value = false }
}

const fetchData = async () => {
  const params = {
    page: page.value - 1,
    size: size.value,
    keyword: searchForm.keyword || undefined,
    categoryId: searchForm.categoryId || undefined,
    teacherName: searchForm.teacherName || undefined,
    status: searchForm.status !== '' ? searchForm.status : undefined,
    courseType: searchForm.courseType !== '' ? searchForm.courseType : undefined,
    // 教师自动过滤为自己的课程
    teacherId: userStore.role === 'TEACHER' ? userStore.userId : null
  }
  // P2-17: SWR 模式 — 如果有缓存数据立即显示（无 loading），后台刷新
  // P2-11: 缓存键加入角色前缀，避免不同角色混用缓存
  const cacheKey = `courses:${userStore.role}:${JSON.stringify(params)}`
  const cached = swrCache.get(cacheKey)
  if (cached && Date.now() - cached.ts < 30000) {
    tableData.value = cached.data.items || []
    totalElements.value = cached.data.totalElements || 0
    // 后台静默刷新
    getCourses(params).then(({ data }) => {
      swrCache.set(cacheKey, { data, ts: Date.now() })
      tableData.value = data.items || []
      totalElements.value = data.totalElements || 0
    }).catch(() => {})
    return
  }
  loading.value = true
  try {
    const { data } = await getCourses(params)
    swrCache.set(cacheKey, { data, ts: Date.now() })
    tableData.value = data.items || []
    totalElements.value = data.totalElements || 0
  } catch {
    ElMessage.error(t('course.fetchCoursesFailed'))
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  page.value = 1
  fetchData()
}

const handleReset = () => {
  searchForm.keyword = ''
  searchForm.categoryId = ''
  searchForm.teacherName = ''
  searchForm.status = ''
  searchForm.courseType = props.fixedCourseType || ''
  page.value = 1
  fetchData()
}

// 【V333】固定类型页防漂移：任何路径清空 courseType 都立即恢复固定类型
watch(() => searchForm.courseType, (v) => {
  if (props.fixedCourseType && !v) searchForm.courseType = props.fixedCourseType
})

const handleSizeChange = () => {
  page.value = 1
  fetchData()
}

const handlePageChange = () => {
  fetchData()
}

const tableRef = ref(null)

const handleCreate = () => {
  isEdit.value = false
  formData.title = ''
  formData.categoryId = null
  formData.teacherId = userStore.role === 'TEACHER' && userStore.userId
    ? Number(userStore.userId)
    : null
  formData.description = ''
  formData.creditHours = 1
  formData.semester = ''
  formData.difficulty = null
  // 【V333】固定类型页创建课程时预设类型
  formData.courseType = props.fixedCourseType || 'VIDEO'
  // 重置封面
  handleRemoveCover()
  dialogVisible.value = true
  fetchTeachers()
}

// 封面上传：选文件后本地预览（不立即上传）
const handleBeforeCoverUpload = (file) => {
  if (file.size > 2 * 1024 * 1024) {
    ElMessage.error(t('course.coverTooBig'))
    return false
  }
  if (!/^image\/(jpeg|jpg|png|gif)$/.test(file.type)) {
    ElMessage.error(t('course.coverFormatOnly'))
    return false
  }
  return true
}

const handleCoverChange = (file) => {
  if (file && file.raw) {
    coverFile.value = file.raw
    if (coverPreviewUrl.value) URL.revokeObjectURL(coverPreviewUrl.value)
    coverPreviewUrl.value = URL.createObjectURL(file.raw)
  }
}

const handleRemoveCover = () => {
  if (coverPreviewUrl.value) {
    URL.revokeObjectURL(coverPreviewUrl.value)
  }
  coverPreviewUrl.value = ''
  coverFile.value = null
  // 清空 el-upload 内部文件列表
  if (coverUploadRef.value) {
    coverUploadRef.value.clearFiles()
  }
}

const handleEdit = (row) => {
  router.push(courseEditPath(row.id))
}

const handleRowClick = (row) => {
  router.push(courseDetailPath(row.id))
}

const handleView = (row) => {
  router.push(courseDetailPath(row.id))
}

const handleApprove = async (row) => {
  try { await ElMessageBox.confirm(t('course.confirmApproveCourse'), t('course.hintTitle'), { type: 'warning' }) } catch { return }
  actingId.value = row.id
  try { await approveCourse(row.id); ElMessage.success(t('course.approveSuccess')); fetchData() }
  catch (e) { ElMessage.error(e?.response?.data?.message || t('course.approveFailed')) }
  finally { actingId.value = null }
}

const handleReject = async (row) => {
  let value
  try {
    const res = await ElMessageBox.prompt(t('course.inputRejectReason'), t('course.rejectCourseTitle'), {
      confirmButtonText: t('course.confirmReject'), cancelButtonText: t('common.cancel'),
      inputType: 'textarea', inputPlaceholder: t('course.rejectReasonPlaceholder'),
      inputValidator: v => v?.trim()?.length >= 10 || t('course.rejectReasonMin')
    })
    value = res.value
  } catch { return }
  actingId.value = row.id
  try { await rejectCourse(row.id, value || ''); ElMessage.success(t('course.rejectSuccess')); fetchData() }
  catch (e) { ElMessage.error(e?.response?.data?.message || t('course.rejectFailed')) }
  finally { actingId.value = null }
}

const handleArchive = async (row) => {
  try { await ElMessageBox.confirm(t('course.confirmArchiveCourse'), t('course.archiveConfirmTitle'), { type: 'warning' }) } catch { return }
  actingId.value = row.id
  try { await updateCourseStatus(row.id, 6); ElMessage.success(t('course.archiveSuccess')); fetchData() }
  catch (e) { ElMessage.error(e?.response?.data?.message || t('course.archiveFailed')) }
  finally { actingId.value = null }
}

const handlePublish = async (row) => {
  try { await ElMessageBox.confirm(t('course.confirmPublishCourse'), t('course.hintTitle'), { type: 'warning' }) } catch { return }
  actingId.value = row.id
  try { await publishCourse(row.id); ElMessage.success(t('course.publishSuccess')); fetchData() }
  catch (e) { ElMessage.error(e?.response?.data?.message || t('course.publishFailed')) }
  finally { actingId.value = null }
}

const handleUnpublish = async (row) => {
  try { await ElMessageBox.confirm(t('course.confirmUnpublishCourse'), t('course.hintTitle'), { type: 'warning' }) } catch { return }
  actingId.value = row.id
  try { await unpublishCourse(row.id); ElMessage.success(t('course.unpublishSuccess')); fetchData() }
  catch (e) { ElMessage.error(e?.response?.data?.message || t('course.unpublishFailed')) }
  finally { actingId.value = null }
}

const handleDelete = async (row) => {
  try { await ElMessageBox.confirm(t('course.confirmDeleteCourse'), t('course.hintTitle'), { type: 'warning' }) } catch { return }
  actingId.value = row.id
  try { await deleteCourse(row.id); ElMessage.success(t('course.deleteSuccess')); fetchData() }
  catch (e) { ElMessage.error(e?.response?.data?.message || t('course.deleteFailed')) }
  finally { actingId.value = null }
}

const handleCopy = async (row) => {
  actingId.value = row.id
  try {
    const { data } = await copyCourse(row.id)
    const newId = data?.id || data
    ElMessage.success(t('course.copySuccessRedirect'))
    router.push(courseEditPath(newId))
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || t('course.copyFailed'))
  } finally { actingId.value = null }
}

const handleExport = async () => {
  if (tableData.value.length === 0) {
    ElMessage.warning(t('course.noExportData'))
    return
  }
  // P2: 导出数量限制，最多 5000 条
  if (totalElements.value > 5000) {
    try {
      await ElMessageBox.confirm(t('course.exportLimitConfirm', { count: totalElements.value }), t('course.hintTitle'), { type: 'warning' })
    } catch { return }
  }
  try {
    ElMessage.info(t('course.exportFetching'))
    // P2-12: 导出全量筛选结果而非当前页，保持筛选条件不变，size 设为 5000(上限)
    const exportParams = {
      page: 0,
      size: 5000,
      keyword: searchForm.keyword || undefined,
      categoryId: searchForm.categoryId || undefined,
      teacherName: searchForm.teacherName || undefined,
      status: searchForm.status !== '' ? searchForm.status : undefined,
      courseType: searchForm.courseType !== '' ? searchForm.courseType : undefined,
      teacherId: userStore.role === 'TEACHER' ? userStore.userId : null
    }
    const { data } = await getCourses(exportParams)
    const allData = data.items || []
    const exportData = allData.map((item, index) => ({
      [i18nT('course.index')]: index + 1,
      [i18nT('course.tableTitle')]: item.title || '',
      [i18nT('app.type')]: courseTypeLabel(item.courseType) || (item.courseType === 'INTERACTIVE' ? i18nT('course.courseware') : i18nT('course.unknown')),
      [i18nT('course.category')]: item.categoryName || '',
      [i18nT('course.teacher')]: item.teacherName || '',
      [i18nT('course.studentCount')]: item.studentCount || 0,
      [i18nT('course.status')]: getStatusLabel(item.status)
    }))
    const wb = new Workbook()
    const ws = wb.addWorksheet(i18nT('course.courseList'))
    ws.addRows(exportData.map(row => Object.values(row)))
    await wb.xlsx.writeFile(i18nT('course.exportFileName', { date: Date.now() }))
    ElMessage.success(i18nT('course.exportSuccess', { count: exportData.length }))
  } catch {
    ElMessage.error(t('course.exportFailed'))
  }
}

function getStatusLabel(status) {
  const map = { 0: t('course.draft'), 1: t('course.pendingReview'), 2: t('course.statusApproved'), 3: t('course.reject'), 4: t('course.published'), 5: t('course.unpublish'), 6: t('course.archived') }
  return map[status] || t('course.unknown')
}

const goSlides = async (row) => {
  // 【D-3 P1-C 修复】课程级课件管理入口死路：自动定位第一个章节，跳到章节级课件管理。
  // 此前 /slides/manage?type=X 无 chapterId/sectionId → SlideManage 只渲染创建卡 + 后端树双 null 报错。
  // 【V333】按课程类型限定课件工作区：HTML 课件 → ?type=HTML，PPT 课件 → ?type=PPT
  const cwType = row.courseType === 'HTML_COURSEWARE' ? 'HTML' : (row.courseType === 'PPT_COURSEWARE' ? 'PPT' : '')
  let firstChapterId = null
  try {
    const { data } = await getChapters({ courseId: row.id, size: 1 })
    firstChapterId = data?.items?.[0]?.id || null
  } catch { /* 章节加载失败 → 维持课程级入口（后端已支持课程级聚合树，不再报错） */ }
  const query = { ...(cwType ? { type: cwType } : {}), ...(firstChapterId ? { chapterId: firstChapterId } : {}) }
  router.push({ path: slideManagePath(row.id), query })
}
const handleManageOffline = (row) => {
  router.push(courseDetailPath(row.id))
}
const handleBackToFullList = () => {
  handleReset()
  router.push(courseListPath.value)
}
const handleSubmit = async () => {
  if (submitLoading.value) return
  if (!formRef.value) return
  // P1 幂等修复: validate 是异步的, loading 必须在 await 之前置位,
  // 否则快速连点会全部穿过守卫并发提交, 产生重复课程
  submitLoading.value = true
  try {
    const valid = await formRef.value.validate()
    if (!valid) { submitLoading.value = false; return }
  } catch {
    submitLoading.value = false
    return
  }
  try {
    const res = await createCourse({
      title: formData.title,
      categoryId: formData.categoryId,
      teacherId: formData.teacherId,
      subtitle: formData.subtitle || '',
      summary: formData.summary || '',
      description: formData.description || '',
      coverUrl: formData.coverUrl || '',
      semester: formData.semester || '',
      difficulty: formData.difficulty,
      courseType: formData.courseType || 'VIDEO',
      creditHours: formData.creditHours || 0,
      price: formData.price || 0,
      freeAccessScope: formData.freeAccessScope,
      freeDeptIds: formData.freeDeptIds,
      discountScope: formData.discountScope,
      discountPercent: formData.discountPercent
    })
    const newCourseId = res?.data?.id
    if (newCourseId && coverFile.value) {
      try {
        await updateCourseCover(newCourseId, coverFile.value)
        ElMessage.success(t('course.createdCoverUploaded'))
      } catch {
        ElMessage.warning(t('course.createdCoverFailed'))
      }
    } else {
      ElMessage.success(t('course.createSuccess'))
    }
    dialogVisible.value = false
    if (newCourseId) {
      router.push(courseDetailPath(newCourseId))
      return
    }
    fetchData()
  } catch {
    ElMessage.error(t('course.createFailed'))
  } finally {
    submitLoading.value = false
  }
}

const handleDialogClose = () => {
  formRef.value?.resetFields()
  handleRemoveCover()
}
// 线下课新增安排
const showOfflineDialog = ref(false)
const offlineSubmitting = ref(false)
const offlineFormRef = ref(null)
const offlineChapterOptions = ref([])
const courseOptions = ref([])  // 线下课课程选择器
    watch(showOfflineDialog, async (v) => {
  if (v) {
    try {
      const { data } = await getCourses({ size: 200 })
      courseOptions.value = data?.items || []
    } catch { courseOptions.value = [] }
  }
})
const offlineForm = reactive({
  courseId: null, chapterId: null, sessionDate: '', startTime: '', endTime: '', location: '', teacherNotes: ''
})
const offlineRules = {
  chapterId: [{ required: true, message: t('course.pleaseSelectChapter'), trigger: 'change' }],
  sessionDate: [{ required: true, message: t('course.pleaseSelectDate'), trigger: 'change' }],
  startTime: [{ required: true, message: t('course.pleaseSelectStartTime'), trigger: 'change' }],
  endTime: [{ required: true, message: t('course.pleaseSelectEndTime'), trigger: 'change' }],
  location: [{ required: true, message: t('course.pleaseInputLocation'), trigger: 'blur' }],
}
async function onOfflineCourseChange(courseId) {
  offlineForm.chapterId = null
  if (!courseId) { offlineChapterOptions.value = []; return }
  try {
    const { data } = await getChapters({ courseId, size: 100 })
    offlineChapterOptions.value = data?.items || []
  } catch { offlineChapterOptions.value = [] }
}
function resetOfflineForm() {
  offlineForm.courseId = null; offlineForm.chapterId = null; offlineForm.sessionDate = ''
  offlineForm.startTime = ''; offlineForm.endTime = ''; offlineForm.location = ''; offlineForm.teacherNotes = ''
  offlineChapterOptions.value = []
  offlineFormRef.value?.resetFields()
}
async function submitOffline() {
  if (!offlineFormRef.value) return
  try { const v = await offlineFormRef.value.validate(); if (!v) return } catch { return }
  if (!offlineForm.chapterId) { ElMessage.warning(t('course.pleaseSelectChapter')); return }
  offlineSubmitting.value = true
  try {
    await createOfflineSession(offlineForm.chapterId, {
      sessionDate: offlineForm.sessionDate,
      startTime: offlineForm.startTime,
      endTime: offlineForm.endTime,
      location: offlineForm.location,
      teacherNotes: offlineForm.teacherNotes || undefined
    })
    ElMessage.success(t('course.offlineCreated'))
    showOfflineDialog.value = false
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || e?.message || t('course.createFailed'))
  } finally {
    offlineSubmitting.value = false
  }
}

onMounted(() => {
  fetchCategories()
  fetchData()
})
</script>

<style scoped>
.course-list-page {
  padding: var(--space-6);
  background: var(--el-bg-color-page);
  min-height: 100dvh;
  max-width: 1440px;
  margin: 0 auto;
}

.cover-preview-wrap { display: flex; flex-direction: column; gap: 6px; align-items: flex-start; }
.cover-preview-wrap img { max-width: 150px; max-height: 80px; border-radius: 4px; border: 1px solid #ebeef5; object-fit: cover; }
.cover-actions { display: flex; gap: 4px; }

.quill-editor-wrapper {
  width: 100%;
  border-radius: 4px;
}

.quill-editor-wrapper :deep(.ql-toolbar) {
  border-radius: 4px 4px 0 0;
  background: #fafafa;
}

.quill-editor-wrapper :deep(.ql-container) {
  border-radius: 0 0 4px 4px;
  font-size: 14px;
}

.page-breadcrumb {
  margin-bottom: var(--space-4);
}

.filter-card {
  margin-bottom: var(--space-6);
  background: var(--el-fill-color-blank);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs), var(--shadow-sm);
}

.table-card {
  background: var(--el-fill-color-blank);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs), var(--shadow-sm);
  transition: box-shadow var(--duration-base) var(--ease-out);
}

.table-card:hover {
  box-shadow: var(--shadow-md), var(--shadow-lg);
}

.table-card :deep(.el-card__header) {
  padding: var(--space-4) var(--space-5);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-actions {
  display: flex;
  gap: var(--space-2);
}

.card-title {
  font-size: var(--text-md);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
  letter-spacing: var(--tracking-wide);
}

.pagination-wrap {
  margin-top: var(--space-4);
  display: flex;
  justify-content: center;
  padding: var(--space-4) var(--space-5);
  border-top: 1px solid var(--el-border-color-lighter);
}

.data-table {
  width: 100%;
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.data-table :deep(.el-table__header) th {
  color: var(--el-text-color-primary);
}

.data-table :deep(.el-table__row) {
  cursor: pointer;
  transition: background-color var(--duration-fast) var(--ease-out);
}

.data-table :deep(.el-table__row:hover > td) {
  background-color: var(--role-primary-light-9);
}

.data-table :deep(.el-table__row--striped > td) {
  background: transparent;
}

.table-thumb {
  width: 48px;
  height: 32px;
  border-radius: var(--radius-md);
  object-fit: cover;
}

.no-thumb {
  color: var(--el-text-color-placeholder);
}

.review-hint {
  font-size: var(--text-xs);
  color: var(--el-text-color-secondary);
  margin-top: var(--space-1);
  line-height: 1.2;
}

.filter-input-w160 {
  width: 160px;
}

.search-input,
.filter-input {
  width: 160px;
  border-radius: var(--radius-md);
}

.search-select,
.filter-select {
  width: 160px;
}

.filter-input-w120 {
  width: 120px;
}

.full-width {
  width: 100%;
}

/* Button border-radius */
:deep(.el-button) {
  border-radius: var(--radius-md);
}

/* Dialog border-radius */
:deep(.el-dialog) {
  border-radius: var(--radius-lg);
}

@media (max-width: 768px) {
  .course-list-page {
    padding: var(--space-4);
  }

  .filter-card {
    margin-bottom: var(--space-4);
  }
}
</style>
