<!--
  课程详情 / 编辑
  路由: /courses/:id  |  /courses/:id/edit
-->
<template>
  <div class="course-detail-page" v-loading="loading" :element-loading-text="$t('common.loading')">
    <!-- 面包屑 -->
    <div class="page-breadcrumb">
        <el-breadcrumb separator="→">
        <el-breadcrumb-item :to="{ path: '/' }">{{ $t('course.home') }}</el-breadcrumb-item>
        <el-breadcrumb-item :to="{ path: userRole === 'TEACHER' ? '/teacher/courses' : '/courses' }">{{ $t('course.courseMgmt') }}</el-breadcrumb-item>
        <el-breadcrumb-item>{{ isCreateMode ? $t('course.createCourse') : (isEditMode ? $t('course.editCourse') : (courseData.title || $t('course.courseDetail'))) }}</el-breadcrumb-item>
      </el-breadcrumb>
    </div>

    <!-- ========== 查看模式 ========== -->
    <template v-if="!isEditMode && !loading">
      <!-- P1C-075: ACADEMIC 只读模式提示 -->
      <el-alert
v-if="userRole === 'ACADEMIC'"
        :title="$t('course.readonlyAcademic')"
        type="warning"
        :closable="false"
        show-icon
        class="readonly-alert"
      />
      <!-- 头部操作栏 -->
      <div class="action-bar">
        <h1 class="course-title">{{ courseData.title || $t('course.unnamed') }}</h1>
        <div class="action-buttons">
          <template v-if="courseData.status === 0 && userRole === 'TEACHER'">
            <el-button type="primary" @click="handleSubmitForReview" :loading="submitLoading" :disabled="submitLoading">{{ $t('course.submitForReview') }}</el-button>
          </template>
          <template v-if="courseData.status === 1 && (userRole === 'ADMIN' || userRole === 'ACADEMIC')">
            <el-button type="success" @click="handleApprove">{{ $t('course.approve') }}</el-button>
            <el-button type="danger" @click="handleReject">{{ $t('course.reject') }}</el-button>
          </template>
          <template v-if="[2, 5].includes(courseData.status) && userRole === 'ADMIN'">
            <el-button type="primary" @click="handlePublish">{{ courseData.status === 5 ? $t('course.publish') : $t('course.publish') }}</el-button>
          </template>
          <template v-if="courseData.status === 4 && userRole === 'ADMIN'">
            <el-button type="warning" @click="handleUnpublish">{{ $t('course.unpublish') }}</el-button>
          </template>
          <el-button v-if="isCoursewareCourseType(courseData.courseType) && (userRole === 'TEACHER' || userRole === 'ADMIN')" type="success" @click="goSlides">{{ $t('course.slideOverview') }}</el-button>
          <el-button v-if="userRole === 'TEACHER' || userRole === 'ADMIN'" type="primary" plain :disabled="courseData.status === 4" @click="switchToEdit">{{ $t('app.edit') }}</el-button>
          <el-button v-if="userRole === 'TEACHER' || userRole === 'ADMIN'" type="warning" plain @click="handleCopy">{{ $t('course.copy') }}</el-button>
          <el-button type="info" plain @click="previewAsStudent">
            <el-icon><View /></el-icon> {{ $t('course.studentPreview') }}
          </el-button>
          <el-button @click="handleBack">{{ $t('app.back') }}</el-button>
        </div>
      </div>

      <!-- 基本信息 -->
      <el-card shadow="never" class="info-card">
        <template #header><span class="card-title">{{ $t('course.basicInfo') }}</span></template>
        <div class="info-grid">
          <div class="info-item">
            <label>{{ $t('course.category') }}</label>
            <span>{{ courseData.categoryName || '-' }}</span>
          </div>
          <div class="info-item">
            <label>{{ $t('course.teacher') }}</label>
            <span>{{ courseData.teacherName || '-' }}</span>
          </div>
          <div class="info-item">
            <label>{{ $t('course.status') }}</label>
            <span>
              <el-tag v-if="courseData.status === 0" type="info" size="small">{{ $t('course.draft') }}</el-tag>
              <el-tag v-else-if="courseData.status === 1" type="warning" size="small">{{ $t('course.submitForReview') }}</el-tag>
              <el-tag v-else-if="courseData.status === 2" type="success" size="small">{{ $t('course.approve') }}</el-tag>
              <el-tag v-else-if="courseData.status === 3" type="danger" size="small">{{ $t('course.reject') }}</el-tag>
              <el-tag v-else-if="courseData.status === 4" type="success" size="small">{{ $t('course.published') }}</el-tag>
              <el-tag v-else-if="courseData.status === 5" type="warning" size="small">{{ $t('course.unpublish') }}</el-tag>
              <el-tag v-else type="info" size="small">{{ $t('course.archived') }}</el-tag>
            </span>
          </div>
          <div class="info-item">
            <label>{{ $t('course.credit') }}</label>
            <span>{{ courseData.creditHours ?? '-' }}</span>
          </div>
          <div class="info-item">
            <label>{{ $t('course.semester') }}</label>
            <span>{{ courseData.semester || '-' }}</span>
          </div>
          <div class="info-item">
            <label>{{ $t('course.difficulty') }}</label>
            <span>
              <template v-if="courseData.difficulty === 1">{{ $t('course.beginner') }}</template>
              <template v-else-if="courseData.difficulty === 2">{{ $t('course.intermediate') }}</template>
              <template v-else-if="courseData.difficulty === 3">{{ $t('course.advanced') }}</template>
              <template v-else>-</template>
            </span>
          </div>
          <div class="info-item">
            <label>{{ $t('course.courseType') }}</label>
            <span>
              <el-tag v-if="getCourseTypeConfig(courseData.courseType)" :type="getCourseTypeConfig(courseData.courseType).tagType" size="small">{{ getCourseTypeConfig(courseData.courseType).label }}</el-tag>
              <span v-else>{{ courseData.courseType || '-' }}</span>
            </span>
          </div>
          <div class="info-item">
            <label>{{ $t('course.coursePrice') }}</label>
            <span class="price">{{ courseData.price ? '¥' + courseData.price : $t('app.free') }}</span>
          </div>
          <div class="info-item">
            <label>{{ $t('course.studentCount') }}</label>
            <span>{{ courseData.studentCount ?? 0 }}</span>
          </div>
        </div>
      </el-card>

      <!-- 封面 -->
      <el-card shadow="never" class="info-card">
        <template #header><span class="card-title">{{ $t('course.courseCover') }}</span></template>
        <template v-if="courseData.coverUrl">
          <el-image :src="courseData.coverUrl" fit="contain" class="cover-img" />
        </template>
        <template v-else>
          <el-alert
            :title="$t('course.noCover')"
            type="warning"
            :closable="false"
            show-icon
            :description="$t('course.noCoverDesc')"
          />
        </template>
      </el-card>

      <!-- 课程描述 -->
      <el-card shadow="never" class="info-card" v-if="courseData.description">
        <template #header><span class="card-title">{{ $t('course.courseDescription') }}</span></template>
        <div class="description-html" v-html="sanitizeHtml(courseData.description)"></div>
      </el-card>

      <!-- 章节管理 -->
      <el-card shadow="never" class="chapter-card">
        <template #header>
          <div class="card-header-row">
            <span class="card-title">{{ $t('course.chapterMgmt') }} <span v-if="userRole === 'TEACHER' || userRole === 'ADMIN'" class="hint">{{ $t('course.dragHint') }}</span></span>
            <el-button v-if="userRole === 'TEACHER' || userRole === 'ADMIN'" type="primary" size="small" @click="handleCreateChapter">{{ $t('course.addChapter') }}</el-button>
          </div>
        </template>
        <el-table ref="chapterTableRef" v-loading="chapterLoading" :data="chapters" stripe row-key="id" @row-keydown.enter="handleRowClick">
          <template #empty><el-empty :description="$t('course.noChapters')" /></template>
          <el-table-column type="expand" width="40" :label="$t('app.detail')">
            <template #default="{ row }">
              <div style="padding:12px 24px 12px 48px;background:var(--el-fill-color-lighter)">
                <div v-loading="sectionLoading[row.id]">
                  <SectionList
                    :sections="sectionsByChapterId[row.id] || []"
                    @upload="(s) => handleSectionUpload(row, s)"
                    @edit="(s) => handleEditSection(row, s)"
                    @delete="(s) => handleDeleteSection(row, s)"
                  />
                  <div style="margin-top:8px">
                    <el-button size="small" type="primary" plain @click.stop="handleAddSection(row)">{{ $t('course.addSection') }}</el-button>
                  </div>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column type="index" label="#" width="60" align="center" />
          <el-table-column prop="title" :label="$t('course.courseName')" min-width="200" show-overflow-tooltip />
          <el-table-column :label="$t('course.sectionCount')" width="70" align="center">
            <template #default="{ row }">{{ (sectionsByChapterId[row.id] || []).length }}</template>
          </el-table-column>
          <el-table-column prop="sortOrder" :label="$t('course.sortOrder')" width="70" align="center" />
          <el-table-column v-if="userRole === 'TEACHER' || userRole === 'ADMIN'" :label="$t('app.operation')" width="120" align="center" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link size="small" @click="handleEditChapter(row)">{{ $t('course.sectionEdit') }}</el-button>
              <el-button type="danger" link size="small" @click="handleDeleteChapter(row)">{{ $t('course.sectionDelete') }}</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div v-if="chapters.length > 0" class="sort-bar">
          <el-button type="warning" size="small" :loading="saveSortLoading" :disabled="saveSortLoading" @click="handleSaveSort">{{ $t('course.saveSort') }}</el-button>
        </div>
      </el-card>
    </template>

    <!-- P1C-075: ACADEMIC 编辑模式只读提示 -->
    <el-alert
