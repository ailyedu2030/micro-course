<template>
  <div class="not-found-container">
    <el-empty :image-size="200" description="页面未找到">
      <template #image>
        <svg viewBox="0 0 24 24" width="120" height="120" fill="none" stroke="currentColor" stroke-width="1.5">
          <circle cx="12" cy="12" r="10"/>
          <path d="M12 8v4M12 16h.01"/>
        </svg>
      </template>
      <template #default>
        <p class="not-found-text">您访问的页面不存在，可能已被删除或地址有误</p>
        <el-button type="primary" @click="$router.push('/login')">返回首页</el-button>
      </template>
    </el-empty>
  </div>
</template>

<script setup>
</script>

<style scoped>
.not-found-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 60vh;
}
.not-found-text {
  color: #666;
  margin-bottom: 16px;
  font-size: 14px;
}
</style>