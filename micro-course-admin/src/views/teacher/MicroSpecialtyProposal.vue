<!--
  微专业申报 — 整理收纳微专业申请表（5模块完整版）
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
        <h2 class="page-title">整理收纳微专业申请表</h2>
        <div class="header-actions">
          <el-button :loading="saving" @click="handleSave">保存</el-button>
          <span v-if="saveStatus" class="save-status" :class="{ 'save-error': saveStatus === '保存失败' || saveStatus === '⚠ 未保存' }">
            {{ saveStatus }}
          </span>
          <el-button type="primary" @click="handleSubmit">提交审核</el-button>
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

    <div v-if="!loadError" class="proposal-content">

    <!-- ========== 模块1：表头基础信息 ========== -->
    <el-card shadow="never" class="proposal-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">模块1：表头基础信息</span>
          <el-button type="warning" plain size="small" @click="handleResetModule('module1')">重置模块</el-button>
        </div>
      </template>
      <el-form :model="form" :rules="rules" ref="formRef1" label-width="110px" class="proposal-form">
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
    <el-card shadow="never" class="proposal-card" v-loading="loading">
      <template #header>
        <div class="card-header">
          <span class="card-title">模块2：微专业基本情况</span>
          <el-button type="warning" plain size="small" @click="handleResetModule('module2')">重置模块</el-button>
        </div>
      </template>
      <el-form :model="form" label-width="110px" class="proposal-form">
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
              <el-input-number v-model="form.totalCredits" :min="0" :max="100" :precision="1" class="full-width" controls-position="right" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="课程门数">
              <el-input-number v-model="form.courseCount" :min="0" :max="200" class="full-width" controls-position="right" />
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
              <DatePickerYM v-model="form.startDate" />
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
          <DynamicTableEditor v-model="courses" :columns="courseColumns" :default-row="courseDefaultRow" @change="onCourseChange" />
        </div>
      </el-form>
    </el-card>

    <!-- ========== 模块3：教学团队 ========== -->
    <el-card shadow="never" class="proposal-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">模块3：教学团队</span>
          <el-button type="warning" plain size="small" @click="handleResetModule('module3')">重置模块</el-button>
        </div>
      </template>
      <el-form :model="form" label-width="110px" class="proposal-form">
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
            <el-form-item label="联系电话">
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
    </el-card>

    <!-- ========== 模块4：牵头单位意见 ========== -->
    <el-card shadow="never" class="proposal-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">模块4：牵头单位意见</span>
          <el-button type="warning" plain size="small" @click="handleResetModule('module4')">重置模块</el-button>
        </div>
      </template>
      <SignatureBlock title="① 微专业负责人意见" v-model="signatures[0]" :upload-handler="signatureUploadHandler" />
      <SignatureBlock title="② 学院意见" v-model="signatures[1]" :upload-handler="sealUploadHandler" />
      <SignatureBlock title="③ 学校意见" v-model="signatures[2]" :upload-handler="sealUploadHandler" />
    </el-card>

    <!-- ========== 模块5：共建共享单位 ========== -->
    <el-card shadow="never" class="proposal-card">
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
                <SignatureUploader v-model="unit.signature" :upload-handler="sharedUnitSignatureHandler" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="公章">
                <SignatureUploader v-model="unit.seal" :upload-handler="sharedUnitSealHandler" />
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
      <el-button type="primary" :loading="submitting" @click="handleSubmit">提交审核</el-button>
      <el-button type="danger" plain @click="handleResetAll">重置全部</el-button>
    </div>
  </div>  <!-- closes ms-proposal-page -->

  </div>  <!-- closes v-if="!loadError" wrapper -->
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
import SignatureBlock from '@/components/storage/SignatureBlock.vue'
import SignatureUploader from '@/components/storage/SignatureUploader.vue'
import DatePickerYM from '@/components/storage/DatePickerYM.vue'

const route = useRoute()
const router = useRouter()

// ==================== 状态 ====================
const loading = ref(false)
const saving = ref(false)
const submitting = ref(false)
const draftId = ref(null)
const saveStatus = ref('')
const dirty = ref(false)
const pendingSave = ref(false)  // RT-3: 标记正在执行的自动保存（防止标签页关闭数据丢失）
const formRef1 = ref(null)
const loadError = ref(false)  // P1-C-13: 全局加载错误标志

