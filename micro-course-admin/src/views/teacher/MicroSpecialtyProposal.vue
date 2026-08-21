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
          <el-icon><ArrowLeft /></el-icon> {{ $t('app.back') }}
        </el-button>
        <h2 class="page-title">{{ $t('microSpecialtyProposal.pageTitle') }}</h2>
        <div class="header-actions">
          <el-button :loading="saving" :disabled="saving" @click="handleSave">{{ $t('app.save') }}</el-button>
          <span v-if="saveStatus" class="save-status" :class="{ 'save-error': isSaveError }">
            {{ saveStatus }}
          </span>
          <el-button type="primary" :disabled="!formComplete" :loading="submitting" @click="handleSubmit">{{ $t('course.submitForReview') }}</el-button>
          <el-dropdown trigger="click" @command="handleExport">
            <el-button>{{ $t('course.export') }}<el-icon class="el-icon--right"><ArrowDown /></el-icon></el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="word">{{ $t('microSpecialtyProposal.exportWord') }}</el-dropdown-item>
                <el-dropdown-item command="pdf">{{ $t('microSpecialtyProposal.exportPdf') }}</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-button type="danger" plain @click="handleResetAll">{{ $t('microSpecialtyProposal.resetAll') }}</el-button>
        </div>
      </div>
    </div>

    <!-- P1-C-13: 全局错误状态/重试 -->
    <div v-if="loadError" class="error-overlay">
      <el-result icon="error" :title="$t('teacherDashboard.loadFailed')" :sub-title="$t('microSpecialtyProposal.loadErrorDesc')">
        <template #extra>
          <el-button type="primary" @click="retryLoad">{{ $t('common.retry') }}</el-button>
          <el-button @click="handleBack">{{ $t('app.back') }}</el-button>
        </template>
      </el-result>
    </div>

    <div v-if="!loadError" v-loading="loading" :element-loading-text="$t('common.loading')" class="proposal-content">