v-if="isEditMode && userRole === 'ACADEMIC'"
      :title="$t('course.readonlyAcademicEdit')"
      type="warning"
      :closable="false"
      show-icon
      class="readonly-alert"
    />
    <!-- ========== 编辑模式 ========== -->
    <template v-if="isEditMode && !loading">
      <!-- 基本信息 -->
      <el-card shadow="never" class="info-card">
        <template #header><span class="card-title">{{ $t('course.basicInfo') }}</span></template>
        <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px" class="edit-form">
          <el-form-item :label="$t('course.courseName')" prop="title">
            <el-input v-model="formData.title" :placeholder="$t('course.courseName')" :aria-label="$t('course.courseName')" />
          </el-form-item>
          <el-form-item :label="$t('course.category')" prop="categoryId">
            <el-select v-model="formData.categoryId" :placeholder="$t('course.category')" class="full-width" :aria-label="$t('course.category')">
              <el-option v-for="cat in categories" :key="cat.id" :label="cat.name" :value="cat.id" />
            </el-select>
          </el-form-item>
          <el-form-item :label="$t('course.teacher')" :prop="isCreateMode && isAdminOrAcademic ? 'teacherId' : undefined">
            <!-- 管理员/教务创建课程必须指定授课教师（P1-C：此前后端 teacher_id NOT NULL 409） -->
            <el-select
              v-if="isCreateMode && isAdminOrAcademic"
              v-model="formData.teacherId"
              :placeholder="$t('course.selectTeacher')"
              class="full-width"
              filterable
              :aria-label="$t('course.teacher')"
            >
              <el-option
                v-for="teacherOpt in teacherOptions"
                :key="teacherOpt.id"
                :label="teacherOpt.realName || teacherOpt.username"
                :value="teacherOpt.id"
              />
            </el-select>
            <el-input v-else :model-value="teacherName" disabled :aria-label="$t('course.teacher')" />
          </el-form-item>
          <el-row :gutter="20">
            <el-col :span="8">
              <el-form-item :label="$t('course.credit')">
                <el-input-number v-model="formData.creditHours" :min="0" :max="20" class="full-width" :aria-label="$t('course.credit')" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item :label="$t('course.semester')">
                <el-input v-model="formData.semester" :placeholder="$t('course.semester')" :aria-label="$t('course.semester')" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item :label="$t('course.difficulty')">
                <el-select v-model="formData.difficulty" :placeholder="$t('course.difficulty')" class="full-width" clearable :aria-label="$t('course.difficulty')">
                  <el-option :label="$t('course.beginner')" :value="1" />
                  <el-option :label="$t('course.intermediate')" :value="2" />
                  <el-option :label="$t('course.advanced')" :value="3" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="8">
              <!-- 【F-2026-08-10-05】5 种课件类型：HTML 课件 / PPT 课件 / 视频课件 / 练习课件 / 线下课程。
                 后端 CourseType 枚举保留 V333 4 值（练习按章节维度聚合），前端按 5 维度展示。 -->
              <el-form-item :label="$t('course.courseType')" prop="courseType">
                <el-select v-model="formData.courseType" :placeholder="$t('course.courseType')" class="full-width" :aria-label="$t('course.courseType')">
                  <el-option v-for="opt in courseTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item :label="$t('course.coursePrice') + '(¥)'">
                <el-input-number v-model="formData.price" :min="0" :precision="2" :placeholder="$t('course.pricePlaceholder')" class="full-width" :aria-label="$t('course.coursePrice')" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-divider content-position="left">{{ $t('course.pricingRule') }}</el-divider>
          <!-- 【I-9 硬编码定价范围】当新增枚举值需同步更新此处 -->
          <el-row :gutter="20">
            <el-col :span="8">
              <el-form-item :label="$t('course.freeAccess')">
                <el-select v-model="formData.freeAccessScope" :placeholder="$t('course.freeAccess')" class="full-width" :aria-label="$t('course.freeAccess')">
                  <el-option :label="$t('app.no')" value="none" />
                  <el-option :label="$t('course.sameDepartment')" value="same_department" />
                  <el-option :label="$t('course.sameCollege')" value="same_college" />
                  <el-option :label="$t('course.sameSchool')" value="same_school" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item :label="$t('course.discountScope')">
                <el-select v-model="formData.discountScope" :placeholder="$t('course.discountScope')" class="full-width" :aria-label="$t('course.discountScope')">
                  <el-option :label="$t('app.no')" value="none" />
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
        </el-form>
      </el-card>

      <!-- 课程描述 -->
      <el-card shadow="never" class="info-card">
        <template #header><span class="card-title">{{ $t('course.courseDescription') }}</span></template>
        <el-form label-width="100px">
          <el-form-item :label="$t('course.courseDescription')" prop="description">
            <div class="quill-editor-wrapper">
              <QuillEditor v-model:content="formData.description" content-type="html" toolbar="essential" :placeholder="$t('course.descriptionPlaceholder')" :style="{ minHeight: '180px' }" />
            </div>
          </el-form-item>
        </el-form>
      </el-card>

      <!-- 封面 -->
      <el-card shadow="never" class="info-card">
        <template #header><span class="card-title">{{ $t('course.courseCover') }}</span></template>
        <div class="cover-edit-area">
          <template v-if="!coverPreviewUrl">
            <el-upload ref="coverUploadRef" :auto-upload="false" :limit="1" accept="image/jpeg,image/png,image/gif,image/webp" :on-change="handleCoverChange" drag :aria-label="$t('course.uploadCover')">
              <el-icon class="el-icon--upload"><i class="el-icon-upload" /></el-icon>
              <div class="el-upload__text">{{ $t('course.uploadCover') }}</div>
              <template #tip><div class="form-tip">{{ $t('course.coverSizeHint') }}</div></template>
            </el-upload>
          </template>
          <div v-else class="cover-preview-wrap">
            <img :src="coverPreviewUrl" class="cover-preview-img" :alt="$t('course.coverPreviewAlt')" />
            <el-button size="small" @click="handleRemoveCover">{{ $t('course.removeCover') }}</el-button>
          </div>
        </div>
      </el-card>

      <!-- 操作按钮 -->
      <div class="submit-bar">
        <el-button type="primary" :loading="submitLoading" :disabled="submitLoading" @click="handleSubmit">{{ isCreateMode ? $t('course.createCourse') : $t('app.save') }}</el-button>
        <el-button @click="switchToView">{{ $t('app.cancel') }}</el-button>
      </div>
    </template>

    <!-- 章节弹窗 -->
    <el-dialog v-model="chapterDialogVisible" :title="chapterDialogTitle" width="480px" @close="handleChapterDialogClose" :close-on-press-escape="true">
      <el-form ref="chapterFormRef" :model="chapterFormData" :rules="chapterFormRules" label-width="80px">
        <el-form-item :label="$t('course.chapterName')" prop="title">
          <el-input v-model="chapterFormData.title" :placeholder="$t('course.chapterName')" :aria-label="$t('course.chapterName')" />
        </el-form-item>
        <div class="form-tip" style="margin-bottom:12px;color:var(--el-color-info);font-size:12px">
          {{ $t('course.chapterTypeHint') }}
        </div>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item :label="$t('course.sortOrder')">
              <el-input-number v-model="chapterFormData.sortOrder" :min="1" class="full-width" :aria-label="$t('course.sortOrder')" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$t('course.duration')">
              <el-input-number v-model="chapterFormData.duration" :min="0" :placeholder="$t('course.optional')" class="full-width" :aria-label="$t('course.duration')" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="handleChapterCancel">{{ $t('app.cancel') }}</el-button>
        <el-button type="primary" :loading="chapterSubmitLoading" :disabled="chapterSubmitLoading" @click="handleChapterSubmit">{{ $t('app.confirm') }}</el-button>
      </template>
    </el-dialog>

    <SectionEditDialog
      v-model="showSectionDialog"
      :section="editingSection"
      :is-edit="isEditSection"
      :loading="sectionSubmitLoading"
      @submit="handleSubmitSection"
    />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, computed, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import Sortable from 'sortablejs'
