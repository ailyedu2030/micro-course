<!--
  题目预览弹窗组件
-->
<template>
  <el-dialog v-model="visible" title="题目预览" width="600px">
    <div v-if="question" class="preview-question">
      <h3 class="question-title">{{ question.content }}</h3>
      <div class="question-meta">
        <el-tag v-if="question.questionType === 'SINGLE_CHOICE'" type="primary" size="small">单选题</el-tag>
        <el-tag v-else-if="question.questionType === 'MULTIPLE_CHOICE'" type="success" size="small">多选题</el-tag>
        <el-tag v-else-if="question.questionType === 'TRUE_FALSE'" type="warning" size="small">判断题</el-tag>
        <el-tag v-else-if="question.questionType === 'SHORT_ANSWER'" type="info" size="small">简答题</el-tag>
        <span class="score-tag">分值：{{ question.score ?? '-' }}</span>
      </div>

      <!-- 单选题 -->
      <div v-if="question.questionType === 'SINGLE_CHOICE'" class="options-area">
        <el-radio-group v-model="dummySingle">
          <el-radio
            v-for="(opt, idx) in parsedOptions"
            :key="idx"
            :value="idx"
            class="option-item">
            {{ opt.label }}
          </el-radio>
        </el-radio-group>
      </div>

      <!-- 多选题 -->
      <div v-else-if="question.questionType === 'MULTIPLE_CHOICE'" class="options-area">
        <el-checkbox-group v-model="dummyMultiple">
          <el-checkbox
            v-for="(opt, idx) in parsedOptions"
            :key="idx"
            :value="idx"
            class="option-item">
            {{ opt.label }}
          </el-checkbox>
        </el-checkbox-group>
      </div>

      <!-- 判断题 -->
      <div v-else-if="question.questionType === 'TRUE_FALSE'" class="options-area">
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
          class="fill-input" />
      </div>

      <div class="correct-answer">
        <span class="answer-label">正确答案：</span>
        <span class="answer-value">{{ displayAnswer }}</span>
      </div>

      <div v-if="question.analysis" class="answer-analysis">
        <span class="answer-label">答案解析：</span>
        <span class="answer-value">{{ question.analysis }}</span>
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
  if (props.question.questionType === 'TRUE_FALSE') {
    return ans === 'true' || ans === true ? '正确' : '错误'
  }
  if (props.question.questionType === 'MULTIPLE_CHOICE' && parsedOptions.value.length > 0) {
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
  padding: 8px 0;
}

.question-title {
  font-size: 16px;
  font-weight: 600;
  color: #1E293B;
  margin: 0 0 16px 0;
  line-height: 1.6;
}

.question-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
}

.score-tag {
  font-size: 13px;
  color: #64748B;
}

.options-area {
  margin-bottom: 24px;
  padding: 16px;
  background: #F8FAFC;
  border-radius: 8px;
}

.option-item {
  display: flex;
  margin-bottom: 12px;
  font-size: 14px;
  color: #334155;
  line-height: 1.5;
}

.option-item:last-child {
  margin-bottom: 0;
}

.fill-input {
  margin-bottom: 8px;
}

.correct-answer {
  margin-top: 16px;
  padding: 12px 16px;
  background: #F0FDF4;
  border: 1px solid #BBF7D0;
  border-radius: 8px;
  font-size: 14px;
}

.answer-label {
  font-weight: 600;
  color: #166534;
  margin-right: 8px;
}

.answer-value {
  color: #15803D;
}

.answer-analysis {
  margin-top: 12px;
  padding: 12px 16px;
  background: #F8FAFC;
  border: 1px solid #E2E8F0;
  border-radius: 8px;
  font-size: 14px;
}

.answer-analysis .answer-label {
  color: #475569;
}

.answer-analysis .answer-value {
  color: #64748B;
}
</style>