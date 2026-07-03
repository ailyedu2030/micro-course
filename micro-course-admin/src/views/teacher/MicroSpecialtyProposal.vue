<!--
  微专业申报 — 微专业申报表（5模块完整版）
  路由: /teacher/micro-specialties/proposals? 可选 query.id 加载已有草稿
  Phase 15 重做
-->
<template>
  <div class="ms-proposal-page">
    <!-- ========== 页头 ========== -->
    <div class="proposal-header">
      <div class="header-top">
        <el-button text @click="handleBack">
          <el-icon><ArrowLeft /></el-icon> 返回
        </el-button>
        <h2 class="page-title">微专业申报表</h2>
        <div class="header-actions">
          <el-button :loading="saving" @click="handleSave">保存</el-button>
          <span v-if="saveStatus" class="save-status" :class="{ 'save-error': saveStatus === '保存失败' || saveStatus === '⚠ 未保存' }">
            {{ saveStatus }}
          </span>
          <el-button type="primary" :disabled="!formComplete" :loading="submitting" @click="handleSubmit">提交审核</el-button>
          <el-dropdown trigger="click" @command="handleExport">
            <el-button>导出<el-icon class="el-icon--right"><ArrowDown /></el-icon></el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="word">导出 Word</el-dropdown-item>
                <el-dropdown-item command="pdf">导出 PDF</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-button type="danger" plain @click="handleResetAll">重置全部</el-button>
        </div>
      </div>
    </div>

    <!-- P1-C-13: 全局错误状态/重试 -->
    <div v-if="loadError" class="error-overlay">
      <el-result icon="error" title="加载失败" sub-title="无法加载申请表数据，请检查网络后重试">
        <template #extra>
          <el-button type="primary" @click="retryLoad">重试</el-button>
          <el-button @click="handleBack">返回</el-button>
        </template>
      </el-result>
    </div>

    <div v-if="!loadError" v-loading="loading" element-loading-text="加载中..." class="proposal-content">
