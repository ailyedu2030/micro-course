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
      <el-form-item label="关键字">
        <el-input
          v-model="modelValue.keyword"
          placeholder="账号/姓名"
          clearable
          class="filter-input"
          @clear="$emit('search')"
          @keyup.enter="$emit('search')"
        />
      </el-form-item>
      <el-form-item label="角色">
        <el-select
          v-model="modelValue.role"
          placeholder="请选择"
          clearable
          class="filter-select"
          @change="$emit('search')"
        >
          <el-option label="学生" value="STUDENT" />
          <el-option label="教师" value="TEACHER" />
          <el-option label="管理员" value="ADMIN" />
          <el-option label="教务" value="ACADEMIC" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select
          v-model="modelValue.status"
          placeholder="请选择"
          clearable
          class="filter-select"
          @change="$emit('search')"
        >
          <el-option label="未激活" :value="0" />
          <el-option label="启用" :value="1" />
          <el-option label="禁用" :value="2" />
          <el-option label="已删除" :value="3" />
        </el-select>
      </el-form-item>

      <!-- 院系/专业/班级级联（可选） -->
      <template v-if="showDeptCascade">
        <el-form-item label="院系">
          <el-select
            v-model="modelValue.departmentId"
            placeholder="请选择院系"
            clearable
            class="filter-select"
            @change="$emit('department-change', modelValue.departmentId)"
          >
            <el-option v-for="d in departments" :key="d.id" :label="d.name" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="专业">
          <el-select
            v-model="modelValue.majorId"
            placeholder="请选择专业"
            clearable
            class="filter-select"
            :disabled="!modelValue.departmentId"
            @change="$emit('major-change', modelValue.majorId)"
          >
            <el-option v-for="m in majors" :key="m.id" :label="m.name" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="班级">
          <el-select
            v-model="modelValue.classId"
            placeholder="请选择班级"
            clearable
            class="filter-select"
            :disabled="!modelValue.majorId"
          >
            <el-option v-for="c in classes" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
      </template>

      <el-form-item>
        <el-button type="primary" @click="$emit('search')">查询</el-button>
        <el-button @click="$emit('reset')">重置</el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script setup>
defineProps({
  modelValue: { type: Object, required: true },
  departments: { type: Array, default: () => [] },
  majors: { type: Array, default: () => [] },
  classes: { type: Array, default: () => [] },
  showDeptCascade: { type: Boolean, default: false }
})

defineEmits(['search', 'reset', 'department-change', 'major-change'])

// vue/no-mutating-props: modelValue 是响应式对象引用，
// v-model 绑定嵌套属性是 Element Plus 表单的标准模式，父组件自动同步。
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
