<!--
  UserSearchBar — 用户列表搜索/筛选栏
  可同时用于 admin/users 和 users/ 两个列表页

  Props:
    modelValue: 搜索表单对象（含 keyword, role, status, departmentId, majorId, classId）
    departments: 院系选项数组
    majors: 专业选项数组（departments 筛选后的）
    classes: 班级选项数组（majors 筛选后的）
    showDeptCascade: 是否显示院系/专业/班级级联筛选（默认 false）

  Events:
    update:modelValue — 表单值变更
    search — 点击搜索/回车
    reset — 点击重置
    department-change — 院系切换
    major-change — 专业切换
-->
<template>
  <!-- eslint-disable vue/no-mutating-props -->
  <!-- modelValue 是响应式对象，v-model 嵌套属性是 Element Plus 标准表单模式 -->
  <el-card class="search-card" shadow="never">
    <el-form :inline="true" :model="modelValue" @submit.prevent>
      <el-form-item :label="$t('userSearch.keyword')">
        <el-input
          v-model="modelValue.keyword"
          :placeholder="$t('userSearch.placeholder')"
          clearable
          class="filter-input"
          @clear="$emit('search')"
          @keyup.enter="$emit('search')"
        />
      </el-form-item>
      <el-form-item :label="$t('userSearch.role')">
        <el-select
          v-model="modelValue.role"
          :placeholder="$t('userSearch.pleaseSelect')"
          clearable
          class="filter-select"
          @change="debouncedEmit('search')"
        >
          <el-option :label="$t('userSearch.student')" value="STUDENT" />
          <el-option :label="$t('userSearch.teacher')" value="TEACHER" />
          <el-option :label="$t('userSearch.admin')" value="ADMIN" />
          <el-option :label="$t('userSearch.academic')" value="ACADEMIC" />
        </el-select>
      </el-form-item>
      <el-form-item :label="$t('userSearch.status')">
        <el-select
          v-model="modelValue.status"
          :placeholder="$t('userSearch.pleaseSelect')"
          clearable
          class="filter-select"
          @change="debouncedEmit('search')"
        >
          <el-option :label="$t('userSearch.statusInactive')" :value="0" />
          <el-option :label="$t('userSearch.statusActive')" :value="1" />
          <el-option :label="$t('userSearch.statusDisabled')" :value="2" />
          <el-option :label="$t('userSearch.statusDeleted')" :value="3" />
        </el-select>
      </el-form-item>

      <!-- 院系/专业/班级级联（可选） -->
      <template v-if="showDeptCascade">
        <el-form-item :label="$t('userSearch.department')">
          <el-select
            v-model="modelValue.departmentId"
            :placeholder="$t('userSearch.department')"
            clearable
            class="filter-select"
            @change="debouncedEmit('department-change', modelValue.departmentId)"
          >
            <el-option v-for="d in departments" :key="d.id" :label="d.name" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('userSearch.major')">
          <el-select
            v-model="modelValue.majorId"
            :placeholder="$t('userSearch.major')"
            clearable
            class="filter-select"
            :disabled="!modelValue.departmentId"
            @change="debouncedEmit('major-change', modelValue.majorId)"
          >
            <el-option v-for="m in majors" :key="m.id" :label="m.name" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('userSearch.classLabel')">
          <el-select
            v-model="modelValue.classId"
            :placeholder="$t('userSearch.classLabel')"
            clearable
            class="filter-select"
            :disabled="!modelValue.majorId"
          >
            <el-option v-for="c in classes" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
      </template>

      <el-form-item>
        <el-button type="primary" @click="$emit('search')">{{ $t('userSearch.query') }}</el-button>
        <el-button @click="$emit('reset')">{{ $t('userSearch.reset') }}</el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script setup>
import { ref, onUnmounted } from 'vue'

const props = defineProps({
  modelValue: { type: Object, required: true },
  departments: { type: Array, default: () => [] },
  majors: { type: Array, default: () => [] },
  classes: { type: Array, default: () => [] },
  showDeptCascade: { type: Boolean, default: false }
})

const emit = defineEmits(['search', 'reset', 'department-change', 'major-change'])

// vue/no-mutating-props: modelValue 是响应式对象引用，
// v-model 绑定嵌套属性是 Element Plus 表单的标准模式，父组件自动同步。

// P1-UX: 筛选变更防抖 300ms，避免快速切换时重复触发 API
let debounceTimer = null
function debouncedEmit(event, ...args) {
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => {
    emit(event, ...args)
    debounceTimer = null
  }, 300)
}

// 重写快捷 emit：select-change 走防抖
const handleSelectChange = (event, ...args) => debouncedEmit(event, ...args)

onUnmounted(() => {
  if (debounceTimer) clearTimeout(debounceTimer)
})
</script>

<style scoped>
.search-card {
  margin-bottom: var(--space-4);
  background: var(--el-fill-color-blank);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs), var(--shadow-sm);
}

.filter-input {
  width: 160px;
}

.filter-select {
  width: 160px;
}
</style>
