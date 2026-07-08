<!--
  教师个人设置
  路由路径: /teacher/profile
  Phase 2.5 - 添加 API Key 管理（Hermes 第三方系统集成）
-->
<template>
  <div class="teacher-profile">
    <h2 class="page-title">个人设置</h2>

    <el-row :gutter="20">
      <el-col :span="14">
        <UserInfoEditor :is-mobile="false" />
        <PasswordEditor :is-mobile="false" />
      </el-col>
      <el-col :span="10">
        <ApiKeyManagement />
        <el-card class="api-key-info" shadow="never">
          <template #header>
            <span>使用说明</span>
          </template>
          <ol class="usage-list">
            <li>
在 Hermes 等第三方系统中配置 Webhook URL：<br>
              <code>{{ webhookUrl }}</code>
            </li>
            <li>在 HTTP Header 中添加 <code>X-API-Key</code>，值为您的 API Key</li>
            <li>
调用方式：<br>
              <pre class="usage-code">curl -X POST {{ webhookUrl }} \
  -H "Content-Type: application/json" \
  -H "X-API-Key: &lt;your-key&gt;" \
  -d '{
    "hermesCourseId": "HM-001",
    "title": "课程标题",
    "categoryId": 1,
    "chapters": [...],
    "pricing": {...}
  }'</pre>
            </li>
            <li>API Key 代表您的教师身份，可操作您全部课程权限</li>
            <li><strong>注意</strong>：请勿将 Key 提交到 git 或分享给他人</li>
          </ol>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import UserInfoEditor from '@/components/profile/UserInfoEditor.vue'
import PasswordEditor from '@/components/profile/PasswordEditor.vue'
import ApiKeyManagement from './ApiKeyManagement.vue'

const webhookUrl = computed(() => {
  return `${window.location.origin}/api/hermes/webhook/courses`
})
</script>

<style scoped>
.teacher-profile {
  padding: 0 8px;
}
.page-title {
  margin: 0 0 20px 0;
  font-size: 20px;
  font-weight: 600;
}
.api-key-info {
  margin-top: 0;
}
.usage-list {
  padding-left: 20px;
  font-size: 13px;
  line-height: 1.8;
  color: #555;
}
.usage-list li {
  margin-bottom: 12px;
}
.usage-list code {
  background: #f4f4f5;
  padding: 2px 6px;
  border-radius: 3px;
  font-size: 12px;
  color: #d63384;
  word-break: break-all;
}
.usage-code {
  background: #1e1e1e;
  color: #d4d4d4;
  padding: 12px;
  border-radius: 4px;
  font-size: 12px;
  line-height: 1.5;
  overflow-x: auto;
  font-family: 'SF Mono', 'Monaco', 'Consolas', monospace;
  margin-top: 6px;
}
</style>