<!-- ========== 分步导航 ========== -->
    <el-steps :active="step" align-center finish-status="success" class="ms-steps">
      <el-step title="表头基础" description="申报信息" />
      <el-step title="基本情况" description="微专业详情" />
      <el-step title="教学团队" description="负责人+成员" />
      <el-step title="佐证材料" description="签字盖章" />
      <el-step title="确认提交" description="预览+提交" />
    </el-steps>

    <!-- ========== 模块1：表头基础信息 ========== -->
    <el-card v-if="step === 0" shadow="never" class="proposal-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">模块1：表头基础信息</span>
          <el-button type="warning" plain size="small" @click="handleResetModule('module1')">重置模块</el-button>
        </div>
      </template>
      <el-form :model="form" :rules="rules" ref="formRef1" label-width="100px" class="proposal-form">
        <el-form-item label="附件标题">
          <el-input :model-value="attachmentTitle" readonly />
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="申报高校" prop="title">
              <el-input v-model="form.title" placeholder="请输入申报高校全称" maxlength="100" show-word-limit />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="微专业名称" prop="microSpecialtyName">
              <el-input v-model="form.microSpecialtyName" placeholder="请输入微专业名称" maxlength="100" show-word-limit />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="负责人" prop="leadName">
              <el-input v-model="form.leadName" placeholder="请输入负责人姓名" maxlength="50" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="联系电话" prop="contactPhone">
              <el-input v-model="form.contactPhone" placeholder="11位手机号" maxlength="11" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="申请时间" prop="applyDate">
              <DatePickerYM v-model="form.applyDate" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </el-card>

    <!-- ========== 模块2：微专业基本情况 ========== -->
    <el-card v-if="step === 1" shadow="never" class="proposal-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">模块2：微专业基本情况</span>
          <el-button type="warning" plain size="small" @click="handleResetModule('module2')">重置模块</el-button>
        </div>
      </template>
      <el-form :model="form" label-width="100px" class="proposal-form">
        <!-- 第一行 -->
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="类型">
              <el-select v-model="form.type" class="full-width">
                <el-option v-for="t in typeOptions" :key="t" :label="t" :value="t" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="16">
            <el-form-item label="面向对象">
              <el-checkbox-group v-model="form.targetAudience">
                <el-checkbox v-for="a in audienceOptions" :key="a" :label="a" :value="a" />
              </el-checkbox-group>
            </el-form-item>
          </el-col>
        </el-row>
        <!-- 第二行 -->
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="面向学科">
              <el-input v-model="form.targetDisciplines" placeholder="如：管理学、教育学" maxlength="200" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="总学分">
              <el-input-number :model-value="totalCreditsDisplay" :disabled="true" :min="0" :max="100" :precision="1" class="full-width" controls-position="right" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="课程门数">
              <el-input-number :model-value="courses.length" :disabled="true" :min="0" :max="200" class="full-width" controls-position="right" />
            </el-form-item>
          </el-col>
        </el-row>
        <!-- 第三行 -->
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="共建高校">
              <el-input v-model="form.coBuildUniversities" placeholder="多个用逗号分隔" maxlength="300" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="拟共享高校">
              <el-input v-model="form.plannedShareUniversities" placeholder="多个用逗号分隔" maxlength="300" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="开课时间">
              <DatePickerYM v-model="form.startDate" placeholder="选择开课时间" future />
            </el-form-item>
          </el-col>
        </el-row>
        <!-- 第四行 -->
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="招生名额">
              <el-input-number v-model="form.enrollmentQuota" :min="0" :max="10000" class="full-width" controls-position="right" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="成班人数">
              <el-input-number v-model="form.classSize" :min="0" :max="10000" class="full-width" controls-position="right" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="学制">
              <el-input v-model="form.duration" placeholder="如：2年" maxlength="50" />
            </el-form-item>
          </el-col>
        </el-row>
        <!-- 产教融合 -->
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="是否产教融合">
              <el-switch v-model="form.isIndustryAcademic" active-text="是" inactive-text="否" />
            </el-form-item>
          </el-col>
          <el-col :span="16">
            <el-form-item v-if="form.isIndustryAcademic" label="合作单位">
              <el-input v-model="form.industryPartners" placeholder="请输入合作单位名称，多个用逗号分隔" maxlength="300" />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 富文本区域 -->
        <el-divider content-position="left">内容描述</el-divider>

        <el-form-item label="微专业介绍">
          <RichTextWithCounter v-model="form.introduction" placeholder="详细介绍微专业的定位、特色与价值..." :min-height="160" />
        </el-form-item>

        <el-form-item label="就业前景">
          <RichTextWithCounter v-model="form.marketDemandAnalysis" placeholder="描述该微专业的就业方向与市场需求..." :min-height="160" :recommend-threshold="500" />
        </el-form-item>

        <el-form-item label="专业简介">
          <RichTextWithCounter v-model="form.specialtyOverview" placeholder="微专业的整体概述..." :min-height="180" :recommend-threshold="800" />
        </el-form-item>

        <el-form-item label="课程体系">
          <RichTextWithCounter v-model="form.curriculumDesign" placeholder="描述课程设计思路与体系架构..." :min-height="160" :recommend-threshold="500" />
        </el-form-item>

        <el-form-item label="建设保障">
          <RichTextWithCounter v-model="form.constructionGuarantee" placeholder="描述师资、场地、经费等建设保障..." :min-height="160" :recommend-threshold="500" />
        </el-form-item>

        <!-- 课程体系动态表 -->
        <el-divider content-position="left">课程体系</el-divider>
        <div class="table-section">
          <div class="table-summary" v-if="totalCourseHours > 0">
            总学时：<strong>{{ totalCourseHours }}</strong>
          </div>
          <CourseChapterEditor v-model="courses" @change="onCourseChange" />
        </div>
      </el-form>
    </el-card>

    <!-- ========== 模块3：教学团队 ========== -->
    <el-card v-if="step === 2" shadow="never" class="proposal-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">模块3：教学团队</span>
          <el-button type="warning" plain size="small" @click="handleResetModule('module3')">重置模块</el-button>
        </div>
      </template>
      <el-form :model="form" :rules="rules3" label-width="110px" class="proposal-form">
        <el-divider content-position="left">专业负责人</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="姓名">
              <el-input v-model="form.leadName" placeholder="负责人姓名" maxlength="50" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="职称">
              <el-select v-model="form.leadTitle" class="full-width" filterable allow-create placeholder="选择或输入职称">
                <el-option v-for="t in titleOptions" :key="t" :label="t" :value="t" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="职务">
              <el-input v-model="form.leadPosition" placeholder="职务" maxlength="50" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="联系电话" prop="leadPhone">
              <el-input v-model="form.leadPhone" placeholder="负责人电话" maxlength="11" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="研究方向">
              <el-input v-model="form.leadResearchDirection" placeholder="研究方向" maxlength="200" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="主讲课程">
              <el-input v-model="form.leadMainTasks" placeholder="主讲课程" maxlength="300" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">近三年课程（最多5行）</el-divider>
        <DynamicTableEditor v-model="leadCourses" :columns="leadCourseColumns" :default-row="leadCourseDefaultRow" :max-rows="5" />

        <el-divider content-position="left">团队成员</el-divider>
        <DynamicTableEditor v-model="teamMembers" :columns="teamColumns" :default-row="teamDefaultRow" />
      </el-form>

      <!-- Phase 2: 章节分配 -->
      <el-divider content-position="left">章节分配</el-divider>
      <div v-if="teamMembers.length === 0" class="empty-hint">
        先添加上方团队成员, 即可为其分配负责章节
      </div>
      <el-table v-else :data="teamMembers" border size="small">
        <el-table-column label="姓名" width="120">
          <template #default="{ row }">{{ row.name || '(未命名)' }}</template>
        </el-table-column>
        <el-table-column label="已分配章节" min-width="200">
          <template #default="{ row }">
            <el-tag
              v-for="a in chapterAssignments.filter(ca => ca.teamMemberIndex === row._index)"
              :key="a.chapterId"
              type="info"
              size="small"
              closable
              @close="removeChapterAssign(a, row._index)"
              class="mg-right-4">
              {{ getChapterLabel(a.chapterId) }}
            </el-tag>
            <span v-if="!chapterAssignments.some(ca => ca.teamMemberIndex === row._index)" class="muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openChapterDrawer(row)">
              分配章节
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 章节分配 Drawer -->
      <el-drawer v-model="chapterDrawerVisible" title="分配章节" direction="rtl" size="50%">
        <template v-if="chapterDrawerMember">
          <el-alert :title="'为 ' + (chapterDrawerMember.name || '(未命名)') + ' 分配章节'" type="info" :closable="false" show-icon class="mg-bottom-16" />

          <el-collapse v-if="courses.length > 0">
            <el-collapse-item v-for="course in courses" :key="course._index || course.id" :title="course.courseName || '(未命名课程)'">
              <el-checkbox-group v-if="course.chapters && course.chapters.length">
                <div v-for="ch in course.chapters" :key="ch.id" class="chapter-check-item">
                  <el-checkbox
                    :model-value="isChapterAssigned(chapterDrawerMember, ch.id)"
                    :label="ch.title + ' (' + (ch.hours || 0) + '学时)'"
                    @change="(val) => toggleChapter(chapterDrawerMember, ch.id, course.id, val)" />
                </div>
              </el-checkbox-group>
              <div v-else class="empty-hint">该课程还没有章节</div>
            </el-collapse-item>
          </el-collapse>
          <div v-else class="empty-hint">还没有课程, 请先在 Step 2 添加课程和章节</div>
        </template>
        <template #footer>
          <el-button @click="chapterDrawerVisible = false">关闭</el-button>
          <el-button type="primary" @click="chapterDrawerVisible = false">完成</el-button>
        </template>
      </el-drawer>
    </el-card>

    <!-- ========== 模块4：牵头单位意见 ========== -->
    <el-card v-if="step === 3" shadow="never" class="proposal-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">模块4：牵头单位意见</span>
          <el-button type="warning" plain size="small" @click="handleResetModule('module4')">重置模块</el-button>
        </div>
      </template>
      <SignatureBlock title="① 微专业负责人意见" v-model="signatures[0]" :signature-uploader="makeUploader('SIGNATURE')" :seal-uploader="makeUploader('SEAL')" />
      <SignatureBlock title="② 学院意见" v-model="signatures[1]" :signature-uploader="makeUploader('SIGNATURE')" :seal-uploader="makeUploader('SEAL')" />
      <SignatureBlock title="③ 学校意见" v-model="signatures[2]" :signature-uploader="makeUploader('SIGNATURE')" :seal-uploader="makeUploader('SEAL')" />
    </el-card>

    <!-- ========== 模块5：共建共享单位 ========== -->
    <el-card v-if="step === 4" shadow="never" class="proposal-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">模块5：共建共享单位</span>
          <el-button type="warning" plain size="small" @click="handleResetModule('module5')">重置模块</el-button>
        </div>
      </template>
      <div v-if="sharedUnits.length === 0" class="empty-hint">
        暂无共建共享单位，点击下方按钮新增
      </div>
      <div v-for="(unit, idx) in sharedUnits" :key="idx" class="shared-unit-block">
        <div class="unit-header">
          <span class="unit-label">单位 {{ idx + 1 }}</span>
          <el-button type="danger" size="small" link @click="removeSharedUnit(idx)">删除</el-button>
        </div>
        <el-form :model="unit" label-width="100px" size="small">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="单位名称">
                <el-input v-model="unit.unitName" placeholder="请输入单位名称" maxlength="100" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="单位类型">
                <el-select v-model="unit.unitType" class="full-width">
                  <el-option v-for="ut in unitTypeOptions" :key="ut.value" :label="ut.label" :value="ut.value" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="意见">
            <el-input v-model="unit.opinionText" type="textarea" :rows="2" placeholder="请输入意见..." />
          </el-form-item>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="负责人签字">
                <SignatureUploader
                  label="签字"
                  :image-url="unit.signatureImageUrl"
                  :uploader="makeUploader('SHARED_SIGNATURE')"
                  @update:image-url="val => { unit.signatureImageUrl = val }"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="公章">
                <SignatureUploader
                  label="公章"
                  :image-url="unit.sealImageUrl"
                  :uploader="makeUploader('SHARED_SEAL')"
                  @update:image-url="val => { unit.sealImageUrl = val }"
                />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="日期">
                <DatePickerYM v-model="unit.signDate" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="备注">
                <el-input v-model="unit.remark" placeholder="备注信息" maxlength="200" />
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
      </div>
      <div class="add-unit-bar">
        <el-button type="primary" plain size="small" @click="addSharedUnit">+ 新增共享单位</el-button>
      </div>
    </el-card>

    <!-- ========== 底部操作栏 ========== -->
    <div class="footer-bar">
      <el-button @click="handleBack">返回</el-button>
      <el-button :loading="saving" @click="handleSave">保存</el-button>
      <el-button type="primary" :disabled="!formComplete" :loading="submitting" @click="handleSubmit">提交审核</el-button>
      <el-button type="danger" plain @click="handleResetAll">重置全部</el-button>
    </div>
  </div>  <!-- closes ms-proposal-page -->