import { useUserStore } from '@/store/user'
import { useCourseWorkspaceRoutes } from '@/composables/useCourseWorkspaceRoutes'
import { getCourseById, createCourse, updateCourse, updateCourseStatus, approveCourse, rejectCourse, submitCourseForReview, updateCourseCover, publishCourse, unpublishCourse, copyCourse } from '@/api/course'
import { getChapters, createChapter, updateChapter, deleteChapter, sortChapters } from '@/api/chapter'
import { getCategories } from '@/api/course-category'
import { getUsers } from '@/api/user'
import { View } from '@element-plus/icons-vue'
import { QuillEditor } from '@vueup/vue-quill'
import '@vueup/vue-quill/dist/vue-quill.snow.css'
import { sanitizeHtml } from '@/utils/xss'
import { listSections, createSection, updateSection, deleteSection } from '@/api/section'
import SectionList from '@/components/course/SectionList.vue'
import SectionEditDialog from '@/components/course/SectionEditDialog.vue'
import { COURSE_TYPE_OPTIONS, getCourseTypeConfig, isCoursewareCourseType } from '@/config/courseTypeConfig'

const router = useRouter()
const route = useRoute()
const { t } = useI18n()
const userStore = useUserStore()
const userRole = computed(() => userStore.role)
const {
  courseListPath,
  courseDetailPath,
  courseEditPath,
  slideManagePath,
  chapterManagePath
} = useCourseWorkspaceRoutes({
  userRoleRef: userRole
})

