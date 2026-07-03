<template>
  <div class="signature-block" :class="{ removable }">
    <div class="block-header">
      <h4 class="block-title">{{ title }}</h4>
      <el-button
        v-if="removable"
        type="danger"
        size="small"
        link
        @click="$emit('remove')"
      >
        删除
      </el-button>
    </div>

    <el-row :gutter="20">
      <!-- 左侧：意见输入 -->
      <el-col :span="14">
        <el-form label-width="0" size="small">
          <el-form-item label="">
            <el-input
              :model-value="opinion"
              type="textarea"
              :rows="4"
              placeholder="请输入意见..."
              :readonly="readonly"
              @input="handleOpinionInput"
            />
          </el-form-item>
        </el-form>
      </el-col>

      <!-- 右侧：签名 + 公章 + 日期 -->
      <el-col :span="10">
        <div class="signature-fields">
          <div class="sig-field">
            <span class="sig-label">签字：</span>
            <SignatureUploader
              :image-url="signatureImageUrl"
              :disabled="readonly"
              :uploader="signatureUploader"
              @update:image-url="handleSignatureUpdate"
            />
          </div>
          <div class="sig-field">
            <span class="sig-label">公章：</span>
            <SignatureUploader
              :image-url="sealImageUrl"
              label="公章"
              :disabled="readonly"
              :uploader="sealUploader"
              @update:image-url="handleSealUpdate"
            />
          </div>
          <div class="sig-field">
            <span class="sig-label">日期：</span>
            <DatePickerYear
              :model-value="signDateNumber"
              :disabled="readonly"
              @update:model-value="handleDateUpdate"
            />
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import SignatureUploader from './SignatureUploader.vue'
import DatePickerYear from './DatePickerYear.vue'

const props = defineProps({
  signLevel: { type: String, default: 'LEAD' },
  opinion: { type: String, default: '' },
  signatureImageUrl: { type: String, default: '' },
  sealImageUrl: { type: String, default: '' },
  signDate: { type: [String, Number], default: '' },
  readonly: { type: Boolean, default: false },
  title: { type: String, default: '' },
  removable: { type: Boolean, default: false },
  // P1-UX: 父组件传入真实的上传函数 (file, onProgress) => Promise<{url, fileName, fileSize}>
  signatureUploader: { type: Function, default: null },
  sealUploader: { type: Function, default: null }
})

const emit = defineEmits([
  'update:opinion',
  'update:signatureImageUrl',
  'update:sealImageUrl',
  'update:signDate',
  'remove'
])

const signDateNumber = computed(() => {
  if (!props.signDate) return null
  const num = Number(props.signDate)
  return isNaN(num) ? null : num
})

function handleOpinionInput(val) {
  emit('update:opinion', val)
}

function handleSignatureUpdate(val) {
  emit('update:signatureImageUrl', val)
}

function handleSealUpdate(val) {
  emit('update:sealImageUrl', val)
}

function handleDateUpdate(val) {
  emit('update:signDate', val ? String(val) : '')
}
</script>

<style scoped>
.signature-block {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 16px;
  margin-bottom: 12px;
  background: #fafafa;
}
.signature-block.removable {
  border-color: #fde2e2;
  background: #fff5f5;
}
.block-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.block-title {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
.signature-fields {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.sig-field {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}
.sig-label {
  font-size: 13px;
  color: #606266;
  white-space: nowrap;
  line-height: 32px;
  min-width: 40px;
}
</style>