</div>  <!-- closes v-if="!loadError" wrapper -->

  <!-- 分步导航按钮 -->
  <div v-if="!loadError" class="step-nav">
    <el-button v-if="step > 0" @click="step--">上一步</el-button>
    <el-button v-if="step < 4" type="primary" @click="step++">下一步</el-button>
    <template v-if="step === 4">
      <el-button :loading="saving" @click="handleSave">保存</el-button>
      <el-button type="primary" :disabled="!formComplete" :loading="submitting" @click="handleSubmit">提交审核</el-button>
    </template>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter, onBeforeRouteLeave } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, ArrowDown } from '@element-plus/icons-vue'
import {
  initStorageDraft, getStorageDetail, saveStorageApplication,
  autoSaveStorageApplication, submitStorageApplication,
  resetStorageModule, resetStorageAll,
  exportStorageWord, exportStoragePdf, uploadStorageImage
} from '@/api/storageApplication'
import RichTextWithCounter from '@/components/storage/RichTextWithCounter.vue'
import DynamicTableEditor from '@/components/storage/DynamicTableEditor.vue'
import CourseChapterEditor from '@/components/storage/CourseChapterEditor.vue'
import SignatureBlock from '@/components/storage/SignatureBlock.vue'
import SignatureUploader from '@/components/storage/SignatureUploader.vue'
import DatePickerYM from '@/components/storage/DatePickerYM.vue'
import { useUserStore } from '@/store/user'

const route = useRoute()
const router = useRouter()