const userId = computed(() => userStore.userId)

// 【V333 简化方案】课程类型 4 选 1
const courseTypeOptions = COURSE_TYPE_OPTIONS

const courseId = computed(() => route.params.id)
const isOwner = computed(() => {
  return userStore.role === 'ADMIN' ||
    (userStore.role === 'TEACHER' && courseData.value?.teacherId === userStore.userId)
})
// P0 修复: /courses/create 创建模式 — 复用编辑表单渲染空白表单,
// 否则 isEditMode=false + loading 永不复位, 页面永久卡在「加载课程信息...」
const isCreateMode = computed(() => route.name === 'CourseCreate')
const isEditMode = computed(() => route.path.includes('/edit') || isCreateMode.value)
const isAdminOrAcademic = computed(() => ['ADMIN', 'ACADEMIC'].includes(userStore.role))
const teacherOptions = ref([])

const loading = ref(true)
const submitLoading = ref(false)
const courseData = ref({})
const categories = ref([])

// P1-C: 修复 QuillEditor 工具栏按钮缺少 aria-label
const QUILL_LABELS = {
  'ql-bold': t('microSpecialtyManage.quillBold'), 'ql-italic': t('microSpecialtyManage.quillItalic'), 'ql-underline': t('microSpecialtyManage.quillUnderline'),
  'ql-strike': t('microSpecialtyManage.quillStrike'), 'ql-link': t('microSpecialtyManage.quillLink'), 'ql-clean': t('microSpecialtyManage.quillClean'),
  'ql-blockquote': t('microSpecialtyManage.quillBlockquote'), 'ql-code-block': t('microSpecialtyManage.quillCodeBlock'),
  'ql-image': t('microSpecialtyManage.quillImage'), 'ql-list': t('microSpecialtyManage.quillListOrdered'), 'ql-bullet': t('microSpecialtyManage.quillListUnordered'),
  'ql-header': t('microSpecialtyManage.quillHeader')
}
function fixQuillAria() {
  setTimeout(() => {
    document.querySelectorAll('.ql-toolbar button').forEach(btn => {
      if (btn.hasAttribute('aria-label')) return
      const cls = Array.from(btn.classList).find(c => c.startsWith('ql-'))
      if (cls && QUILL_LABELS[cls]) btn.setAttribute('aria-label', QUILL_LABELS[cls])
      else if (cls) btn.setAttribute('aria-label', cls.replace('ql-', '').replace('-', ' '))
    })
    document.querySelectorAll('.ql-picker').forEach(picker => {
      if (!picker.hasAttribute('aria-label') && picker.classList.contains('ql-header')) {
        const labelBtn = picker.querySelector('.ql-picker-label')
        if (labelBtn && !labelBtn.getAttribute('aria-label')) {
          labelBtn.setAttribute('aria-label', t('microSpecialtyManage.quillHeader'))
        }
      }
    })
  }, 500)
}

