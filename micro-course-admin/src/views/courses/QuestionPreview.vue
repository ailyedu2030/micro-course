<!--
  题目预览弹窗组件
-->
<template>
  <el-dialog v-model="visible" title="题目预览" width="600px" :close-on-press-escape="true">
    <div v-if="question" class="preview-question">
      <h3 class="question-title">{{ question.content }}</h3>
      <div class="question-meta">
        <el-tag v-if="question.questionType === 'SINGLE'" type="primary" size="small">单选题</el-tag>
        <el-tag v-else-if="question.questionType === 'MULTIPLE'" type="success" size="small">多选题</el-tag>
        <el-tag v-else-if="question.questionType === 'JUDGE'" type="warning" size="small">判断题</el-tag>
        <el-tag v-else-if="question.questionType === 'SHORT_ANSWER'" type="info" size="small">简答题</el-tag>
        <span class="score-tag">分值：{{ question.score ?? '-' }}</span>
      </div>

      <!-- 单选题 -->
      <div v-if="question.questionType === 'SINGLE'" class="options-area">
        <el-radio-group v-model="dummySingle">
          <el-radio
            v-for="(opt, idx) in parsedOptions"
            :key="idx"
            :value="idx"
            class="option-item"
>
            {{ opt.label }}
          </el-radio>
        </el-radio-group>
      </div>

      <!-- 多选题 -->
      <div v-else-if="question.questionType === 'MULTIPLE'" class="options-area">
        <el-checkbox-group v-model="dummyMultiple">
          <el-checkbox
            v-for="(opt, idx) in parsedOptions"
            :key="idx"
            :value="idx"
            class="option-item"
>
            {{ opt.label }}
          </el-checkbox>
        </el-checkbox-group>
      </div>

      <!-- 判断题 -->
      <div v-else-if="question.questionType === 'JUDGE'" class="options-area">
        <el-radio-group v-model="dummyJudge">
          <el-radio :value="true" class="option-item">正确</el-radio>
          <el-radio :value="false" class="option-item">错误</el-radio>
        </el-radio-group>
      </div>

      <!-- 简答题 -->
      <div v-else-if="question.questionType === 'SHORT_ANSWER'" class="options-area">
        <el-input
          v-model="dummyFill"
          type="textarea"
          :rows="3"
          placeholder="输入答案"
          class="fill-input"
/>
      </div>

      <div class="correct-answer">
        <span class="answer-label">正确答案：</span>
        <span class="answer-value">{{ displayAnswer }}</span>
      </div>

      <div v-if="question.explanation" class="answer-analysis">
        <span class="answer-label">答案解析：</span>
        <span class="answer-value">{{ question.explanation }}</span>
      </div>
    </div>
    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  question: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['update:modelValue'])

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const dummySingle = ref(null)
const dummyMultiple = ref([])
const dummyJudge = ref(null)
const dummyFill = ref('')

const parsedOptions = computed(() => {
  if (!props.question?.options) return []
  try {
    return JSON.parse(props.question.options)
  } catch {
    return []
  }
})

const displayAnswer = computed(() => {
  if (!props.question?.answer) return '-'
  const ans = props.question.answer
  if (props.question.questionType === 'JUDGE') {
    return ans === 'true' || ans === true ? '正确' : '错误'
  }
  if (props.question.questionType === 'MULTIPLE' && parsedOptions.value.length > 0) {
    const labels = parsedOptions.value.map(o => o.label)
    return ans.split(',').map(a => labels.findIndex(l => l === a.trim()) + 1).filter(i => i > 0).map(i => String.fromCharCode(64 + i)).join(',')
  }
  return ans
})

watch(() => props.question, () => {
  dummySingle.value = null
  dummyMultiple.value = []
  dummyJudge.value = null
  dummyFill.value = ''
})
</script>

<style scoped>
.preview-question {
  padding: var(--space-2) 0;
}

.question-title {
  font-size: var(--text-md);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
  margin: 0 0 var(--space-4) 0;
  line-height: 1.6;
}

.question-meta {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-5);
}

.score-tag {
  font-size: var(--text-sm);
  color: var(--el-text-color-secondary);
}

.options-area {
  margin-bottom: var(--space-6);
  padding: var(--space-4);
  background: var(--el-fill-color-light);
  border-radius: var(--radius-md);
}

.option-item {
  display: flex;
  margin-bottom: var(--space-3);
  font-size: var(--text-base);
  color: var(--el-text-color-primary);
  line-height: 1.5;
}

.option-item:last-child {
  margin-bottom: 0;
}

.fill-input {
  margin-bottom: var(--space-2);
}

.correct-answer {
  margin-top: var(--space-4);
  padding: var(--space-3) var(--space-4);
  background: rgba(16, 185, 129, 0.08);
  border: 1px solid rgba(16, 185, 129, 0.2);
  border-radius: var(--radius-md);
  font-size: var(--text-base);
}

.answer-label {
  font-weight: var(--weight-semibold);
  color: var(--el-color-success);
  margin-right: var(--space-2);
}

.answer-value {
  color: var(--el-color-success);
}

.answer-analysis {
  margin-top: var(--space-3);
  padding: var(--space-3) var(--space-4);
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--radius-md);
  font-size: var(--text-base);
}

.answer-analysis .answer-label {
  color: var(--el-text-color-primary);
}

.answer-analysis .answer-value {
  color: var(--el-text-color-secondary);
}
</style>