// ==================== 状态 ====================
const loading = ref(false)
const saving = ref(false)
const submitting = ref(false)
const step = ref(0)  /* 分步表单当前步骤: 0-4 */
const draftId = ref(null)
const saveStatus = ref('')
const dirty = ref(false)
const pendingSave = ref(false)  // RT-3: 标记正在执行的自动保存（防止标签页关闭数据丢失）
const formRef1 = ref(null)
const loadError = ref(false)  // P1-C-13: 全局加载错误标志
const initialLoadComplete = ref(false)
const autoSaveEnabled = ref(false)
const leavingConfirmed = ref(false)  /* 防 route guard 双重确认 */

// 附件标题（只读）
const attachmentTitle = computed(() => {
  const name = form.value.microSpecialtyName || '未命名'
  return `微专业申报表 - ${name}`
})

// 表单主体
const form = ref({
  // 模块1
  title: '',
  microSpecialtyName: '',
  leadName: '',
  contactPhone: '',
  applyDate: '',
  // 模块2
  type: '急需紧缺型',
  targetAudience: [],
  targetDisciplines: '',
  totalCredits: null,
  courseCount: null,
  coBuildUniversities: '',
  plannedShareUniversities: '',
  enrollmentQuota: null,
  classSize: null,
  startDate: '',
  duration: '',
  isIndustryAcademic: false,
  industryPartners: '',
  introduction: '',
  marketDemandAnalysis: '',
  specialtyOverview: '',
  curriculumDesign: '',
  constructionGuarantee: '',
  // 模块3
  leadTitle: '',
  leadPosition: '',
  leadPhone: '',
  leadResearchDirection: '',
  leadMainTasks: ''
})

// 子表（独立 ref，供 DynamicTableEditor v-model 使用）
const courses = ref([])
const leadCourses = ref([])
const teamMembers = ref([])

// 签字模块（3个签名块）—— signLevel 必须存在，后端 DB NOT NULL + 后端生成 SHARED_UNIT 时按此匹配
const signatures = ref([
  { signLevel: 'LEAD',   opinionText: '', signature: { type: 'TEXT', text: '', imageUrl: '' }, seal: { type: 'TEXT', text: '', imageUrl: '' }, signDate: '', remark: '' },
  { signLevel: 'DEPT',   opinionText: '', signature: { type: 'TEXT', text: '', imageUrl: '' }, seal: { type: 'TEXT', text: '', imageUrl: '' }, signDate: '', remark: '' },
  { signLevel: 'SCHOOL', opinionText: '', signature: { type: 'TEXT', text: '', imageUrl: '' }, seal: { type: 'TEXT', text: '', imageUrl: '' }, signDate: '', remark: '' }
])

// 共建共享单位
const sharedUnits = ref([])

// Phase 2: 章节-教师分配
const chapterAssignments = ref([])
const chapterDrawerVisible = ref(false)
const chapterDrawerMember = ref(null)  // 当前正在分配的团队成员

function openChapterDrawer(member) {
  chapterDrawerMember.value = member
  chapterDrawerVisible.value = true
}

function isChapterAssigned(member, chapterId) {
  return chapterAssignments.value.some(a => 
    a.chapterId === chapterId && a.teamMemberIndex === member._index)
}

function toggleChapter(member, chapterId, courseId, checked) {
  const memberIndex = member._index
  if (checked) {
    chapterAssignments.value.push({
      courseId, chapterId, 
      teamMemberIndex: memberIndex,
      teacherId: memberIndex + 1  // 用序号占位(提案阶段,文本名)
    })
  } else {
    chapterAssignments.value = chapterAssignments.value.filter(a =>
      !(a.chapterId === chapterId && a.teamMemberIndex === memberIndex))
  }
}

// 获取所有课程章节(扁平化,供 Drawer 使用)
function allChapters() {
  const result = []
  for (const course of courses.value) {
    for (const ch of (course.chapters || [])) {
      result.push({ id: ch.id, title: ch.title, courseName: course.courseName, courseId: course.id })
    }
  }
  return result
}

function getChapterLabel(chapterId) {
  for (const course of courses.value) {
    const ch = (course.chapters || []).find(c => c.id === chapterId)
    if (ch) return (course.courseName || '') + ' / ' + (ch.title || '')
  }
  return '(未知章节)'
}
function removeChapterAssign(assign, memberIndex) {
  chapterAssignments.value = chapterAssignments.value.filter(a =>
    !(a.chapterId === assign.chapterId && a.teamMemberIndex === memberIndex))
}

// ==================== 下拉选项 ====================
const typeOptions = ['急需紧缺型']
const audienceOptions = ['专科', '本科', '硕士', '博士']
const titleOptions = ['教授', '副教授', '讲师', '助教', '企业导师']
const unitTypeOptions = [
  { value: 'CO_BUILD_UNIV', label: '共建高校' },
  { value: 'ENTERPRISE', label: '合作企业' },
  { value: 'SHARE_UNIV', label: '拟共享高校' }  // P1-C-5 修复：与 UnitType 枚举一致
]

// ==================== 表格列配置 ====================
const courseColumns = [
  { prop: 'moduleName', label: '模块', type: 'text', placeholder: '如：专业基础模块' },
  { prop: 'courseName', label: '课程名称', type: 'text', minWidth: '180', placeholder: '必填' },
  { prop: 'hours', label: '学时', type: 'number', width: '80', min: 1 },
  { prop: 'credits', label: '学分', type: 'number', width: '80', min: 0.5 },
  { prop: 'semester', label: '开课学期', type: 'text', placeholder: '如：第1学期' }
]
const courseDefaultRow = { moduleName: '', courseName: '', hours: null, credits: null, semester: '', chapters: [] }