const formRef = ref(null)
const formData = reactive({
  title: '', categoryId: null, teacherId: null,
  description: '', creditHours: 1, semester: '',
  difficulty: null, courseType: 'VIDEO', price: null, isFree: true,
  freeAccessScope: 'none',
  freeDeptIds: '[]',
  discountScope: 'none',
  discountPercent: 0
})
const formRules = {
  title: [{ required: true, message: t('course.inputCourseTitle'), trigger: 'blur' }],
  categoryId: [{ required: true, message: t('course.pleaseSelectCategory'), trigger: 'change' }],
  teacherId: [{ required: true, message: t('course.selectTeacher'), trigger: 'change' }],
  courseType: [{ required: true, message: t('course.pleaseSelectCourseType'), trigger: 'change' }]
}
const teacherName = computed(() => courseData.value.teacherName || '')

const coverUploadRef = ref(null)
const coverPreviewUrl = ref('')
const coverFile = ref(null)

// ===== 章节 =====
const chapterLoading = ref(false)
const chapterSubmitLoading = ref(false)
const saveSortLoading = ref(false)
const chapters = ref([])
const sectionsByChapterId = ref({})
const sectionLoading = ref({})
const showSectionDialog = ref(false)
const editingSection = ref(null)
const isEditSection = ref(false)
const currentChapterForSection = ref(null)
const sectionSubmitLoading = ref(false)
const chapterDialogVisible = ref(false)
const chapterDialogTitle = ref(t('course.addChapter'))
const isChapterEdit = ref(false)
const currentChapterId = ref(null)
const chapterFormRef = ref(null)
const chapterTableRef = ref(null)

const chapterFormData = reactive({ title: '', sortOrder: 0, duration: 0 })
const onChapterTypeChange = () => {}

let sortableInstance = null

// ===== 数据加载 =====
const fetchCategories = async () => {
  try { const { data } = await getCategories({ size: 1000 }); categories.value = data.items || [] }
  catch { categories.value = [] }
}

// 管理员/教务创建课程需选择授课教师（P1-C：后端 teacher_id NOT NULL）
const fetchTeachers = async () => {
  try {
    const { data } = await getUsers({ role: 'TEACHER', page: 0, size: 500 })
    teacherOptions.value = data?.items || data || []
  } catch { teacherOptions.value = [] }
}

const fetchCourse = async () => {
  // 创建模式无 courseId: 直接结束 loading, 使用表单默认值渲染空白创建表单
  if (!courseId.value) { loading.value = false; return }
  loading.value = true
  try {
    const { data } = await getCourseById(courseId.value)
    courseData.value = data || {}
    if (isEditMode.value) {
      formData.title = data.title || ''
      formData.categoryId = data.categoryId || null
      formData.description = data.description || ''
      formData.creditHours = data.creditHours ?? 1
      formData.semester = data.semester || ''
      formData.difficulty = data.difficulty ?? null
      formData.courseType = data.courseType || 'VIDEO'
      formData.price = data.price ?? null
      formData.isFree = data.isFree !== false
      formData.freeAccessScope = data.freeAccessScope || 'none'
      formData.freeDeptIds = data.freeDeptIds || '[]'
      formData.discountScope = data.discountScope || 'none'
      formData.discountPercent = data.discountPercent ?? 0
      formData.teacherId = data.teacherId || null
      if (data.coverUrl) coverPreviewUrl.value = data.coverUrl
    }
  } catch (e) { ElMessage.error(e?.response?.data?.message || t('course.fetchCourseFailed')) }
  finally { loading.value = false }
}

const fetchChapters = async () => {
  if (!courseId.value) return
  chapterLoading.value = true
  try {
    const { data } = await getChapters({ courseId: courseId.value, size: 999 })
    chapters.value = data?.items || data || []
    // 加载每个章节的课时
    for (const ch of chapters.value) {
      await loadSections(ch.id)
    }
  } catch {
    chapters.value = []
    ElMessage.warning(t('course.fetchChaptersFailed'))
  }
  finally { chapterLoading.value = false; await nextTick(); initSortable() }
}

const initSortable = () => {
  if (sortableInstance) sortableInstance.destroy()
  const el = chapterTableRef.value?.$el?.querySelector('.el-table__body-wrapper tbody')
  if (!el) return
  sortableInstance = Sortable.create(el, {
    filter: '.el-table__row--expanded',
    animation: 150,
    onEnd: () => {
      const rows = el.querySelectorAll('.el-table__row:not(.el-table__row--expanded)')
      const newOrder = []
      rows.forEach(row => {
        const idx = row.getAttribute('data-row-key')
        if (idx) {
          const ch = chapters.value.find(c => String(c.id) === idx)
          if (ch) newOrder.push(ch)
        }
      })
      if (newOrder.length === chapters.value.length) chapters.value = newOrder
    }
  })
}

