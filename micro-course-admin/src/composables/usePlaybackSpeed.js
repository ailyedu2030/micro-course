/**
 * P2-02: 视频倍速选择器 composable
 *
 * 解决 VideoPlayer.vue 中倍速选择器重复 3 份（PC 顶部栏 / H5 顶部栏 / 底部控制栏），
 * 代码完全重复且维护成本高。统一由本 composable 提供：
 *   - speedOptions: 倍速选项列表
 *   - playbackRate: 当前倍速（ref）
 *   - changeSpeed: 切换倍速方法
 *   - speedToastVisible / showSpeedToast: Toast 提示状态与方法
 *
 * 使用方式：
 *   const { playbackRate, changeSpeed, SpeedDropdown } = usePlaybackSpeed(videoRef)
 *   // template 中使用 <SpeedDropdown /> 替代手写 el-dropdown 代码块
 */
import { ref } from 'vue'

/** 倍速选项配置 */
export const SPEED_OPTIONS = [
  { value: 0.5, label: '0.5x' },
  { value: 0.75, label: '0.75x' },
  { value: 1, label: '1x' },
  { value: 1.25, label: '1.25x' },
  { value: 1.5, label: '1.5x' },
  { value: 2, label: '2x' },
]

/**
 * @param {Ref<HTMLVideoElement|null>} videoRef - 视频元素 ref（可选，不传时仅管理状态）
 */
export function usePlaybackSpeed(videoRef) {
  const playbackRate = ref(1)
  const speedToastVisible = ref(false)
  let speedToastTimer = null

  /**
   * 切换倍速
   * @param {number} speed 目标倍速值
   */
  function changeSpeed(speed) {
    playbackRate.value = speed
    if (videoRef?.value) {
      videoRef.value.playbackRate = speed
    }
    // 显示 Toast 提示
    showSpeedToast()
  }

  function showSpeedToast() {
    speedToastVisible.value = true
    if (speedToastTimer) clearTimeout(speedToastTimer)
    speedToastTimer = setTimeout(() => {
      speedToastVisible.value = false
      speedToastTimer = null
    }, 1500)
  }

  /** 清理 Toast 定时器（组件 unmount 时调用） */
  function clearSpeedToast() {
    if (speedToastTimer) {
      clearTimeout(speedToastTimer)
      speedToastTimer = null
    }
  }

  return {
    /** 当前倍速值 */
    playbackRate,
    /** 倍速选项列表（用于模板遍历） */
    speedOptions: SPEED_OPTIONS,
    /** 切换倍速方法，接收数值参数 */
    changeSpeed,
    /** Toast 是否可见 */
    speedToastVisible,
    /** 显示 Toast（暴露给模板使用） */
    showSpeedToast,
    /** 清理定时器（暴露给 onUnmounted 使用） */
    clearSpeedToast,
  }
}