const leadCourseColumns = [
  { prop: 'courseName', label: '课程名称', type: 'text', minWidth: '180', placeholder: '必填' },
  { prop: 'credits', label: '学分', type: 'number', width: '80', min: 0.5 },
  { prop: 'hours', label: '学时', type: 'number', width: '80', min: 1 }
]
const leadCourseDefaultRow = { courseName: '', credits: null, hours: null }

const teamColumns = [
  { prop: 'name', label: '姓名', type: 'text', placeholder: '必填', minWidth: '100' },
  { prop: 'age', label: '年龄', type: 'number', width: '70', min: 18, max: 70 },
  { prop: 'title', label: '职称', type: 'text', placeholder: '教授/副教授/讲师/企业导师' },
  { prop: 'organization', label: '所在单位', type: 'text', minWidth: '150' },
  { prop: 'profession', label: '专业/行业', type: 'text', minWidth: '150' },
  { prop: 'taughtCourses', label: '曾授课程', type: 'text', minWidth: '150' },
  { prop: 'plannedCourses', label: '拟授课程', type: 'text', minWidth: '150' }
]
const teamDefaultRow = { name: '', age: null, title: '', organization: '', profession: '', taughtCourses: '', plannedCourses: '' }

// ==================== 表单校验 ====================
const rules = {
  title: [{ required: true, message: '请输入申报高校名称', trigger: 'blur' }],
  microSpecialtyName: [{ required: true, message: '请输入微专业名称', trigger: 'blur' }],
  leadName: [{ required: true, message: '请输入专业负责人', trigger: 'blur' }],
  contactPhone: [
    { required: true, message: '请输入联系电话', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的11位手机号', trigger: 'blur' }
  ],
  applyDate: [{ required: true, message: '请选择申请时间', trigger: 'change' }]
}

// ==================== 模块3 表单校验 (P2-A) ====================
const rules3 = {
  leadPhone: [
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的11位手机号', trigger: 'blur' }
  ]
}

// ==================== 计算属性 ====================
const totalCourseHours = computed(() => {
  return courses.value.reduce((sum, row) => sum + (Number(row.hours) || 0), 0)
})

/** 课程表学分列总和（自动联动到总学分输入框） */
const totalCreditsDisplay = computed(() => {
  const sum = courses.value.reduce((s, row) => s + (Number(row.credits) || 0), 0)
  return sum || 0
})

// 同步课程门数/总学分到 form（提交时后端需要这些字段）
watch(courses, () => {
  form.value.courseCount = courses.value.length
  form.value.totalCredits = totalCreditsDisplay.value
}, { deep: true })

/** 每学分最低16学时校验：学时 < 学分×16 时警告 */
watch(courses, () => {
  for (const row of courses.value) {
    const cred = Number(row.credits) || 0
    const hrs = Number(row.hours) || 0
    if (cred > 0 && hrs > 0 && hrs < cred * 16) {
      console.warn(`[学分·学时] 课程「${row.courseName || ''}」学分 ${cred} 建议至少 ${cred * 16} 学时，当前 ${hrs}`)
    }
  }
}, { deep: true })

/** P2-2 修复: 章节学时之和 vs 课程学时汇总校验警告 */
watch(courses, () => {
  for (const row of courses.value) {
    const courseHrs = Number(row.hours) || 0
    const chapters = row.chapters || []
    if (courseHrs > 0 && chapters.length > 0) {
      const chapterSum = chapters.reduce((s, ch) => s + (Number(ch.hours) || 0), 0)
      if (chapterSum !== courseHrs) {
        console.warn(`[章节·学时] 课程「${row.courseName || ''}」课程学时 ${courseHrs} 与 ${chapters.length} 个章节学时之和 ${chapterSum} 不一致`)
      }
    }
  }
}, { deep: true })

// 表单是否完整(用于禁用"提交审核"按钮,防止误点)
const formComplete = computed(() => {
  return !!(
    form.value.title &&
    form.value.microSpecialtyName &&
    form.value.leadName &&
    form.value.contactPhone &&
    form.value.applyDate &&
    courses.value.length > 0 &&
    teamMembers.value.length > 0
  )
})

// ==================== 构建保存 payload ====================
function buildSavePayload() {
  const payload = { ...form.value }
  // P0-5 修复：checkbox-group 产生的数组转为逗号分隔字符串
  if (Array.isArray(payload.targetAudience)) {
    payload.targetAudience = payload.targetAudience.join(',')
  }
  return {
    ...payload,
    courses: courses.value,
    leadCourses: leadCourses.value,
    teamMembers: teamMembers.value,
    signatures: signatures.value,
    sharedUnits: sharedUnits.value,
    chapterAssignments: chapterAssignments.value.map(a => ({
      courseId: a.courseId,
      chapterId: a.chapterId,
      teacherId: a.teacherId
    }))
  }
}

// ==================== 课程表变更 ====================
function onCourseChange() {
  // 可在此处理课程表变更后的逻辑
}

// ==================== 共享单位操作 ====================
function addSharedUnit() {
  sharedUnits.value.push({
    unitName: '',
    unitType: 'SHARE_UNIV',  // P1-C-5 修复
    sortOrder: sharedUnits.value.length + 1,
    opinionText: '',
    signatureImageUrl: '',  // P1-UX: 顶层字段，与 SignatureUploader image-url 直接对接
    sealImageUrl: '',       // P1-UX: 顶层字段
    signDate: '',
    remark: ''
  })
}

function removeSharedUnit(index) {
  sharedUnits.value.splice(index, 1)
}

// ==================== 上传工厂 ====================
// P1-UX: 返回符合 SignatureUploader 期望的 (file, onProgress) => Promise<{url}> 函数
// 让组件可以真实上传到后端并接收 0-100% 进度
function makeUploader(type) {
  return async (file, onProgress) => {
    if (!draftId.value) throw new Error('草稿初始化未完成，请稍候')
    return await uploadStorageImage(draftId.value, file, type, onProgress)
  }
}

// ==================== 保存 ====================
async function handleSave() {
  if (!draftId.value) {
    ElMessage.warning('草稿尚未初始化')
    return
  }
  // P1-C-11 修复：增加程序化表单校验
  try {
    await formRef1.value?.validate()
  } catch {
    ElMessage.warning('请补全必填项后再保存')
    return
  }
  saving.value = true
  try {
    await saveStorageApplication(draftId.value, buildSavePayload())
    saveStatus.value = '已保存 ' + new Date().toLocaleTimeString()
    dirty.value = false
    autoSaveEnabled.value = true  /* 手动保存成功后启用 autoSave */
    ElMessage.success('保存成功')
  } catch (e) {
    saveStatus.value = '保存失败'
    ElMessage.error(e?.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

// ==================== 自动保存（1.5s 防抖） ====================
const autoSaveTimer = ref(null)

/**
 * Extracted auto-save scheduler — shared by all targeted watchers.
 * Debounces: resets the timer on each call, only fires after 1.5s of inactivity.
 */
function scheduleAutoSave() {
  if (!draftId.value) return
  if (!initialLoadComplete.value) return
  dirty.value = true  /* 先标记 dirty（即使 autoSave 未启用也能触发离开警告） */
  if (!autoSaveEnabled.value) return
  if (autoSaveTimer.value) clearTimeout(autoSaveTimer.value)
  autoSaveTimer.value = setTimeout(async () => {
    pendingSave.value = true  // RT-3: mark save in progress
    saveStatus.value = '保存中'
    try {
      await autoSaveStorageApplication(draftId.value, buildSavePayload())
      saveStatus.value = '已保存 ' + new Date().toLocaleTimeString()
      dirty.value = false
    } catch {
      saveStatus.value = '⚠ 保存失败'
    } finally {
      pendingSave.value = false  // RT-3: clear in-progress flag
    }
  }, 1500)
}

// ---- Targeted watchers (replaces single deep watch) ----
// Deep-watching form caused unnecessary timer resets on every keystroke
// in rich text fields (HTML string changes). Now we watch specific signals.

// Non-rich-text form fields: shallow watch via computed snapshot
const formDirtySnapshot = computed(() => {
   
  const { introduction, marketDemandAnalysis, specialtyOverview, curriculumDesign, constructionGuarantee, ...rest } = form.value
  return { ...rest }
})
watch(formDirtySnapshot, scheduleAutoSave)

// Rich text fields: watch string length only (not deep) to detect edits
watch(() => form.value.introduction?.length, scheduleAutoSave)
watch(() => form.value.marketDemandAnalysis?.length, scheduleAutoSave)
watch(() => form.value.specialtyOverview?.length, scheduleAutoSave)
watch(() => form.value.curriculumDesign?.length, scheduleAutoSave)
watch(() => form.value.constructionGuarantee?.length, scheduleAutoSave)

// Sub-table arrays: deep watch via JSON (captures cell edits, not just add/remove)
watch(() => JSON.stringify(courses.value), scheduleAutoSave)
watch(() => JSON.stringify(leadCourses.value), scheduleAutoSave)
watch(() => JSON.stringify(teamMembers.value), scheduleAutoSave)
watch(() => JSON.stringify(signatures.value), scheduleAutoSave)
watch(() => JSON.stringify(sharedUnits.value), scheduleAutoSave)

// Phase 2: 自动维护 teamMembers._index — P1-UX: 修复递归更新死循环
// Vue 3 watch 会对整个数组重新触发 (包括 deep 修改成员 _index)
// 改用 length 监听 + 计算属性，触发时机更精准
const teamMembersIndex = computed(() => teamMembers.value.map((_, i) => i))
watch(teamMembersIndex, () => {
  teamMembers.value.forEach((m, i) => { if (m._index !== i) m._index = i })
})

// ==================== 提交审核 ====================
async function handleSubmit() {
  if (!draftId.value) {
    ElMessage.warning('草稿尚未初始化')
    return
  }
  // P2-G: 业务级校验 — 课程表至少1行
  if (courses.value.length === 0) {
    ElMessage.warning('请至少填写一行课程信息')
    return
  }
  // P2-G: 业务级校验 — 教学团队至少1人
  if (teamMembers.value.length === 0) {
    ElMessage.warning('请至少填写一位教学团队成员')
    return
  }
  // P1-C-11 修复：增加程序化表单校验
  try {
    await formRef1.value?.validate()
  } catch (errors) {
    // P1-UX: 滚动到第一个错误字段 + 焦点, 用户立即知道改哪里
    const firstErrorField = Object.keys(errors || {})[0]
    if (firstErrorField) {
      const el = document.querySelector(`[prop="${firstErrorField}"]`)
      if (el) {
        el.scrollIntoView({ behavior: 'smooth', block: 'center' })
        const input = el.querySelector('input, textarea, select')
        if (input) input.focus()
      }
    }
    ElMessage.warning('请补全必填项后再提交')
    return
  }
  try {
    await ElMessageBox.confirm('提交后将无法修改，确定提交审核？', '确认提交', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  submitting.value = true
  try {
    await submitStorageApplication(draftId.value)
    ElMessage.success('提交成功，等待审核')
    router.push('/teacher/micro-specialties/my-proposals')
  } catch (e) {
    // P1-UX: 提交失败的详细错误滚动定位（同上）
    const errorData = e?.response?.data
    if (errorData?.errors) {
      const firstErrorField = Object.keys(errorData.errors)[0]
      if (firstErrorField) {
        const el = document.querySelector(`[prop="${firstErrorField}"]`)
        if (el) {
          el.scrollIntoView({ behavior: 'smooth', block: 'center' })
          const input = el.querySelector('input, textarea, select')
          if (input) input.focus()
        }
      }
    }
    ElMessage.error(errorData?.message || '提交失败')
  } finally {
    submitting.value = false
  }
}

// ==================== 重置模块 ====================
async function handleResetModule(moduleName) {
  if (!draftId.value) return
  try {
    await ElMessageBox.confirm(`确定重置「${moduleName}」的数据？此操作不可恢复。`, '确认重置', { type: 'warning' })
  } catch {
    return
  }
  try {
    await resetStorageModule(draftId.value, moduleName)
    ElMessage.success('模块已重置')
    await loadDraft(draftId.value)
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '重置失败')
  }
}

// ==================== 重置全部 ====================
async function handleResetAll() {
  if (!draftId.value) return
  try {
    await ElMessageBox.confirm('确定重置全部数据？此操作不可恢复！', '确认全部重置', {
      confirmButtonText: '确定重置',
      cancelButtonText: '取消',
      type: 'error'
    })
  } catch {
    return
  }
  try {
    await resetStorageAll(draftId.value)
    ElMessage.success('已全部重置')
    await loadDraft(draftId.value)
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '重置失败')
  }
}

// ==================== 导出 ====================
async function handleExport(type) {
  if (!draftId.value) {
    ElMessage.warning('草稿尚未初始化')
    return
  }
  const fn = type === 'word' ? exportStorageWord : exportStoragePdf
  try {
    const res = await fn(draftId.value)
    // B4 fix: check if response is actually a JSON error disguised as blob
    if (res.data && res.data.type === 'application/json') {
      const text = await new Response(res.data).text()
      const err = JSON.parse(text)
      ElMessage.error(err.message || '导出校验失败')
      return
    }
    const blob = new Blob([res.data])
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    const ext = type === 'word' ? 'docx' : 'pdf'
    const schoolName = form.value.title || '申报高校'
    const date = new Date().toISOString().slice(0, 10).replace(/-/g, '')
    a.download = `【${schoolName}】微专业申报表_${date}.${ext}`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success(`${type === 'word' ? 'Word' : 'PDF'} 导出成功`)
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '导出失败，请检查表单完整性')
  }
}

// ==================== 加载草稿 ====================
async function loadDraft(id) {
  loading.value = true
  loadError.value = false
  try {
    const res = await getStorageDetail(id)
    const data = res.data
    if (data) {
      // 同步顶层字段
      const topFields = [
        'title', 'microSpecialtyName', 'leadName', 'contactPhone', 'applyDate',
        'type', 'targetAudience', 'targetDisciplines', 'totalCredits', 'courseCount',
        'coBuildUniversities', 'plannedShareUniversities', 'enrollmentQuota', 'classSize',
        'startDate', 'duration', 'isIndustryAcademic', 'industryPartners',
        'introduction', 'marketDemandAnalysis', 'specialtyOverview', 'curriculumDesign', 'constructionGuarantee',
        'leadTitle', 'leadPosition', 'leadPhone', 'leadResearchDirection', 'leadMainTasks'
      ]
      for (const field of topFields) {
        if (data[field] !== undefined) {
          form.value[field] = data[field]
        }
      }
      // B1 fix: convert targetAudience from comma-separated string to array for checkbox-group
      if (typeof form.value.targetAudience === 'string') {
        form.value.targetAudience = form.value.targetAudience
          ? form.value.targetAudience.split(',').map(s => s.trim())
          : []
      }
      // 同步子表
      courses.value = (data.courses || []).map(c => ({ ...c, chapters: c.chapters || [] }))
      leadCourses.value = data.leadCourses || []
      teamMembers.value = data.teamMembers || []
      // 同步签名（确保至少3个）
      const sigs = data.signatures || []
      signatures.value = sigs.length >= 3
        ? sigs.slice(0, 3)
        : [
            sigs[0] || { opinionText: '', signature: { type: 'TEXT', text: '', imageUrl: '' }, seal: { type: 'TEXT', text: '', imageUrl: '' }, signDate: '', remark: '' },
            sigs[1] || { opinionText: '', signature: { type: 'TEXT', text: '', imageUrl: '' }, seal: { type: 'TEXT', text: '', imageUrl: '' }, signDate: '', remark: '' },
            sigs[2] || { opinionText: '', signature: { type: 'TEXT', text: '', imageUrl: '' }, seal: { type: 'TEXT', text: '', imageUrl: '' }, signDate: '', remark: '' }
          ]
      sharedUnits.value = data.sharedUnits || []
      // 加载章节分配
      if (data.chapterAssignments) {
        chapterAssignments.value = data.chapterAssignments.map(a => ({
          ...a, teamMemberIndex: (a.teacherId || 1) - 1
        }))
      }
    }
    draftId.value = id
    dirty.value = false
    saveStatus.value = ''
    initialLoadComplete.value = true
  } catch (e) {
    loadError.value = true  // P1-C-13: 显示错误状态
    ElMessage.error(e?.response?.data?.message || '加载草稿失败')
  } finally {
    loading.value = false
  }
}

// ==================== 初始化草稿 ====================
async function initDraft() {
  loadError.value = false
  try {
    const res = await initStorageDraft()
    const id = typeof res.data === 'object' ? res.data.id : res.data
    draftId.value = id
    // P1-UX: 写入 URL query, 刷新页面或分享链接不会丢失数据
    router.replace({ query: { ...route.query, id } })
    // 自动填充当前教师的姓名和联系方式
    const currentUser = useUserStore()
    if (currentUser.realName) form.value.leadName = currentUser.realName
    if (currentUser.phone) form.value.contactPhone = currentUser.phone
    dirty.value = false
    saveStatus.value = ''
    initialLoadComplete.value = true
    autoSaveEnabled.value = true
    // draftId 已经在 initDraft() 返回时拿到，图片上传按钮立即可用
    // 不再自动保存（避免空表单 PUT 400），改为图片上传时才要求表单已存在
  } catch (e) {
    loadError.value = true  // P1-C-13: 显示错误状态
    ElMessage.error(e?.response?.data?.message || '初始化草稿失败')
  }
}

// P1-C-13: 重试加载
function retryLoad() {
  const id = route.query.id || route.params.id
  if (id) {
    loadDraft(id)
  } else {
    initDraft()
  }
}

// ==================== 返回 ====================
function handleBack() {
  if (dirty.value) {
    ElMessageBox.confirm('有未保存的更改，确定离开？', '确认离开', { type: 'warning' })
      .then(() => {
        leavingConfirmed.value = true
        router.push('/teacher/micro-specialties/my-proposals')
      })
      .catch(() => {})
  } else {
    router.push('/teacher/micro-specialties/my-proposals')
  }
}

// RT-3: 标签页关闭/刷新前警告 — 防止 autoSave 进行中的数据丢失
function handleBeforeUnload(e) {
  if (dirty.value || pendingSave.value) {
    e.preventDefault()
    e.returnValue = '有未保存的数据，确定离开吗？'
  }
}

// ==================== 路由离开守卫 ====================
onBeforeRouteLeave((to, from, next) => {
  if (dirty.value && !leavingConfirmed.value) {
    ElMessageBox.confirm('有未保存的更改，确定离开？', '确认离开', { type: 'warning' })
      .then(() => next())
      .catch(() => next(false))
  } else {
    next()
  }
})

// ==================== 初始化 ====================
onMounted(async () => {
  window.addEventListener('beforeunload', handleBeforeUnload)
  const id = route.query.id || route.params.id
  if (id) {
    // 30秒超时: 如果 API 无响应, 强制解除 loading 白纱
    const timer = setTimeout(() => { loading.value && (loading.value = false) }, 30000)
    await loadDraft(id)
    clearTimeout(timer)
  } else {
    await initDraft()
  }
})

// ==================== 清理 ====================
onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', handleBeforeUnload)  // RT-3: 移除标签页关闭警告
  if (autoSaveTimer.value) {
    clearTimeout(autoSaveTimer.value)
  }
})
</script>

<style scoped>
.ms-proposal-page {
  padding: 20px 24px;
  max-width: 100%;
  margin: 0 auto;
}

/* 页头 */
.proposal-header {
  margin-bottom: 20px;
}
.header-top {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}
.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: #303133;
  flex: 1;
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.save-status {
  font-size: 12px;
  color: #67c23a;
  white-space: nowrap;
}
.save-status.save-error {
  color: #f56c6c;
  font-weight: 600;
}

/* 卡片 */
.proposal-card {
  margin-bottom: 16px;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

/* 表单 */
.proposal-form {
  max-width: 100%;
  padding: 0;
}
.full-width {
  width: 100%;
}

/* 让 el-form-item 撑满，富文本对齐 */
.proposal-form :deep(.el-form-item) {
  display: flex !important;
  width: 100% !important;
  margin-bottom: 22px;
}
.proposal-form :deep(.el-form-item__label) {
  width: 110px !important;
  flex-shrink: 0;
}
.proposal-form :deep(.el-form-item__content) {
  flex: 1 1 0 !important;
  min-width: 0 !important;
  width: 100% !important;
  margin-left: 0 !important;
}
/* 富文本编辑器撑满 */
.rich-text-counter {
  width: 100% !important;
  display: block;
}
.rich-text-wrapper {
  width: 100% !important;
  display: block;
  min-width: 0;
}
.rich-text-wrapper :deep(.ql-toolbar) {
  width: 100% !important;
  max-width: 100% !important;
  box-sizing: border-box;
  display: block;
}
.rich-text-wrapper :deep(.ql-container) {
  width: 100% !important;
  max-width: 100% !important;
  box-sizing: border-box;
  font-size: 14px;
  display: block;
}
.rich-text-wrapper :deep(.ql-editor) {
  min-height: 160px;
}

/* 表格区块 */
.table-section {
  margin-top: 8px;
}
.table-summary {
  margin-bottom: 8px;
  font-size: 14px;
  color: #606266;
}
.table-summary strong {
  color: #409eff;
}

/* 共享单位 */
.shared-unit-block {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 16px;
  margin-bottom: 12px;
  background: #fafafa;
}
.unit-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.unit-label {
  font-weight: 600;
  font-size: 14px;
  color: #303133;
}
.empty-hint {
  text-align: center;
  color: #909399;
  padding: 24px 0;
  font-size: 14px;
}
.add-unit-bar {
  text-align: center;
  margin-top: 8px;
}

/* Phase 2: 章节分配 */
.mg-right-4 {
  margin-right: 4px;
}
.mg-bottom-16 {
  margin-bottom: 16px;
}
.muted {
  color: #c0c4cc;
}
.chapter-check-item {
  padding: 6px 0;
}

/* 底部操作栏 */
.footer-bar {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 8px;
  padding-bottom: 32px;
  border-top: 1px solid #ebeef5;
  margin-top: 8px;
}

/* P1-C-13: 错误覆盖层 */
.error-overlay {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 400px;
}

.proposal-content {
  /* 内容容器占位 */
}
.ms-steps {
  margin-bottom: 24px;
}
.step-nav {
  display: flex;
  justify-content: center;
  gap: 16px;
  padding: 24px 0;
}
</style>
