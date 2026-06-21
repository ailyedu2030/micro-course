<!--
  学习趋势图（ECharts 折线图）
  从 LearningCenter.vue 拆分而来：自管理 echarts 实例、resize 与 dispose。
  通过 props 接收数据，PC / H5 各实例化一份，由父级 v-if/v-else 控制挂载。
  视觉与原内联实现完全一致（option 逐字保留）。
  Author: jackie
-->
<template>
  <div
    v-loading="loading"
    :aria-busy="loading"
    class="chart-container"
    :class="{ 'h5-chart-container': mobile }"
  >
    <div v-if="data.length === 0" class="empty-wrap">
      <el-empty description="暂无学习数据" :image-size="mobile ? 60 : 80" />
    </div>
    <div
      v-else
      ref="chartRef"
      class="echarts-container"
      role="img"
      aria-label="本周学习时长分布图"
    ></div>
  </div>
</template>

<script setup>
import { ref, watch, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  data: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  accuracyMode: { type: Boolean, default: false },
  mobile: { type: Boolean, default: false }
})

const chartRef = ref(null)
let chartInstance = null

function initChart() {
  const containerRef = chartRef.value
  if (!containerRef || props.data.length === 0) return

  if (chartInstance) {
    chartInstance.dispose()
  }

  chartInstance = echarts.init(containerRef)

  const option = {
    title: {
      text: props.accuracyMode ? '正确率趋势' : '本周学习时长',
      textStyle: {
        fontSize: 14,
        fontWeight: 600,
        color: 'var(--el-text-color-primary)'
      },
      left: 0,
      top: 0
    },
    tooltip: {
      trigger: 'axis',
      formatter: props.accuracyMode ? '{b}: {c}%' : '{b}: {c} 小时'
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      top: '15%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: props.data.map((d) => d.day),
      axisLine: { lineStyle: { color: 'var(--el-border-color)' } },
      axisLabel: { color: 'var(--el-text-color-secondary)', fontSize: 12 }
    },
    yAxis: {
      type: 'value',
      name: '小时',
      nameTextStyle: { color: 'var(--el-text-color-secondary)', fontSize: 12 },
      axisLine: { show: false },
      splitLine: { lineStyle: { color: 'var(--el-border-color-lighter)', type: 'dashed' } },
      axisLabel: { color: 'var(--el-text-color-secondary)' }
    },
    series: [
      {
        data: props.data.map((d) => d.hours),
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 8,
        lineStyle: { color: 'var(--role-primary)', width: 2 },
        itemStyle: { color: 'var(--role-primary)', borderColor: 'var(--el-color-white)', borderWidth: 2 },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(99, 102, 241, 0.3)' },
              { offset: 1, color: 'rgba(99, 102, 241, 0.02)' }
            ]
          }
        }
      }
    ]
  }

  chartInstance.setOption(option)
}

function handleResize() {
  if (chartInstance) {
    chartInstance.resize()
  }
}

// 数据或模式变化后重绘图表
watch(
  () => [props.data, props.accuracyMode],
  async () => {
    await nextTick()
    initChart()
  },
  { deep: true }
)

onMounted(async () => {
  window.addEventListener('resize', handleResize)
  await nextTick()
  initChart()
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  if (chartInstance) {
    chartInstance.dispose()
    chartInstance = null
  }
})
</script>

<style scoped>
.chart-container {
  height: 260px;
}

.h5-chart-container {
  height: 200px;
}

.echarts-container {
  width: 100%;
  height: 100%;
}

.empty-wrap {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}
</style>
