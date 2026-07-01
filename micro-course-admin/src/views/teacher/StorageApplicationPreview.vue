<template>
  <div class="preview-page" v-loading="loading">
    <!-- 顶部操作栏 -->
    <div class="toolbar">
      <el-button @click="$router.back()">返回</el-button>
      <el-button type="primary" @click="handleExport('word')">下载Word</el-button>
      <el-button type="primary" @click="handleExport('pdf')">下载PDF</el-button>
    </div>

    <!-- 错误状态 -->
    <el-result v-if="error" icon="error" title="加载失败" sub-title="无法加载预览数据，请稍后重试">
      <template #extra><el-button type="primary" @click="loadData">重试</el-button></template>
    </el-result>

    <!-- A4 纸样式预览 -->
    <div v-else-if="data" class="a4-paper" id="preview-content">
      <!-- 标题 -->
      <h1 class="preview-title">高校开放共享"微专业"资源平台推荐表</h1>

      <!-- 模块1：基本信息 -->
      <table class="preview-table">
        <tr>
          <td class="label-cell">申报高校</td>
          <td class="value-cell">{{ data.title || '' }}</td>
          <td class="label-cell">微专业名称</td>
          <td class="value-cell">{{ data.microSpecialtyName || '' }}</td>
        </tr>
        <tr>
          <td class="label-cell">专业负责人</td>
          <td class="value-cell">{{ data.leadName || '' }}</td>
          <td class="label-cell">联系电话</td>
          <td class="value-cell">{{ data.contactPhone || '' }}</td>
        </tr>
        <tr>
          <td class="label-cell">申请时间</td>
          <td class="value-cell" colspan="3">{{ data.applyDate || '' }}</td>
        </tr>
      </table>

      <!-- 模块2：微专业基本情况 -->
      <h2 class="section-title">一、微专业基本情况</h2>
      <table class="preview-table">
        <tr>
          <td class="label-cell">类型</td>
          <td class="value-cell">{{ data.type || '急需紧缺型' }}</td>
          <td class="label-cell">面向对象</td>
          <td class="value-cell">{{ data.targetAudience || '' }}</td>
        </tr>
        <tr>
          <td class="label-cell">目标学科</td>
          <td class="value-cell">{{ data.targetDisciplines || '' }}</td>
          <td class="label-cell">总学分</td>
          <td class="value-cell">{{ data.totalCredits || '' }}</td>
        </tr>
        <tr>
          <td class="label-cell">课程数量</td>
          <td class="value-cell">{{ data.courseCount || '' }}</td>
          <td class="label-cell">招生人数</td>
          <td class="value-cell">{{ data.enrollmentQuota || '' }}</td>
        </tr>
        <tr>
          <td class="label-cell">开班人数</td>
          <td class="value-cell">{{ data.classSize || '' }}</td>
          <td class="label-cell">学制</td>
          <td class="value-cell">{{ data.duration || '' }}</td>
        </tr>
        <tr>
          <td class="label-cell">开课时间</td>
          <td class="value-cell">{{ data.startDate || '' }}</td>
          <td class="label-cell">产教融合</td>
          <td class="value-cell">{{ data.isIndustryAcademic ? '是' : '否' }}</td>
        </tr>
        <tr v-if="data.industryPartners">
          <td class="label-cell">合作企业</td>
          <td class="value-cell" colspan="3">{{ data.industryPartners }}</td>
        </tr>
        <tr v-if="data.coBuildUniversities">
          <td class="label-cell">共建高校</td>
          <td class="value-cell" colspan="3">{{ data.coBuildUniversities }}</td>
        </tr>
        <tr v-if="data.plannedShareUniversities">
          <td class="label-cell">拟共享高校</td>
          <td class="value-cell" colspan="3">{{ data.plannedShareUniversities }}</td>
        </tr>
      </table>

      <!-- 微专业介绍（富文本） -->
      <div v-if="data.introduction" class="rich-section">
        <strong class="block-label">微专业介绍：</strong>
        <div v-html="data.introduction" class="rich-content"></div>
      </div>

      <!-- 市场需求分析 -->
      <div v-if="data.marketDemandAnalysis" class="rich-section">
        <strong class="block-label">市场需求分析：</strong>
        <div v-html="data.marketDemandAnalysis" class="rich-content"></div>
      </div>

      <!-- 专业概述 -->
      <div v-if="data.specialtyOverview" class="rich-section">
        <strong class="block-label">专业概况：</strong>
        <div v-html="data.specialtyOverview" class="rich-content"></div>
      </div>

      <!-- 课程设计 -->
      <div v-if="data.curriculumDesign" class="rich-section">
        <strong class="block-label">课程设计思路：</strong>
        <div v-html="data.curriculumDesign" class="rich-content"></div>
      </div>

      <!-- 课程表 -->
      <div v-if="data.courses && data.courses.length" class="table-section">
        <strong class="block-label">课程设置：</strong>
        <table class="preview-table">
          <thead>
            <tr>
              <th>模块</th>
              <th>课程名称</th>
              <th style="width:60px">学时</th>
              <th style="width:60px">学分</th>
              <th>学期</th>
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
        <strong class="block-label">建设保障：</strong>
        <div v-html="data.constructionGuarantee" class="rich-content"></div>
      </div>

      <!-- 模块3：教学团队情况 -->
      <h2 class="section-title">二、微专业教学团队情况</h2>

      <!-- 负责人信息 -->
      <table class="preview-table">
        <tr>
          <td class="label-cell">负责人</td>
          <td class="value-cell">{{ data.leadName || '' }}</td>
          <td class="label-cell">职称</td>
          <td class="value-cell">{{ data.leadTitle || '' }}</td>
        </tr>
        <tr>
          <td class="label-cell">职务</td>
          <td class="value-cell">{{ data.leadPosition || '' }}</td>
          <td class="label-cell">研究方向</td>
          <td class="value-cell">{{ data.leadResearchDirection || '' }}</td>
        </tr>
      </table>

      <!-- 负责人授课课程 -->
      <div v-if="data.leadCourses && data.leadCourses.length" class="table-section">
        <strong class="block-label">负责人授课课程：</strong>
        <table class="preview-table">
          <thead>
            <tr>
              <th>课程名称</th>
              <th style="width:80px">学分</th>
              <th style="width:80px">学时</th>
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
        <strong class="block-label">团队成员：</strong>
        <table class="preview-table">
          <thead>
            <tr>
              <th style="width:50px">序号</th>
              <th>姓名</th>
              <th style="width:60px">年龄</th>
              <th>职称</th>
              <th>所在单位</th>
              <th>专业/行业</th>
              <th>曾授课程</th>
              <th>拟授课程</th>
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
      <h2 class="section-title">三、审批意见</h2>
      <div v-if="data.signatures && data.signatures.length" class="signatures-section">
        <table class="preview-table">
          <thead>
            <tr>
              <th style="width:50px">序号</th>
              <th style="width:100px">审批层级</th>
              <th>审批意见</th>
              <th style="width:90px">签字</th>
              <th style="width:90px">公章</th>
              <th style="width:90px">日期</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="s in data.signatures" :key="s.id">
              <td class="text-center">{{ s.unitSeq || '' }}</td>
              <td>{{ signLevelLabel(s.signLevel) }}</td>
              <td>
                <template v-if="s.opinionText">{{ s.opinionText }}</template>
                <template v-if="s.remark"><br/>备注：{{ s.remark }}</template>
              </td>
              <td class="text-center">
                <img v-if="s.signatureImageUrl" :src="s.signatureImageUrl" class="signature-img" alt="签字" />
                <span v-else-if="s.signatureText">{{ s.signatureText }}</span>
                <span v-else>-</span>
              </td>
              <td class="text-center">
                <img v-if="s.sealImageUrl" :src="s.sealImageUrl" class="seal-img" alt="公章" />
                <span v-else>-</span>
              </td>
              <td class="text-center">{{ s.signDate || '' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else class="empty-section">
        <p class="empty-text">暂无审批记录</p>
      </div>

      <!-- 模块5：共享单位 -->
      <h2 class="section-title">四、共享单位信息</h2>
      <div v-if="data.sharedUnits && data.sharedUnits.length" class="table-section">
        <table class="preview-table">
          <thead>
            <tr>
              <th style="width:50px">序号</th>
              <th>单位名称</th>
              <th style="width:120px">单位类型</th>
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
        <p class="empty-text">暂无共享单位信息</p>
      </div>

      <!-- 页脚 -->
      <div class="preview-footer">
        教育部高校学生司（高校毕业生就业服务司）制 &nbsp;&nbsp; 文件编制时间：2026年3月
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getStoragePreview, exportStorageWord, exportStoragePdf } from '@/api/storageApplication'

const route = useRoute()
const loading = ref(true)
const error = ref(false)
const data = ref(null)

const loadData = async () => {
  loading.value = true
  error.value = false
  try {
    const res = await getStoragePreview(route.params.id)
    data.value = res.data
  } catch {
    error.value = true
  } finally {
    loading.value = false
  }
}

const handleExport = async (type) => {
  const fn = type === 'word' ? exportStorageWord : exportStoragePdf
  try {
    const res = await fn(route.params.id)
    const blob = res.data instanceof Blob ? res.data : new Blob([res.data])
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    const ext = type === 'word' ? 'docx' : 'pdf'
    const title = data.value?.title || '申报'
    a.download = `【${title}】申请表_${new Date().toISOString().slice(0, 10)}.${ext}`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch (e) {
    ElMessage.error('导出失败')
  }
}

const memberTypeLabel = (type) => {
  const map = { INTERNAL: '校内', EXTERNAL: '校外', LEAD: '负责人' }
  return map[type] || type || ''
}

const signLevelLabel = (level) => {
  const map = {
    DEPARTMENT: '院系审批',
    COLLEGE: '学院审批',
    ACADEMIC: '教务处审批',
    UNIVERSITY: '学校审批',
    SHARED_UNIT: '共享单位意见'
  }
  return map[level] || level || ''
}

const unitTypeLabel = (type) => {
  const map = { UNIVERSITY: '高校', ENTERPRISE: '企业', RESEARCH: '科研机构', SHARE_UNIV: '拟共享高校', OTHER: '其他' }  // P1-C-5 修复
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
  color: #999;
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
</style>