<!-- ========== 分步导航 ========== -->
    <el-steps :active="step" align-center finish-status="success" class="ms-steps">
      <el-step :title="$t('microSpecialtyProposal.step1Title')" :description="$t('microSpecialtyProposal.step1Desc')" />
      <el-step :title="$t('microSpecialtyProposal.step2Title')" :description="$t('microSpecialtyProposal.step2Desc')" />
      <el-step :title="$t('microSpecialtyProposal.step3Title')" :description="$t('microSpecialtyProposal.step3Desc')" />
      <el-step :title="$t('microSpecialtyProposal.step4Title')" :description="$t('microSpecialtyProposal.step4Desc')" />
      <el-step :title="$t('microSpecialtyProposal.step5Title')" :description="$t('microSpecialtyProposal.step5Desc')" />
    </el-steps>

    <!-- ========== 模块1：表头基础信息 ========== -->
    <el-card v-if="step === 0" shadow="never" class="proposal-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ $t('microSpecialtyProposal.module1Title') }}</span>
          <!-- 模块1为主表字段，无可重置的子表，隐藏重置按钮 -->
        </div>
      </template>
      <el-form :model="form" :rules="rules" ref="formRef1" label-width="100px" class="proposal-form">
        <el-form-item :label="$t('microSpecialtyProposal.attachmentTitle')">
          <el-input :model-value="attachmentTitle" readonly />
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item :label="$t('microSpecialtyProposal.applyingUniversity')" prop="title">
              <el-input v-model="form.title" :placeholder="$t('microSpecialtyProposal.applyingUniversityPlaceholder')" maxlength="100" show-word-limit />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item :label="$t('microSpecialtyProposal.microSpecialtyName')" prop="microSpecialtyName">
              <el-input v-model="form.microSpecialtyName" :placeholder="$t('microSpecialtyProposal.microSpecialtyNamePlaceholder')" maxlength="100" show-word-limit />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item :label="$t('microSpecialtyProposal.leader')" prop="leadName">
              <el-input v-model="form.leadName" :placeholder="$t('microSpecialtyProposal.leaderPlaceholder')" maxlength="50" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$t('microSpecialtyProposal.contactPhone')" prop="contactPhone">
              <el-input v-model="form.contactPhone" :placeholder="$t('microSpecialtyProposal.phone11Placeholder')" maxlength="11" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$t('microSpecialtyProposal.applyDate')" prop="applyDate">
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
          <span class="card-title">{{ $t('microSpecialtyProposal.module2Title') }}</span>
          <!-- 模块2为主表字段，无可重置的子表，隐藏重置按钮 -->
        </div>
      </template>
      <el-form :model="form" :rules="rules2" ref="formRef2" label-width="100px" class="proposal-form">
        <!-- 第一行 -->
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item :label="$t('app.type')" prop="type">
              <el-select v-model="form.type" class="full-width">
                <el-option v-for="opt in typeOptions" :key="opt" :label="typeLabel(opt)" :value="opt" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="16">
            <el-form-item :label="$t('microSpecialtyProposal.targetAudience')" prop="targetAudience">
              <el-checkbox-group v-model="form.targetAudience">
                <el-checkbox v-for="a in audienceOptions" :key="a" :label="audienceLabel(a)" :value="a" />
              </el-checkbox-group>
            </el-form-item>
          </el-col>
        </el-row>
        <!-- 第二行 -->
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item :label="$t('microSpecialtyProposal.targetDisciplines')" prop="targetDisciplines">
              <el-input v-model="form.targetDisciplines" :placeholder="$t('microSpecialtyProposal.targetDisciplinesPlaceholder')" maxlength="200" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$t('microSpecialtyProposal.totalCredits')">
              <el-input-number :model-value="totalCreditsDisplay" :disabled="true" :min="0" :max="100" :precision="1" class="full-width" controls-position="right" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$t('microSpecialtyProposal.courseCount')">
              <el-input-number :model-value="courses.length" :disabled="true" :min="0" :max="200" class="full-width" controls-position="right" />
            </el-form-item>
          </el-col>
        </el-row>
        <!-- 第三行 -->
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item :label="$t('microSpecialtyProposal.coBuildUniversities')">
              <el-input v-model="form.coBuildUniversities" :placeholder="$t('microSpecialtyProposal.commaSeparated')" maxlength="300" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$t('microSpecialtyProposal.plannedShareUniversities')">
              <el-input v-model="form.plannedShareUniversities" :placeholder="$t('microSpecialtyProposal.commaSeparated')" maxlength="300" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$t('microSpecialtyProposal.startDate')">
                  <DatePickerYM v-model="form.startDate" :placeholder="$t('microSpecialtyProposal.startDatePlaceholder')" future precision="month" />
            </el-form-item>
          </el-col>
        </el-row>
        <!-- 第四行 -->
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item :label="$t('microSpecialtyProposal.enrollmentQuota')">
              <el-input-number v-model="form.enrollmentQuota" :min="1" :max="10000" class="full-width" controls-position="right" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$t('microSpecialtyProposal.classSize')">
              <el-input-number v-model="form.classSize" :min="1" :max="10000" class="full-width" controls-position="right" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$t('microSpecialtyProposal.duration')">
              <el-input v-model="form.duration" :placeholder="$t('microSpecialtyProposal.durationPlaceholder')" maxlength="50" />
            </el-form-item>
          </el-col>
        </el-row>
        <!-- 产教融合 -->
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item :label="$t('microSpecialtyProposal.isIndustryAcademic')">
              <el-switch v-model="form.isIndustryAcademic" :active-text="$t('app.yes')" :inactive-text="$t('app.no')" />
            </el-form-item>
          </el-col>
          <el-col :span="16">
            <el-form-item v-if="form.isIndustryAcademic" :label="$t('microSpecialtyProposal.industryPartners')">
              <el-input v-model="form.industryPartners" :placeholder="$t('microSpecialtyProposal.industryPartnersPlaceholder')" maxlength="300" />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 富文本区域 -->
        <el-divider content-position="left">{{ $t('microSpecialtyProposal.contentDescription') }}</el-divider>

        <el-form-item :label="$t('microSpecialtyProposal.introduction')">
          <RichTextWithCounter v-model="form.introduction" :placeholder="$t('microSpecialtyProposal.introductionPlaceholder')" :min-height="160" :recommend-threshold="1000" :warning-threshold="1200" :max-threshold="1500" />
        </el-form-item>

        <el-form-item :label="$t('microSpecialtyProposal.marketDemandAnalysis')">
          <RichTextWithCounter v-model="form.marketDemandAnalysis" :placeholder="$t('microSpecialtyProposal.marketDemandAnalysisPlaceholder')" :min-height="160" :recommend-threshold="800" :warning-threshold="1000" :max-threshold="1200" />
        </el-form-item>

        <el-form-item :label="$t('microSpecialtyProposal.specialtyOverview')">
          <RichTextWithCounter v-model="form.specialtyOverview" :placeholder="$t('microSpecialtyProposal.specialtyOverviewPlaceholder')" :min-height="180" :recommend-threshold="800" :warning-threshold="1000" :max-threshold="1200" />
        </el-form-item>

        <el-form-item :label="$t('microSpecialtyProposal.curriculumDesign')">
          <RichTextWithCounter v-model="form.curriculumDesign" :placeholder="$t('microSpecialtyProposal.curriculumDesignPlaceholder')" :min-height="160" :recommend-threshold="800" :warning-threshold="1000" :max-threshold="1200" />
        </el-form-item>

        <el-form-item :label="$t('microSpecialtyProposal.constructionGuarantee')">
          <RichTextWithCounter v-model="form.constructionGuarantee" :placeholder="$t('microSpecialtyProposal.constructionGuaranteePlaceholder')" :min-height="160" :recommend-threshold="800" :warning-threshold="1000" :max-threshold="1200" />
        </el-form-item>

        <!-- 课程体系动态表 -->
        <el-divider content-position="left">
          <span>{{ $t('microSpecialtyProposal.curriculumDesign') }}</span>
          <el-badge
            v-if="courseValidation.totalIssues > 0"
            :value="courseValidation.totalIssues"
            :type="courseValidation.worstLevel === 'error' ? 'danger' : 'warning'"
            class="course-issue-badge"
          />
        </el-divider>

        <!-- Tier3: 课程学时校验 banner -->
        <el-alert
          v-if="showCourseIssueAlert"
          :type="courseValidation.worstLevel === 'error' ? 'error' : 'warning'"
          :closable="false"
          show-icon
          class="course-hours-alert"
          :title="courseValidation.errorCount > 0
            ? $t('microSpecialtyProposal.hoursMismatchAlert', { count: courseValidation.issueList.length, errorCount: courseValidation.errorCount })
            : $t('microSpecialtyProposal.hoursAttentionAlert', { count: courseValidation.issueList.length })">
          <template #default>
            <ul class="course-issue-list">
              <li v-for="(it, idx) in courseValidation.issueList" :key="idx">
                <strong :class="`issue-${it.level}`">{{ it.courseName }}：</strong>
                <span v-for="(d, di) in it.issues" :key="di" :class="`issue-${d.severity}`">
                  {{ d.message }}<span v-if="di < it.issues.length - 1">{{ $t('microSpecialtyProposal.issueSeparator') }}</span>
                </span>
              </li>
            </ul>
            <div class="course-issue-actions">
              <el-button size="small" type="primary" @click="fixCourseHoursToChapterSum">
                {{ $t('microSpecialtyProposal.syncHoursButton') }}
              </el-button>
              <el-button size="small" @click="dismissCourseIssues">{{ $t('microSpecialtyProposal.dismissIssues') }}</el-button>
              <span class="course-issue-hint">{{ $t('microSpecialtyProposal.issueHint') }}</span>
            </div>
          </template>
        </el-alert>

        <div class="table-section">
          <div class="table-summary" v-if="totalCourseHours > 0">
            {{ $t('microSpecialtyProposal.totalHours') }}<strong>{{ totalCourseHours }}</strong>
          </div>
          <CourseChapterEditor v-model="courses" @change="onCourseChange" />
        </div>
      </el-form>
    </el-card>

    <!-- ========== 模块3：教学团队 ========== -->
    <el-card v-if="step === 2" shadow="never" class="proposal-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ $t('microSpecialtyProposal.module3Title') }}</span>
          <el-button type="warning" plain size="small" @click="handleResetModule('teamMembers')">{{ $t('microSpecialtyProposal.resetModule') }}</el-button>
        </div>
      </template>
      <el-form :model="form" :rules="rules3" label-width="110px" class="proposal-form">
        <el-divider content-position="left">{{ $t('microSpecialtyProposal.professionalLeader') }}</el-divider>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item :label="$t('microSpecialtyProposal.name')">
              <el-input v-model="form.leadName" :placeholder="$t('microSpecialtyProposal.leaderNamePlaceholder')" maxlength="50" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$t('microSpecialtyProposal.title')">
              <el-select v-model="form.leadTitle" class="full-width" filterable allow-create :placeholder="$t('microSpecialtyProposal.titlePlaceholder')">
                <el-option v-for="opt in titleOptions" :key="opt" :label="titleLabel(opt)" :value="opt" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$t('microSpecialtyProposal.position')">
              <el-input v-model="form.leadPosition" :placeholder="$t('microSpecialtyProposal.positionPlaceholder')" maxlength="50" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item :label="$t('microSpecialtyProposal.contactPhone')" prop="leadPhone">
              <el-input v-model="form.leadPhone" :placeholder="$t('microSpecialtyProposal.leaderPhonePlaceholder')" maxlength="11" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$t('microSpecialtyProposal.researchDirection')">
              <el-input v-model="form.leadResearchDirection" :placeholder="$t('microSpecialtyProposal.researchDirection')" maxlength="200" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item :label="$t('microSpecialtyProposal.mainCourses')">
              <el-input v-model="form.leadMainTasks" :placeholder="$t('microSpecialtyProposal.mainCourses')" maxlength="300" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">{{ $t('microSpecialtyProposal.threeYearCourses') }}</el-divider>
        <DynamicTableEditor v-model="leadCourses" :columns="leadCourseColumns" :default-row="leadCourseDefaultRow" :max-rows="5" />

        <el-divider content-position="left">{{ $t('microSpecialtyProposal.teamMembers') }}</el-divider>
        <DynamicTableEditor v-model="teamMembers" :columns="teamColumns" :default-row="teamDefaultRow" />
      </el-form>

      <!-- Phase 2: 章节分配 -->
      <el-divider content-position="left">{{ $t('microSpecialtyProposal.chapterAssignment') }}</el-divider>
      <div v-if="teamMembers.length === 0" class="empty-hint">
        {{ $t('microSpecialtyProposal.addTeamFirstHint') }}
      </div>
      <el-table v-else :data="teamMembers" border size="small">
        <el-table-column :label="$t('microSpecialtyProposal.name')" width="120">
          <template #default="{ row }">{{ row.name || $t('microSpecialtyProposal.unnamed') }}</template>
        </el-table-column>
        <el-table-column :label="$t('microSpecialtyProposal.assignedChapters')" min-width="200">
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
        <el-table-column :label="$t('app.operation')" width="120">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openChapterDrawer(row)">
              {{ $t('microSpecialtyProposal.assignChapters') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 章节分配 Drawer -->
      <el-drawer v-model="chapterDrawerVisible" :title="$t('microSpecialtyProposal.assignChapters')" direction="rtl" size="50%">
        <template v-if="chapterDrawerMember">
          <el-alert :title="$t('microSpecialtyProposal.assignChapterFor', { name: chapterDrawerMember.name || $t('microSpecialtyProposal.unnamed') })" type="info" :closable="false" show-icon class="mg-bottom-16" />

          <el-collapse v-if="courses.length > 0">
            <el-collapse-item v-for="course in courses" :key="course._index || course.id" :title="course.courseName || $t('microSpecialtyProposal.unnamedCourse')">
              <el-checkbox-group v-if="course.chapters && course.chapters.length">
                <div v-for="ch in course.chapters" :key="ch.id" class="chapter-check-item">
                  <el-checkbox
                    :model-value="isChapterAssigned(chapterDrawerMember, ch.id)"
                    :label="$t('microSpecialtyProposal.chapterWithHours', { title: ch.title, hours: (ch.hours || 0) })"
                    @change="(val) => toggleChapter(chapterDrawerMember, ch.id, course.id, val)" />
                </div>
              </el-checkbox-group>
              <div v-else class="empty-hint">{{ $t('microSpecialtyProposal.noChaptersInCourse') }}</div>
            </el-collapse-item>
          </el-collapse>
          <div v-else class="empty-hint">{{ $t('microSpecialtyProposal.noCoursesHint') }}</div>
        </template>
        <template #footer>
          <el-button @click="chapterDrawerVisible = false">{{ $t('common.close') }}</el-button>
          <el-button type="primary" @click="chapterDrawerVisible = false">{{ $t('app.finish') }}</el-button>
        </template>
      </el-drawer>
    </el-card>

    <!-- ========== 模块4：牵头单位意见 ========== -->
    <el-card v-if="step === 3" shadow="never" class="proposal-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ $t('microSpecialtyProposal.module4Title') }}</span>
          <el-button type="warning" plain size="small" @click="handleResetModule('signatures')">{{ $t('microSpecialtyProposal.resetModule') }}</el-button>
        </div>
      </template>
      <SignatureBlock :title="$t('microSpecialtyProposal.signatureLeaderOpinion')" v-model="signatures[0]" :signature-uploader="makeUploader('SIGNATURE')" :seal-uploader="makeUploader('SEAL')" />
      <SignatureBlock :title="$t('microSpecialtyProposal.signatureDeptOpinion')" v-model="signatures[1]" :signature-uploader="makeUploader('SIGNATURE')" :seal-uploader="makeUploader('SEAL')" />
      <SignatureBlock :title="$t('microSpecialtyProposal.signatureSchoolOpinion')" v-model="signatures[2]" :signature-uploader="makeUploader('SIGNATURE')" :seal-uploader="makeUploader('SEAL')" />
    </el-card>

    <!-- ========== 模块5：共建共享单位 ========== -->
    <el-card v-if="step === 4" shadow="never" class="proposal-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ $t('microSpecialtyProposal.module5Title') }}</span>
          <el-button type="warning" plain size="small" @click="handleResetModule('sharedUnits')">{{ $t('microSpecialtyProposal.resetModule') }}</el-button>
        </div>
      </template>
      <div v-if="sharedUnits.length === 0" class="empty-hint">
        {{ $t('microSpecialtyProposal.noSharedUnits') }}
      </div>
      <div v-for="(unit, idx) in sharedUnits" :key="idx" class="shared-unit-block">
        <div class="unit-header">
          <span class="unit-label">{{ $t('microSpecialtyProposal.unitNumber', { n: idx + 1 }) }}</span>
          <el-button type="danger" size="small" link @click="removeSharedUnit(idx)">{{ $t('app.delete') }}</el-button>
        </div>
        <el-form :model="unit" label-width="100px" size="small">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item :label="$t('microSpecialtyProposal.unitName')">
                <el-input v-model="unit.unitName" :placeholder="$t('microSpecialtyProposal.unitNamePlaceholder')" maxlength="100" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item :label="$t('microSpecialtyProposal.unitType')">
                <el-select v-model="unit.unitType" class="full-width">
                  <el-option v-for="ut in unitTypeOptions" :key="ut.value" :label="unitTypeLabel(ut.value)" :value="ut.value" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item :label="$t('microSpecialtyProposal.opinion')">
            <el-input v-model="unit.opinionText" type="textarea" :rows="2" :placeholder="$t('microSpecialtyProposal.opinionPlaceholder')" />
          </el-form-item>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item :label="$t('microSpecialtyProposal.leaderSignature')">
                <SignatureUploader
                  :label="$t('microSpecialtyProposal.signature')"
                  :image-url="unit.signatureImageUrl"
                  :uploader="makeUploader('SHARED_SIGNATURE')"
                  @update:image-url="val => { unit.signatureImageUrl = val }"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item :label="$t('microSpecialtyProposal.seal')">
                <SignatureUploader
                  :label="$t('microSpecialtyProposal.seal')"
                  :image-url="unit.sealImageUrl"
                  :uploader="makeUploader('SHARED_SEAL')"
                  @update:image-url="val => { unit.sealImageUrl = val }"
                />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item :label="$t('course.date')">
                <DatePickerYM v-model="unit.signDate" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item :label="$t('course.remark')">
                <el-input v-model="unit.remark" :placeholder="$t('microSpecialtyProposal.remarkPlaceholder')" maxlength="200" />
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
      </div>
      <div class="add-unit-bar">
        <el-button type="primary" plain size="small" @click="addSharedUnit">{{ $t('microSpecialtyProposal.addSharedUnit') }}</el-button>
      </div>
    </el-card>

    <!-- ========== 底部操作栏 ========== -->
    <div class="footer-bar">
      <el-button @click="handleBack">{{ $t('app.back') }}</el-button>
      <el-button :loading="saving" @click="handleSave">{{ $t('app.save') }}</el-button>
      <el-button type="primary" :disabled="!formComplete" :loading="submitting" @click="handleSubmit">{{ $t('course.submitForReview') }}</el-button>
      <el-button type="danger" plain @click="handleResetAll">{{ $t('microSpecialtyProposal.resetAll') }}</el-button>
    </div>
  </div>  <!-- closes ms-proposal-page -->
</div>  <!-- closes v-if="!loadError" wrapper -->

  <!-- 分步导航按钮 -->
  <div v-if="!loadError" class="step-nav">
    <el-button v-if="step > 0" @click="step--">{{ $t('app.prev') }}</el-button>
    <el-button v-if="step < 4" type="primary" @click="handleNextStep">{{ $t('app.next') }}</el-button>
    <template v-if="step === 4">
      <el-button :loading="saving" @click="handleSave">{{ $t('app.save') }}</el-button>
      <el-button type="primary" :disabled="!formComplete" :loading="submitting" @click="handleSubmit">{{ $t('course.submitForReview') }}</el-button>
    </template>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
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
import { validateCourseList, autoFixCourseHours } from '@/utils/courseValidation'
import SignatureUploader from '@/components/storage/SignatureUploader.vue'
import DatePickerYM from '@/components/storage/DatePickerYM.vue'
import { useUserStore } from '@/store/user'

const { t } = useI18n()
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
const autoSaveAbortController = ref(null)  // C-007: AbortController 用于取消 in-flight autoSave
const formRef1 = ref(null)
const formRef2 = ref(null)

// P1-C 修复 (2026-08-04): "下一步"原直接 step++ 绕过表单校验，
// 第一步必填项（申报高校/微专业名称/负责人/电话/申请时间）全空也能进入后续步骤，
// 用户填完 5 步才发现第一步为空 → 流程校验失效。
const handleNextStep = async () => {
  try {
    if (step.value === 0 && formRef1.value) {
      await formRef1.value.validate()
    }
    if (step.value === 1 && formRef2.value) {
      await formRef2.value.validate()
    }
    step.value++
  } catch {
    // 校验失败，停留在当前步骤并展示错误提示
    ElMessage.warning(t('microSpecialtyProposal.completeCurrentStep'))
  }
}
const loadError = ref(false)  // P1-C-13: 全局加载错误标志
const initialLoadComplete = ref(false)
const autoSaveEnabled = ref(false)
const leavingConfirmed = ref(false)  /* 防 route guard 双重确认 */

// 保存状态错误判断（与 saveStatus 的 i18n 文案比较，双语言均生效）
const isSaveError = computed(() => {
  const s = saveStatus.value
  return s === t('microSpecialtyProposal.saveStatusFailed') ||
    s === t('microSpecialtyProposal.saveStatusFailedWarn') ||
    s === t('microSpecialtyProposal.saveStatusUnsavedWarn')
})

// 附件标题（只读）
const attachmentTitle = computed(() => {
  const name = form.value.microSpecialtyName || t('microSpecialtyProposal.unnamedPlain')
  return t('microSpecialtyProposal.attachmentTitleFormat', { name })
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
    // V202 P0-2 修复: 不要在 chapterAssignments 里写 teacherId 占位
    // 后端 StorageApplicationCudServiceImpl V202 现在对 null teacherId 合法
    // 当教师尚未绑定时,chapter.teacher_id = NULL + acceptStatus='PENDING'
    // 当教师正式邀请后(走 inviteTeacher 流程),会在 MicroSpecialtyServiceImpl
    // 的第 554-562 行单独插入新行,绑定真实 teacherId
    chapterAssignments.value.push({
      courseId,
      chapterId,
      teamMemberIndex: memberIndex,
      source: 'TBD',
      acceptStatus: 'PENDING'
      // teacherId 故意不设,避免 memberIndex+1 占位污染
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
  return t('microSpecialtyProposal.unknownChapter')
}
async function removeChapterAssign(assign, memberIndex) {
  try {
    await ElMessageBox.confirm(t('microSpecialtyProposal.confirmRemoveAssignment'), t('microSpecialtyProposal.confirmRemoveTitle'), {
      type: 'warning', confirmButtonText: t('microSpecialtyProposal.remove'), cancelButtonText: t('common.cancel')
    })
    chapterAssignments.value = chapterAssignments.value.filter(a =>
      !(a.chapterId === assign.chapterId && a.teamMemberIndex === memberIndex))
    ElMessage.success(t('microSpecialtyProposal.assignmentRemoved'))
  } catch {}
}

// ==================== 下拉选项 ====================
// TODO: 从后端配置接口动态获取类型选项
// 注意：value 保持中文枚举值（后端契约），label 经 i18n 翻译后展示
const typeOptions = ['急需紧缺型', '学科交叉型', '产教融合型']
const audienceOptions = ['专科', '本科', '硕士', '博士']
const titleOptions = ['教授', '副教授', '讲师', '助教', '企业导师']
const unitTypeOptions = [
  { value: 'CO_BUILD_UNIV', label: '共建高校' },
  { value: 'ENTERPRISE', label: '合作企业' },
  { value: 'SHARE_UNIV', label: '拟共享高校' }  // P1-C-5 修复：与 UnitType 枚举一致
]

function typeLabel(val) {
  return ({
    '急需紧缺型': t('microSpecialtyProposal.typeUrgent'),
    '学科交叉型': t('microSpecialtyProposal.typeInterdisciplinary'),
    '产教融合型': t('microSpecialtyProposal.typeIndustryAcademic')
  })[val] || val
}

function audienceLabel(val) {
  return ({
    '专科': t('microSpecialtyProposal.audienceJunior'),
    '本科': t('microSpecialtyProposal.audienceBachelor'),
    '硕士': t('microSpecialtyProposal.audienceMaster'),
    '博士': t('microSpecialtyProposal.audienceDoctor')
  })[val] || val
}

function titleLabel(val) {
  return ({
    '教授': t('microSpecialtyProposal.titleProfessor'),
    '副教授': t('microSpecialtyProposal.titleAssociateProfessor'),
    '讲师': t('microSpecialtyProposal.titleLecturer'),
    '助教': t('microSpecialtyProposal.titleAssistant'),
    '企业导师': t('microSpecialtyProposal.titleEnterpriseMentor')
  })[val] || val
}

function unitTypeLabel(val) {
  return ({
    'CO_BUILD_UNIV': t('microSpecialtyProposal.coBuildUniversities'),
    'ENTERPRISE': t('microSpecialtyProposal.coopEnterprise'),
    'SHARE_UNIV': t('microSpecialtyProposal.plannedShareUniversities')
  })[val] || val
}

// ==================== 表格列配置 ====================
const courseColumns = [
  { prop: 'moduleName', label: '模块', type: 'text', placeholder: '如：专业基础模块' },
  { prop: 'courseName', label: '课程名称', type: 'text', minWidth: '180', placeholder: '必填' },
  { prop: 'hours', label: '学时', type: 'number', width: '80', min: 1 },
  { prop: 'credits', label: '学分', type: 'number', width: '80', min: 0.5 },
  { prop: 'semester', label: '开课学期', type: 'text', placeholder: '如：第1学期' }
]
const courseDefaultRow = { moduleName: '', courseName: '', hours: null, credits: null, semester: '', chapters: [] }

const leadCourseColumns = computed(() => [
  { prop: 'courseName', label: t('microSpecialtyProposal.courseName'), type: 'text', minWidth: '180', placeholder: t('microSpecialtyProposal.required') },
  { prop: 'credits', label: t('microSpecialtyProposal.credits'), type: 'number', width: '80', min: 0.5 },
  { prop: 'hours', label: t('microSpecialtyProposal.hours'), type: 'number', width: '80', min: 1 }
])
const leadCourseDefaultRow = { courseName: '', credits: null, hours: null }

const teamColumns = computed(() => [
  { prop: 'name', label: t('microSpecialtyProposal.name'), type: 'text', placeholder: t('microSpecialtyProposal.required'), minWidth: '100' },
  { prop: 'age', label: t('microSpecialtyProposal.age'), type: 'number', width: '70', min: 18, max: 70 },
  { prop: 'title', label: t('microSpecialtyProposal.title'), type: 'text', placeholder: t('microSpecialtyProposal.titlePlaceholderHint') },
  { prop: 'organization', label: t('microSpecialtyProposal.organization'), type: 'text', minWidth: '150' },
  { prop: 'profession', label: t('microSpecialtyProposal.profession'), type: 'text', minWidth: '150' },
  { prop: 'taughtCourses', label: t('microSpecialtyProposal.taughtCourses'), type: 'text', minWidth: '150' },
  { prop: 'plannedCourses', label: t('microSpecialtyProposal.plannedCourses'), type: 'text', minWidth: '150' }
])
const teamDefaultRow = { name: '', age: null, title: '', organization: '', profession: '', taughtCourses: '', plannedCourses: '' }

// ==================== 表单校验 ====================
const rules = computed(() => ({
  title: [{ required: true, message: t('microSpecialtyProposal.requiredUniversityName'), trigger: 'blur' }],
  microSpecialtyName: [{ required: true, message: t('microSpecialtyProposal.requiredSpecialtyName'), trigger: 'blur' }],
  leadName: [{ required: true, message: t('microSpecialtyProposal.requiredLeader'), trigger: 'blur' }],
  contactPhone: [
    { required: true, message: t('microSpecialtyProposal.requiredContactPhone'), trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: t('microSpecialtyProposal.validPhone11'), trigger: 'blur' }
  ],
  applyDate: [{ required: true, message: t('microSpecialtyProposal.requiredApplyDate'), trigger: 'change' }]
}))

// ==================== 模块3 表单校验 (P2-A) ====================
const rules3 = computed(() => ({
  leadPhone: [
    { pattern: /^1[3-9]\d{9}$/, message: t('microSpecialtyProposal.validPhone11'), trigger: 'blur' }
  ]
}))

// ==================== 模块2 表单校验 (P1-C) ====================
const rules2 = computed(() => ({
  type: [{ required: true, message: t('microSpecialtyProposal.requiredType'), trigger: 'change' }],
  targetAudience: [{ type: 'array', required: true, min: 1, message: t('microSpecialtyProposal.requiredAudience'), trigger: 'change' }],
  targetDisciplines: [{ required: true, message: t('microSpecialtyProposal.requiredDisciplines'), trigger: 'blur' }]
}))

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

// ==================== 课程学时校验（三档呈现） ====================
// Tier3 顶部 banner + Tier2 段头 badge 共用此数据
// 保留 console.warn 兜底（开发态还可看到）
const courseValidation = computed(() => validateCourseList(courses.value))
watch(courseValidation, (v) => {
  if (v.issueList.length > 0) {
    for (const issue of v.issueList) {
      for (const det of issue.issues) {
        const tag = det.severity === 'error' ? '⛔' : '⚠️'
        console.warn(`${tag} [${det.key}] 课程「${issue.courseName}」${det.message}`)
      }
    }
  }
})

// 用户可关闭本次提示（提交后自动重置）
const courseIssueDismissed = ref(false)
watch(() => courses.value.length, () => { courseIssueDismissed.value = false })

function fixCourseHoursToChapterSum() {
  const n = autoFixCourseHours(courses.value)
  if (n > 0) {
    ElMessage.success(t('microSpecialtyProposal.autoSyncSuccess', { count: n }))
    courseIssueDismissed.value = false
  } else {
    ElMessage.info(t('microSpecialtyProposal.noFixNeeded'))
  }
}

function dismissCourseIssues() { courseIssueDismissed.value = true }

const showCourseIssueAlert = computed(() =>
  !courseIssueDismissed.value && courseValidation.value.worstLevel !== 'ok'
)

// 表单是否完整(用于禁用"提交审核"按钮,防止误点)
// B-003: 添加行级校验 — 团队成员姓名必填、课程名称必填、签名至少有一个非空
const formComplete = computed(() => {
  return !!(
    form.value.title &&
    form.value.microSpecialtyName &&
    form.value.leadName &&
    form.value.contactPhone &&
    /^1[3-9]\d{9}$/.test(form.value.contactPhone) &&
    form.value.applyDate &&
    courses.value.length > 0 &&
    courses.value.every(c => c.courseName?.trim()) &&
    teamMembers.value.length > 0 &&
    teamMembers.value.every(m => m.name?.trim()) &&
    signatures.value.some(s => s.opinionText?.trim())
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
    sharedUnits: sharedUnits.value.map(u => ({
      ...u,
      // P1-2026-08-21: 后端 ProposalSharedUnitItem 只接受嵌套 signature/seal(SignatureFile)，
      // 顶层 signatureImageUrl/sealImageUrl 会被 Jackson 丢弃 → 签名/盖章图片保存即丢失
      signature: u.signatureImageUrl ? { type: 'IMAGE', text: '', imageUrl: u.signatureImageUrl } : (u.signature || { type: 'TEXT', text: '', imageUrl: '' }),
      seal: u.sealImageUrl ? { type: 'IMAGE', text: '', imageUrl: u.sealImageUrl } : (u.seal || { type: 'TEXT', text: '', imageUrl: '' })
    })),
    chapterAssignments: chapterAssignments.value.map(a => {
      // V202 P0-2 修复: 不传 teacherId 字段,让后端用 NULL 写入
      // 即使前端 toggleChapter 误设了 teacherId 旧值,这里也强制清掉
      // 审计 2026-08-14 修复: teamMemberIndex 必须保留,否则章节-教师分配无法持久化
      return {
        courseId: a.courseId,
        chapterId: a.chapterId,
        // P1-2026-08-21: 字段与后端 DTO 对齐(memberIndex), 否则保存即丢失、重载全归第1位成员
        memberIndex: a.teamMemberIndex,
        source: a.source || 'TBD',
        acceptStatus: a.acceptStatus || 'PENDING'
        // teacherId 故意不传
      }
    })
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

async function removeSharedUnit(index) {
  try {
    await ElMessageBox.confirm(t('microSpecialtyProposal.confirmRemoveUnit'), t('microSpecialtyProposal.confirmRemoveTitle'), {
      type: 'warning', confirmButtonText: t('microSpecialtyProposal.remove'), cancelButtonText: t('common.cancel')
    })
    sharedUnits.value.splice(index, 1)
    ElMessage.success(t('microSpecialtyProposal.unitRemoved'))
  } catch {}
}

// ==================== 上传工厂 ====================
// P1-UX: 返回符合 SignatureUploader 期望的 (file, onProgress) => Promise<{url}> 函数
// 让组件可以真实上传到后端并接收 0-100% 进度
function makeUploader(type) {
  return async (file, onProgress) => {
    if (!draftId.value) throw new Error(t('microSpecialtyProposal.draftNotReady'))
    return await uploadStorageImage(draftId.value, file, type, onProgress)
  }
}

// ==================== 保存 ====================
async function handleSave() {
  // 关键：先 validate，再 ensureDraft。否则校验失败会留下孤儿空白 DRAFT
  // formRef1 仅在 step === 0 时存在；自动跳到 Step 0 后再校验，避免绕过
  if (step.value !== 0) {
    step.value = 0
    await nextTick()
  }
  if (!formRef1.value) {
    ElMessage.warning(t('microSpecialtyProposal.goStep1First'))
    return
  }
  try {
    await formRef1.value.validate()
  } catch {
    ElMessage.warning(t('microSpecialtyProposal.completeRequiredBeforeSave'))
    return
  }
  // D-009: 检查共享单位中是否有暂不支持的字段
  const hasUnsupportedFields = sharedUnits.value.some(u =>
    u.opinionText || u.signatureImageUrl || u.sealImageUrl || u.signDate
  )
  if (hasUnsupportedFields) {
    ElMessage.info(t('microSpecialtyProposal.sharedUnitFieldWarning'))
  }
  // 懒创建：用户手动点保存时若草稿未存在, 先创建
  if (!draftId.value) {
    try {
      await ensureDraft()
    } catch (e) {
      ElMessage.error(e?.response?.data?.message || t('microSpecialtyProposal.initDraftFailed'))
      return
    }
    if (!draftId.value) return  // ensureDraft 失败
  }
  saving.value = true
  try {
    await saveStorageApplication(draftId.value, buildSavePayload())
    saveStatus.value = t('microSpecialtyProposal.saveStatusSaved', { time: new Date().toLocaleTimeString() })
    dirty.value = false  // 仅在 save 成功后清除 dirty（避免数据丢失）
    autoSaveEnabled.value = true  /* 手动保存成功后启用 autoSave */
    ElMessage.success(t('course.saveSuccess'))
  } catch (e) {
    // 关键：保留 dirty=true，让用户在路由离开/刷新时被警告，避免数据丢失
    saveStatus.value = t('microSpecialtyProposal.saveStatusFailed')
    ElMessage.error(e?.response?.data?.message || t('microSpecialtyProposal.saveStatusFailed'))
  } finally {
    saving.value = false
  }
}

// ==================== 自动保存（1.5s 防抖） ====================
const autoSaveTimer = ref(null)

// 草稿懒创建：仅在用户首次输入/手动保存时才 POST /init
// 避免"进入表单未填返回"产生空白 DRAFT 污染列表
let draftInitInFlight = null
async function ensureDraft() {
  if (draftId.value) return
  if (draftInitInFlight) return draftInitInFlight
  draftInitInFlight = (async () => {
    try {
      await initDraft()
    } finally {
      draftInitInFlight = null
    }
  })()
  return draftInitInFlight
}

// 实际执行一次保存（被 scheduleAutoSave 和 handleSave 共用）
async function performAutoSave() {
  // C-007: 取消上一个 in-flight 请求（防止旧请求覆盖新保存结果）
  if (autoSaveAbortController.value) {
    autoSaveAbortController.value.abort()
  }
  autoSaveAbortController.value = new AbortController()

  pendingSave.value = true
  saveStatus.value = t('microSpecialtyProposal.saveStatusSaving')
  try {
    const res = await autoSaveStorageApplication(draftId.value, buildSavePayload(), {
      signal: autoSaveAbortController.value.signal
    })
    // P1-UX: 使用服务器时间戳显示"已保存 HH:MM:SS"，避免客户端时钟偏差
    const serverTime = res?.data?.serverTime
    const displayTime = serverTime ? new Date(serverTime).toLocaleTimeString() : new Date().toLocaleTimeString()
    saveStatus.value = t('microSpecialtyProposal.saveStatusSaved', { time: displayTime })
    dirty.value = false  // 仅成功时清除 dirty；失败保留让用户被警告，避免数据丢失
  } catch (e) {
    // C-007: AbortError 静默跳过（取消旧请求或组件卸载时触发）
    if (e.name === 'AbortError' || e.code === 'ERR_CANCELED') return
    saveStatus.value = t('microSpecialtyProposal.saveStatusFailedWarn')
    ElMessage.error(e?.response?.data?.message || t('microSpecialtyProposal.autoSaveFailed'))
  } finally {
    pendingSave.value = false
    autoSaveAbortController.value = null
  }
}

/**
 * Extracted auto-save scheduler — shared by all targeted watchers.
 * Debounces: resets the timer on each call, only fires after 1.5s of inactivity.
 * 懒创建：第一次输入时先 ensureDraft() 再 autoSave
 */
function scheduleAutoSave() {
  if (!initialLoadComplete.value) return
  dirty.value = true  /* 先标记 dirty（即使 autoSave 未启用也能触发离开警告） */
  // 草稿未创建：懒创建，确保 draftId 后立即跑一次保存
  if (!draftId.value) {
    if (draftInitInFlight || !initialLoadComplete.value) return
    ensureDraft().then(() => {
      if (!draftId.value) return  // init 失败
      autoSaveEnabled.value = true  /* 首次创建即启用 autoSave */
      performAutoSave()
    }).catch(() => {
      saveStatus.value = t('microSpecialtyProposal.saveStatusFailedWarn')
    })
    return
  }
  if (!autoSaveEnabled.value) return
  if (autoSaveTimer.value) clearTimeout(autoSaveTimer.value)
  autoSaveTimer.value = setTimeout(performAutoSave, 1500)
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

// Consolidated: merge 10 individual watches into 1 (PERF-015/016)
watch(() => [
  form.value.introduction?.length,
  form.value.marketDemandAnalysis?.length,
  form.value.specialtyOverview?.length,
  form.value.curriculumDesign?.length,
  form.value.constructionGuarantee?.length,
  JSON.stringify(courses.value),
  JSON.stringify(leadCourses.value),
  JSON.stringify(teamMembers.value),
  JSON.stringify(signatures.value),
  JSON.stringify(sharedUnits.value),
], scheduleAutoSave)

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
    ElMessage.warning(t('microSpecialtyProposal.draftNotInitialized'))
    return
  }
  // P2-G: 业务级校验 — 课程表至少1行
  if (courses.value.length === 0) {
    ElMessage.warning(t('microSpecialtyProposal.atLeastOneCourse'))
    return
  }
  // P2-G: 业务级校验 — 教学团队至少1人
  if (teamMembers.value.length === 0) {
    ElMessage.warning(t('microSpecialtyProposal.atLeastOneMember'))
    return
  }
  // P1-C-11 修复：增加程序化表单校验
  // 跨步骤校验：formRef1 仅 step===0 时挂载，自动跳到 Step 0 再校验
  if (step.value !== 0) {
    step.value = 0
    await nextTick()
  }
  if (!formRef1.value) {
    ElMessage.warning(t('microSpecialtyProposal.goStep1First'))
    return
  }
  try {
    await formRef1.value.validate()
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
    ElMessage.warning(t('microSpecialtyProposal.completeRequiredBeforeSubmit'))
    return
  }
  // 模块2 校验（P1-C）: type / targetAudience / targetDisciplines 必填
  if (step.value !== 1) {
    step.value = 1
    await nextTick()
  }
  if (formRef2.value) {
    try {
      await formRef2.value.validate()
    } catch (errors) {
      const firstErrorField = Object.keys(errors || {})[0]
      if (firstErrorField) {
        const el = document.querySelector(`[prop="${firstErrorField}"]`)
        if (el) {
          el.scrollIntoView({ behavior: 'smooth', block: 'center' })
          const input = el.querySelector('input, textarea, select')
          if (input) input.focus()
        }
      }
      ElMessage.warning(t('microSpecialtyProposal.completeModule2Required'))
      return
    }
  }
  try {
    await ElMessageBox.confirm(t('microSpecialtyProposal.confirmSubmitMsg'), t('microSpecialtyProposal.confirmSubmitTitle'), {
      confirmButtonText: t('course.dialogConfirm'),
      cancelButtonText: t('common.cancel'),
      type: 'warning'
    })
  } catch {
    return
  }
  submitting.value = true
  try {
    await submitStorageApplication(draftId.value)
    ElMessage.success(t('microSpecialtyProposal.submitSuccess'))
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
    ElMessage.error(errorData?.message || t('microSpecialtyProposal.submitFailed'))
  } finally {
    submitting.value = false
  }
}

// ==================== 重置模块 ====================
async function handleResetModule(moduleName) {
  if (!draftId.value) return
  try {
    await ElMessageBox.confirm(t('microSpecialtyProposal.confirmResetModuleMsg', { module: moduleName }), t('microSpecialtyProposal.confirmResetTitle'), { type: 'warning' })
  } catch {
    return
  }
  try {
    await resetStorageModule(draftId.value, moduleName)
    ElMessage.success(t('microSpecialtyProposal.moduleReset'))
    await loadDraft(draftId.value)
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || t('microSpecialtyProposal.resetFailed'))
  }
}

// ==================== 重置全部 ====================
async function handleResetAll() {
  if (!draftId.value) return
  try {
    await ElMessageBox.confirm(t('microSpecialtyProposal.confirmResetAllMsg'), t('microSpecialtyProposal.confirmResetAllTitle'), {
      confirmButtonText: t('microSpecialtyProposal.confirmResetAllBtn'),
      cancelButtonText: t('common.cancel'),
      type: 'error'
    })
  } catch {
    return
  }
  try {
    await resetStorageAll(draftId.value)
    ElMessage.success(t('microSpecialtyProposal.resetAllDone'))
    await loadDraft(draftId.value)
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || t('microSpecialtyProposal.resetFailed'))
  }
}

// ==================== 导出 ====================
async function handleExport(type) {
  if (!draftId.value) {
    ElMessage.warning(t('microSpecialtyProposal.draftNotInitialized'))
    return
  }
  const fn = type === 'word' ? exportStorageWord : exportStoragePdf
  try {
    const res = await fn(draftId.value)
    // B4 fix: check if response is actually a JSON error disguised as blob
    if (res.data && res.data.type === 'application/json') {
      const text = await new Response(res.data).text()
      const err = JSON.parse(text)
      ElMessage.error(err.message || t('microSpecialtyProposal.exportValidateFailed'))
      return
    }
    let blob
    if (res.data instanceof Blob) {
      blob = res.data
    } else {
      blob = new Blob([res.data], { type: 'application/octet-stream' })
    }
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    const ext = type === 'word' ? 'docx' : 'pdf'
    const schoolName = form.value.title || t('microSpecialtyProposal.applyingUniversity')
    const date = new Date().toISOString().slice(0, 10).replace(/-/g, '')
    a.download = `【${schoolName}】${t('microSpecialtyProposal.pageTitle')}_${date}.${ext}`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success(t('microSpecialtyProposal.exportSuccess', { format: type === 'word' ? 'Word' : 'PDF' }))
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || t('microSpecialtyProposal.exportFailed'))
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
      // P1-2026-08-21: 嵌套 signature/seal → 顶层 signatureImageUrl/sealImageUrl（与上传组件绑定字段一致）
      sharedUnits.value = (data.sharedUnits || []).map(u => ({
        ...u,
        signatureImageUrl: u.signature?.imageUrl || u.signatureImageUrl || '',
        sealImageUrl: u.seal?.imageUrl || u.sealImageUrl || ''
      }))
      // 加载章节分配
      if (data.chapterAssignments) {
        // P1-2026-08-21: 优先用后端持久化的 memberIndex(占位条目 teacherId=null 时旧映射全部归第1位成员)
        chapterAssignments.value = data.chapterAssignments.map(a => ({
          ...a, teamMemberIndex: a.memberIndex ?? ((a.teacherId || 1) - 1)
        }))
      }
    }
    draftId.value = id
    dirty.value = false
    saveStatus.value = ''
    initialLoadComplete.value = true
  } catch (e) {
    loadError.value = true  // P1-C-13: 显示错误状态
    ElMessage.error(e?.response?.data?.message || t('microSpecialtyProposal.loadDraftFailed'))
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
    ElMessage.error(e?.response?.data?.message || t('microSpecialtyProposal.initDraftFailed'))
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
    ElMessageBox.confirm(t('microSpecialtyProposal.unsavedChangesMsg'), t('microSpecialtyProposal.unsavedChangesTitle'), { type: 'warning' })
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
    e.returnValue = t('microSpecialtyProposal.unsavedDataMsg')
  }
}

// ==================== 路由离开守卫 ====================
onBeforeRouteLeave((to, from, next) => {
  if (dirty.value && !leavingConfirmed.value) {
    ElMessageBox.confirm(t('microSpecialtyProposal.unsavedChangesMsg'), t('microSpecialtyProposal.unsavedChangesTitle'), { type: 'warning' })
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
  }
  // 无 id：不清创建草稿，避免"进入未填返回"产生空白 DRAFT
  // 等用户首次输入时由 scheduleAutoSave / handleSave 懒调用 ensureDraft()
  initialLoadComplete.value = true
  loading.value = false
})

// ==================== 清理 ====================
onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', handleBeforeUnload)  // RT-3: 移除标签页关闭警告
  if (autoSaveTimer.value) {
    clearTimeout(autoSaveTimer.value)
  }
  // C-007: 取消正在执行的 autoSave 请求（避免组件卸载后回调更新已销毁的响应式状态）
  if (autoSaveAbortController.value) {
    autoSaveAbortController.value.abort()
    autoSaveAbortController.value = null
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

/* 课程学时校验 banner */
.course-hours-alert {
  margin: 8px 0 16px;
}
.course-issue-list {
  margin: 4px 0 8px;
  padding-left: 20px;
}
.course-issue-list li {
  line-height: 1.8;
  font-size: 13px;
}
.course-issue-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 4px;
  flex-wrap: wrap;
}
.course-issue-hint {
  color: #909399;
  font-size: 12px;
}
.issue-ok { color: #67c23a; }
.issue-warn { color: #e6a23c; }
.issue-error { color: #f56c6c; }
.course-issue-badge {
  margin-left: 8px;
  vertical-align: middle;
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
  max-width: 100%;
  min-width: 0;
  overflow-x: auto;
}
.step-nav {
  display: flex;
  justify-content: center;
  gap: 16px;
  padding: 24px 0;
}
</style>