// 附件标题（只读）
const attachmentTitle = computed(() => {
  const name = form.value.microSpecialtyName || '未命名'
  return `整理收纳微专业申请表 - ${name}`
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

// 签字模块（3个签名块）
const signatures = ref([
  { opinionText: '', signature: { type: 'TEXT', text: '', imageUrl: '' }, seal: { type: 'TEXT', text: '', imageUrl: '' }, signDate: '', remark: '' },
  { opinionText: '', signature: { type: 'TEXT', text: '', imageUrl: '' }, seal: { type: 'TEXT', text: '', imageUrl: '' }, signDate: '', remark: '' },
  { opinionText: '', signature: { type: 'TEXT', text: '', imageUrl: '' }, seal: { type: 'TEXT', text: '', imageUrl: '' }, signDate: '', remark: '' }
])

// 共建共享单位
const sharedUnits = ref([])

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
const courseDefaultRow = { moduleName: '', courseName: '', hours: null, credits: null, semester: '' }

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

// ==================== 计算属性 ====================
const totalCourseHours = computed(() => {
  return courses.value.reduce((sum, row) => sum + (Number(row.hours) || 0), 0)
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
    sharedUnits: sharedUnits.value
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
    signature: { type: 'TEXT', text: '', imageUrl: '' },
    seal: { type: 'TEXT', text: '', imageUrl: '' },
    signDate: '',
    remark: ''  // B2 fix: add remark field for shared units
  })
}

function removeSharedUnit(index) {
  sharedUnits.value.splice(index, 1)
}

// ==================== 上传处理器 ====================
function signatureUploadHandler(file) {
  if (!draftId.value) return Promise.reject(new Error('请先保存草稿'))
  return uploadStorageImage(draftId.value, file, 'SIGNATURE')
}

function sealUploadHandler(file) {
  if (!draftId.value) return Promise.reject(new Error('请先保存草稿'))
  return uploadStorageImage(draftId.value, file, 'SEAL')
}

function sharedUnitSignatureHandler(file) {
  if (!draftId.value) return Promise.reject(new Error('请先保存草稿'))
  return uploadStorageImage(draftId.value, file, 'SHARED_SIGNATURE')
}

function sharedUnitSealHandler(file) {
  if (!draftId.value) return Promise.reject(new Error('请先保存草稿'))
  return uploadStorageImage(draftId.value, file, 'SHARED_SEAL')
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

watch(
  [form, courses, leadCourses, teamMembers, signatures, sharedUnits],
  () => {
    if (!draftId.value) return
    dirty.value = true
    if (autoSaveTimer.value) clearTimeout(autoSaveTimer.value)
    autoSaveTimer.value = setTimeout(async () => {
      pendingSave.value = true  // RT-3: 标记正在保存（配合 beforeunload 防止数据丢失）
      saveStatus.value = '保存中'
      try {
        await autoSaveStorageApplication(draftId.value, buildSavePayload())
        saveStatus.value = '已保存 ' + new Date().toLocaleTimeString()
        dirty.value = false
      } catch {
        saveStatus.value = '保存失败'
        // B3 fix: flash the save status to a persistent warning so user notices
        setTimeout(() => {
          if (saveStatus.value === '保存失败') {
            saveStatus.value = '⚠ 未保存'
          }
        }, 5000)
      } finally {
        pendingSave.value = false  // RT-3: 清除待保存标记
      }
    }, 1500)
  },
  { deep: true }
)

// ==================== 提交审核 ====================
async function handleSubmit() {
  if (!draftId.value) {
    ElMessage.warning('草稿尚未初始化')
    return
  }
  // P1-C-11 修复：增加程序化表单校验
  try {
    await formRef1.value?.validate()
  } catch {
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
    ElMessage.error(e?.response?.data?.message || '提交失败')
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
    a.download = `【${schoolName}】整理收纳微专业申请表_${date}.${ext}`
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
      courses.value = data.courses || []
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
    }
    draftId.value = id
    dirty.value = false
    saveStatus.value = ''
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
    // initDraft 返回 draftId（可能是字符串或数字）
    const id = typeof res.data === 'object' ? res.data.id : res.data
    draftId.value = id
    dirty.value = false
    saveStatus.value = ''
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
      .then(() => router.back())
      .catch(() => {})
  } else {
    router.back()
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
  if (dirty.value) {
    ElMessageBox.confirm('有未保存的更改，确定离开？', '确认离开', { type: 'warning' })
      .then(() => next())
      .catch(() => next(false))
  } else {
    next()
  }
})

// ==================== 初始化 ====================
onMounted(async () => {
  window.addEventListener('beforeunload', handleBeforeUnload)  // RT-3: 标签页关闭警告
  const id = route.query.id || route.params.id
  if (id) {
    await loadDraft(id)
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
  max-width: 1100px;
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
  gap: 10px;
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
}
.full-width {
  width: 100%;
}

/* 富文本 */
.rich-text-wrapper {
  width: 100%;
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

/* 底部操作栏 */
.footer-bar {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
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
</style>