// ===== 页面操作 =====
const handleRowClick = (row) => {
  chapterTableRef.value?.toggleRowExpansion(row)
}
const handleBack = () => {
  router.push(courseListPath.value)
}
const goSlides = async () => {
  // 【D-3 P1-C 修复】课程级课件管理入口死路：此前跳 /slides/manage?type=X 无 chapterId/sectionId，
  // SlideManage 只渲染创建卡 + getCoursewareTree 双 null 报错，教师无法到达已有内容。
  // 现在：自动定位第一个章节，跳到章节级课件管理（/slides/manage?chapterId=X&type=Y）。
  // 【V333】按课程类型限定课件工作区：HTML 课件 → ?type=HTML，PPT 课件 → ?type=PPT
  const cwType = courseData.value.courseType === 'HTML_COURSEWARE' ? 'HTML'
    : (courseData.value.courseType === 'PPT_COURSEWARE' ? 'PPT' : '')
  let firstChapterId = chapters.value[0]?.id
  if (!firstChapterId && courseId.value) {
    try {
      const { data } = await getChapters({ courseId: courseId.value, size: 1 })
      firstChapterId = data?.items?.[0]?.id || null
    } catch { /* 章节加载失败 → 维持课程级入口（后端已支持课程级聚合树，不再报错） */ }
  }
  const query = { ...(cwType ? { type: cwType } : {}), ...(firstChapterId ? { chapterId: firstChapterId } : {}) }
  router.push({ path: slideManagePath(route.params.id), query })
}
const handleSectionUpload = (chapter, section) => {
  // 【D-3 修复】SectionList「课件」按钮 @upload 死按钮 → 打开该课时的课件管理（上传/编辑入口）
  router.push({ path: slideManagePath(courseId.value), query: { sectionId: section.id } })
}
const previewAsStudent = () => {
  window.open(`/student/courses/${courseId.value}`, '_blank')
}
const switchToEdit = () => {
  if (courseData.value?.status === 4) {
    ElMessage.warning(t('course.editingDisabled'))
    return
  }
  router.push(courseEditPath(courseId.value))
}
const switchToView = () => {
  // 创建模式取消: 无 courseId, 返回课程列表; 编辑模式取消: 回课程详情
  if (isCreateMode.value) { router.push(userRole.value === 'TEACHER' ? '/teacher/courses' : '/courses'); return }
  router.push(courseDetailPath(courseId.value))
}

const handleSubmitForReview = async () => {
  if (submitLoading.value) return
  // 提交前预检：封面必须已上传
  if (!courseData.value?.coverUrl) {
    ElMessage.warning(t('course.coverRequired'))
    return
  }
  try { await ElMessageBox.confirm(t('course.confirmSubmitReview'), t('course.hintTitle'), { type: 'info' }) } catch { return }
  submitLoading.value = true
  try { await submitCourseForReview(courseId.value); ElMessage.success(t('course.submittedForReview')); fetchCourse() }
  catch (e) { ElMessage.error(e?.response?.data?.message || t('course.operationFailed')) }
  finally { submitLoading.value = false }
}
const handleApprove = async () => {
  try { await ElMessageBox.confirm(t('course.confirmApprove'), t('course.hintTitle'), { type: 'info' }) } catch { return }
  try { await approveCourse(courseId.value); ElMessage.success(t('course.approve')); fetchCourse() }
  catch (e) { ElMessage.error(e?.response?.data?.message || t('course.operationFailed')) }
}
const handleReject = async () => {
  // eslint-disable-next-line no-useless-assignment -- reason 在 try 块内被用户输入覆盖
  let reason = ''
  try { const res = await ElMessageBox.prompt(t('course.inputRejectReason'), t('course.rejectTitle'), { confirmButtonText: t('course.dialogConfirm'), inputType: 'textarea', inputProps: { maxlength: 500, showWordLimit: true }, inputValidator: (v) => { if (!v || v.trim().length < 10) { return t('course.rejectReasonMin10') } if (v.trim().length > 500) { return t('course.rejectReasonMax500') } return true } }); reason = res.value }
  catch { return }
  try { await rejectCourse(courseId.value, reason); ElMessage.success(t('course.rejectedSuccess')); fetchCourse() }
  catch (e) { ElMessage.error(e?.response?.data?.message || t('course.operationFailed')) }
}
const handlePublish = async () => {
  try { await ElMessageBox.confirm(t('course.confirmPublish'), t('course.hintTitle'), { type: 'info' }) } catch { return }
  try { await publishCourse(courseId.value); ElMessage.success(t('course.published')); fetchCourse() }
  catch (e) { ElMessage.error(e?.response?.data?.message || t('course.operationFailed')) }
}
const handleUnpublish = async () => {
  try { await ElMessageBox.confirm(t('course.confirmUnpublish'), t('course.hintTitle'), { type: 'info' }) } catch { return }
  try { await unpublishCourse(courseId.value); ElMessage.success(t('course.unpublished')); fetchCourse() }
  catch (e) { ElMessage.error(e?.response?.data?.message || t('course.operationFailed')) }
}

const handleCopy = async () => {
  try { await ElMessageBox.confirm(t('course.confirmCopy'), t('course.hintTitle'), { type: 'info' }) } catch { return }
  try {
    const res = await copyCourse(courseId.value)
    ElMessage.success(t('course.copySuccess'))
    if (res.data?.videoCopied === false) {
      ElMessageBox.alert(t('course.videoNotCopied'), t('course.hintTitle'))
    }
    router.push(courseDetailPath(res.data.id))
  } catch (e) { ElMessage.error(e?.response?.data?.message || t('course.copyFailed')) }
}

// ===== 编辑提交 =====
const handleCoverChange = (file) => {
  if (file.raw && file.raw.size > 2 * 1024 * 1024) {
    ElMessage.warning(t('course.coverTooBig'))
    coverUploadRef.value?.clearFiles()
    return
  }
  coverFile.value = file.raw
  if (coverPreviewUrl.value) URL.revokeObjectURL(coverPreviewUrl.value)
  coverPreviewUrl.value = URL.createObjectURL(file.raw)
}
const handleRemoveCover = () => {
  if (coverPreviewUrl.value) URL.revokeObjectURL(coverPreviewUrl.value)
  coverPreviewUrl.value = ''; coverFile.value = null
  coverUploadRef.value?.clearFiles()
}

