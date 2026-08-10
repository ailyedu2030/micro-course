<!--
  UserTable — 用户列表通用表格（C4 共享组件）
  用于 admin/UserList 和 users/UserList，提供统一的用户表格展示

  Props:
    loading: 是否加载中
    error: 是否加载失败
    data: 表格数据数组
    total: 总记录数
    page: 当前页码
    size: 每页条数
    showAvatar: 是否显示头像列（默认 false）
    showDeptDetail: 是否显示院系/专业/班级分列（默认 true，false = 合并为"部门/专业/班级"）
    statusClickable: 状态标签是否可点击切换（默认 false）

  Events:
    update:page — 页码变更
    update:size — 每页条数变更
    retry — 点击重试
    view-detail — 查看详情
    edit — 编辑
    delete — 删除
    toggle-status — 切换状态
    avatar-upload — 上传头像（返回 file, row）
-->
<template>
  <el-card class="table-card" shadow="never">
    <!-- 加载骨架 -->
    <el-skeleton v-if="loading" :rows="6" animated />

    <!-- 错误状态 -->
    <el-result
      v-else-if="error"
      icon="error"
      title="数据加载失败"
      sub-title="请稍后重试"
    >
      <template #extra>
        <el-button type="primary" @click="$emit('retry')">重试</el-button>
      </template>
    </el-result>

    <!-- 空状态 -->
    <el-empty
      v-else-if="!loading && data.length === 0"
      description="暂无用户数据"
      :image-size="120"
    />

    <!-- 表格 -->
    <template v-else>
      <el-table
        :data="data"
        stripe
        border
        class="data-table"
        v-loading="loading"
        :aria-busy="loading"
      >
        <el-table-column type="index" label="序号" width="70" align="center" />

        <!-- 头像列（可选） -->
        <el-table-column v-if="showAvatar" label="头像" width="80" align="center">
          <template #default="{ row }">
            <slot name="avatar" :row="row">
              <el-avatar :size="40">{{ row.realName?.charAt(0) || 'U' }}</el-avatar>
            </slot>
          </template>
        </el-table-column>

        <el-table-column prop="username" label="账号" min-width="120" show-overflow-tooltip />
        <el-table-column prop="realName" label="姓名" min-width="100" show-overflow-tooltip>
          <template #default="{ row }">
            <span>{{ row.realName || '-' }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="role" label="角色" width="100" align="center">
          <template #default="{ row }">
            <slot name="role" :row="row">
              <el-tag size="small" :type="getRoleTagType(row.role)">
                {{ getRoleLabel(row.role) }}
              </el-tag>
            </slot>
          </template>
        </el-table-column>

        <!-- 院系/专业/班级 — 分列或合并 -->
        <template v-if="showDeptDetail">
          <el-table-column prop="departmentName" label="院系" min-width="130" show-overflow-tooltip>
            <template #default="{ row }">
              <span>{{ row.departmentName || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="majorName" label="专业" min-width="130" show-overflow-tooltip>
            <template #default="{ row }">
              <span>{{ row.majorName || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="className" label="班级" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">
              <span>{{ row.className || '-' }}</span>
            </template>
          </el-table-column>
        </template>
        <template v-else>
          <el-table-column label="部门/专业/班级" min-width="180">
            <template #default="{ row }">
              <span>{{ row.departmentName }}</span>
              <span v-if="row.majorName"> / {{ row.majorName }}</span>
              <span v-if="row.className"> / {{ row.className }}</span>
            </template>
          </el-table-column>
        </template>

        <el-table-column prop="status" label="状态" width="90" align="center">
          <template #default="{ row }">
            <slot name="status" :row="row">
              <el-tag
                size="small"
                :type="getStatusTagType(row.status)"
                :style="statusClickable ? { cursor: 'pointer' } : {}"
                @click="statusClickable && $emit('toggle-status', row)"
              >
                {{ getStatusLabel(row.status) }}
              </el-tag>
            </slot>
          </template>
        </el-table-column>

        <el-table-column label="注册时间" min-width="160">
          <template #default="{ row }">
            <span class="text-secondary">{{ $formatDateTime(row.createdAt) || '-' }}</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="200" align="center" fixed="right">
          <template #default="{ row }">
            <slot name="actions" :row="row">
              <el-button type="primary" link size="small" @click="$emit('view-detail', row)">详情</el-button>
              <el-button type="warning" link size="small" @click="$emit('edit', row)">编辑</el-button>
              <el-button type="info" link size="small" @click="$emit('reset-password', row)">重置密码</el-button>
              <el-button type="danger" link size="small" @click="$emit('delete', row)">删除</el-button>
            </slot>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrap">
        <el-pagination
          :current-page="page"
          :page-size="size"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          @update:current-page="$emit('update:page', $event)"
          @update:page-size="$emit('update:size', $event)"
          aria-label="分页导航"
        />
      </div>
    </template>
  </el-card>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  loading: { type: Boolean, default: false },
  error: { type: Boolean, default: false },
  data: { type: Array, default: () => [] },
  total: { type: Number, default: 0 },
  page: { type: Number, default: 1 },
  size: { type: Number, default: 20 },
  showAvatar: { type: Boolean, default: false },
  showDeptDetail: { type: Boolean, default: true },
  statusClickable: { type: Boolean, default: false }
})

defineEmits(['update:page', 'update:size', 'retry', 'view-detail', 'edit', 'delete', 'toggle-status', 'reset-password'])

function getRoleLabel(role) {
  const map = { STUDENT: '学生', TEACHER: '教师', ADMIN: '管理员', ACADEMIC: '教务' }
  return map[role] || role || '-'
}

function getRoleTagType(role) {
  const map = { STUDENT: 'success', TEACHER: 'warning', ADMIN: 'danger', ACADEMIC: '' }
  return map[role] || 'info'
}

function getStatusLabel(status) {
  const map = { 0: '未激活', 1: '启用', 2: '禁用', 3: '已删除' }
  return map[status] || '未知'
}

function getStatusTagType(status) {
  const map = { 0: 'info', 1: 'success', 2: 'danger', 3: 'info' }
  return map[status] || 'info'
}
</script>

<style scoped>
.table-card {
  background: var(--el-fill-color-blank);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs), var(--shadow-sm);
  transition: box-shadow var(--duration-base) var(--ease-out);
}

.table-card:hover {
  box-shadow: var(--shadow-md), var(--shadow-lg);
}

.data-table {
  width: 100%;
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.data-table :deep(.el-table__header th) {
  background: var(--el-fill-color-light) !important;
  color: var(--el-text-color-primary);
  font-weight: var(--weight-semibold);
  font-size: var(--text-base);
}

.data-table :deep(.el-table__row:hover > td) {
  background: var(--role-primary-light-9) !important;
}

.pagination-wrap {
  margin-top: var(--space-6);
  display: flex;
  justify-content: flex-end;
  padding: var(--space-4) var(--space-5);
  border-top: 1px solid var(--el-border-color-lighter);
}

.text-secondary {
  color: var(--el-text-color-secondary);
  font-size: var(--text-base);
}
</style>