const handleSubmit = async () => {
  if (submitLoading.value) return
  if (!formRef.value) return
  // P1 幂等修复: validate 是异步的, loading 必须在 await 之前置位,
  // 否则快速连点会全部穿过守卫并发提交, 产生重复课程 (审计实测 3 连击建 3 门课)
  submitLoading.value = true
  try {
    const valid = await formRef.value.validate()
    if (!valid) { submitLoading.value = false; return }
  } catch { submitLoading.value = false; return }
  try {
    const payload = {
      title: formData.title, categoryId: formData.categoryId, teacherId: formData.teacherId,
      description: formData.description,
      creditHours: formData.creditHours, semester: formData.semester || undefined,
      difficulty: formData.difficulty, courseType: formData.courseType,
      price: formData.price, isFree: formData.isFree,
      freeAccessScope: formData.freeAccessScope,
      freeDeptIds: formData.freeDeptIds,
      discountScope: formData.discountScope,
      discountPercent: formData.discountPercent
    }
    if (isCreateMode.value) {
      // 创建模式: 走 createCourse, 成功后跳新课程详情页; teacherId 为空时后端默认当前登录教师
      const res = await createCourse({ ...payload, teacherId: formData.teacherId || undefined })
      const newCourseId = res?.data?.id
      if (newCourseId && coverFile.value) {
        try { await updateCourseCover(newCourseId, coverFile.value) }
        catch { ElMessage.warning(t('course.createdCoverFailed')) }
      }
      ElMessage.success(t('course.createSuccess'))
      if (newCourseId) router.push(courseDetailPath(newCourseId))
      return
    }
    await updateCourse(courseId.value, payload)
    if (coverFile.value) {
      try { await updateCourseCover(courseId.value, coverFile.value) }
      catch { ElMessage.warning(t('course.savedCoverFailed')) }
    }
    ElMessage.success(t('course.saveSuccess'))
    router.push(courseDetailPath(courseId.value))
  } catch (e) { ElMessage.error(e?.response?.data?.message || t('course.saveFailed')) }
  finally { submitLoading.value = false }
}

// ===== 章节操作 =====
const handleCreateChapter = () => {
  chapterDialogTitle.value = t('course.addChapter'); isChapterEdit.value = false
  chapterFormData.title = ''; chapterFormData.sortOrder = 0; chapterFormData.duration = 0
  chapterDialogVisible.value = true
}
const handleEditChapter = (row) => {
  chapterDialogTitle.value = t('course.chapterEdit'); isChapterEdit.value = true; currentChapterId.value = row.id
  chapterFormData.title = row.title || ''; chapterFormData.sortOrder = row.sortOrder ?? 0
  chapterFormData.duration = row.duration ?? 0
  chapterDialogVisible.value = true
}
const gotoChapterContent = (chapterId, type) => {
  router.push(chapterManagePath(courseId.value, chapterId, type))
}
const loadSections = async (chapterId) => {
  try {
    sectionLoading.value[chapterId] = true
    const { data } = await listSections(courseId.value, chapterId, { page: 0, size: 100 })
    sectionsByChapterId.value[chapterId] = data?.items || []
  } catch {
    sectionsByChapterId.value[chapterId] = []
  } finally {
    sectionLoading.value[chapterId] = false
  }
}

const handleAddSection = (chapter) => {
  currentChapterForSection.value = chapter
  editingSection.value = null
  isEditSection.value = false
  showSectionDialog.value = true
}

const handleEditSection = (chapter, section) => {
  currentChapterForSection.value = chapter
  editingSection.value = section
  isEditSection.value = true
  showSectionDialog.value = true
}

const handleDeleteSection = async (chapter, section) => {
  try {
    await ElMessageBox.confirm(t('course.confirmDeleteSection', { title: section.title }), t('course.confirmDeleteTitle'), { type: 'warning' })
    await deleteSection(courseId.value, chapter.id, section.id, true)
    ElMessage.success(t('course.deleteSuccess'))
    await loadSections(chapter.id)
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e?.response?.data?.message || t('course.deleteFailed'))
  }
}

const handleSubmitSection = async (form) => {
  sectionSubmitLoading.value = true
  try {
    if (isEditSection.value) {
      await updateSection(courseId.value, currentChapterForSection.value.id, editingSection.value.id, form)
      ElMessage.success(t('course.updateSuccess'))
    } else {
      await createSection(courseId.value, currentChapterForSection.value.id, form)
      ElMessage.success(t('course.createSuccess'))
    }
    showSectionDialog.value = false
    await loadSections(currentChapterForSection.value.id)
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || t('course.operationFailed'))
  } finally {
    sectionSubmitLoading.value = false
  }
}

const handleDeleteChapter = async (row) => {
  const videoCount = row.videoCount ?? 0
  const contentHint = videoCount > 0 ? t('course.videoCountHint', { count: videoCount }) : ''
  try { await ElMessageBox.confirm(t('course.confirmDeleteChapter', { title: row.title || '' }) + contentHint, t('course.hintTitle'), { type: 'warning', confirmButtonText: t('course.confirmDeleteTitle'), cancelButtonText: t('app.cancel') }) } catch { return }
  try { await deleteChapter(row.id); ElMessage.success(t('course.deleted')); fetchChapters() }
  catch (e) { ElMessage.error(e?.response?.data?.message || t('course.deleteFailed')) }
}
const handleSaveSort = async () => {
  if (saveSortLoading.value) return
  saveSortLoading.value = true
  const sorts = chapters.value.map((c, i) => ({ id: c.id, sortOrder: i + 1 }))
  try { await sortChapters(sorts); ElMessage.success(t('course.sortSaved')); fetchChapters() }
  catch (e) { ElMessage.error(e?.response?.data?.message || t('course.saveSortFailed')) }
  finally { saveSortLoading.value = false }
}
const handleChapterSubmit = async () => {
  if (chapterSubmitLoading.value) return
  if (!chapterFormRef.value) return
  try { await chapterFormRef.value.validate() } catch { return }
  chapterSubmitLoading.value = true
  try {
    if (isChapterEdit.value) {
      const { title, sortOrder, duration } = chapterFormData
      await updateChapter(currentChapterId.value, { title, sortOrder, duration: duration || 0 })
    } else {
      await createChapter({ ...chapterFormData, courseId: Number(courseId.value) })
    }
    ElMessage.success(isChapterEdit.value ? t('course.updateSuccess') : t('course.createSuccess'))
    chapterDialogVisible.value = false
    await fetchChapters()
  } catch (e) { ElMessage.error(e?.response?.data?.message || e?.message || t('course.operationFailed')) }
  finally { chapterSubmitLoading.value = false }
}
const handleChapterCancel = () => { chapterDialogVisible.value = false }
const handleChapterDialogClose = () => { chapterFormRef.value?.resetFields() }

onMounted(() => {
  // P1C-075: ACADEMIC 角色从编辑模式重定向到查看模式
  // P0 修复补充: 创建模式下无 courseId, ACADEMIC(后端仅 TEACHER/ADMIN 可创建)直接回课程列表,
  // 避免 router.replace(courseDetailPath(undefined)) 产生非法路由
  if (userRole.value === 'ACADEMIC' && isEditMode.value) {
    if (isCreateMode.value) { router.replace('/courses'); return }
    router.replace(courseDetailPath(courseId.value))
    return
  }
  fetchCategories()
  if (isCreateMode.value && isAdminOrAcademic.value) fetchTeachers()
  fetchCourse().then(() => { 
    if (!isEditMode.value) fetchChapters()
    if (isEditMode.value) fixQuillAria()
  })
})
onUnmounted(() => { if (sortableInstance) sortableInstance.destroy() })
</script>

<style scoped>
.course-detail-page {
  padding: 24px;
  max-width: 1100px;
  margin: 0 auto;
}
.page-breadcrumb { margin-bottom: 16px; }

/* 操作栏 */
.action-bar {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 20px; flex-wrap: wrap; gap: 12px;
}
.course-title { font-size: 22px; font-weight: 600; color: #303133; margin: 0; }
.action-buttons { display: flex; gap: 8px; flex-wrap: wrap; }

/* 信息卡片 */
.info-card { margin-bottom: 16px; }
.card-title { font-size: 16px; font-weight: 600; color: #303133; }
.card-header-row { display: flex; justify-content: space-between; align-items: center; }
.hint { font-size: 12px; color: var(--el-color-info); font-weight: 400; }
.edit-card { margin-bottom: 16px; }
.submit-bar { margin-top: 16px; display: flex; gap: 12px; justify-content: flex-end; }
.cover-edit-area { max-width: 400px; }

/* 信息网格 */
.info-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px 24px; }
.info-item { display: flex; flex-direction: column; gap: 4px; }
.info-item label { font-size: 13px; color: var(--el-text-color-secondary); }
.info-item span { font-size: 14px; color: var(--el-text-color-primary); word-break: break-word; }
.price { color: var(--el-color-warning); font-weight: 600; }

/* 封面 */
.cover-img { max-width: 300px; border-radius: 6px; }

/* 描述 HTML */
.description-html { line-height: 1.8; color: #303133; font-size: 14px; }
.description-html :deep(ol), .description-html :deep(ul) { padding-left: 20px; margin: 8px 0; }
.description-html :deep(li) { margin-bottom: 4px; }
.description-html :deep(p) { margin: 8px 0; }

/* 章节 */
.chapter-card { margin-bottom: 16px; }
.sort-bar { margin-top: 12px; text-align: right; }

/* 编辑表单 */
.edit-form { max-width: 700px; }
.full-width { width: 100%; }
.form-tip { font-size: 12px; color: var(--el-color-info); margin-top: 4px; }
.cover-preview-wrap { display: flex; flex-direction: column; gap: 8px; align-items: flex-start; }
.cover-preview-wrap img { max-width: 200px; max-height: 120px; border-radius: 6px; border: 1px solid #ebeef5; object-fit: cover; }

/* Quill */
.quill-editor-wrapper { width: 100%; border-radius: 4px; }
.quill-editor-wrapper :deep(.ql-toolbar) { border-radius: 4px 4px 0 0; background: #fafafa; }
.quill-editor-wrapper :deep(.ql-container) { border-radius: 0 0 4px 4px; font-size: 14px; }

/* P1C-075: 只读模式提示 */
.readonly-alert {
  margin-bottom: 16px;
}

/* 响应式 */
@media (max-width: 768px) {
  .course-detail-page { padding: 12px; }
  .info-grid { grid-template-columns: 1fr 1fr; }
  .cover-img { max-width: 100%; }
}
@media (max-width: 480px) {
  .info-grid { grid-template-columns: 1fr; }
  .action-bar { flex-direction: column; align-items: flex-start; }
}
</style>
