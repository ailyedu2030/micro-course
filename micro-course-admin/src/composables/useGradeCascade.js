/**
 * useGradeCascade — 年级联动逻辑 composable
 *
 * 封装院系 → 专业 → 班级的三级联动查询逻辑，消除多处的重复代码。
 *
 * 使用方式：
 *   import { useGradeCascade } from '@/composables/useGradeCascade'
 *   const { majors, classes, fetchMajors, fetchClasses, handleDeptChange, handleMajorChange } = useGradeCascade()
 *
 * 注意：不封装 departments 的获取，因为 departments 在各页面中用途不同
 * （搜索栏 vs 编辑弹窗），让各页面自行管理 departmentOptions 的加载。
 */
import { ref } from 'vue'
import { getMajors } from '@/api/major'
import { getClasses } from '@/api/class'

/**
 * 创建一个独立的联级状态实例。
 * 每个调用方获得独立的状态（适用于同一页面有多个级联区域如搜索栏+弹窗）。
 */
export function useGradeCascade() {
  const majors = ref([])
  const classes = ref([])

  /** 根据院系 ID 加载专业列表 */
  async function fetchMajors(departmentId) {
    if (!departmentId) {
      majors.value = []
      return
    }
    try {
      const { data } = await getMajors({ departmentId, size: 1000 })
      majors.value = data.items || []
    } catch {
      majors.value = []
    }
  }

  /** 根据专业 ID 加载班级列表 */
  async function fetchClasses(majorId) {
    if (!majorId) {
      classes.value = []
      return
    }
    try {
      const { data } = await getClasses({ majorId, size: 1000 })
      classes.value = data.items || []
    } catch {
      classes.value = []
    }
  }

  /**
   * 切换院系时：重置专业/班级选择，重新加载专业列表
   * @param {string|number} departmentId 新选的院系 ID
   * @param {object} target 外部响应式对象（可选），同时清空其 majorId/classId
   */
  function handleDeptChange(departmentId, target) {
    if (target) {
      target.majorId = ''
      target.classId = ''
    }
    majors.value = []
    classes.value = []
    fetchMajors(departmentId)
  }

  /**
   * 切换专业时：重置班级选择，重新加载班级列表
   * @param {string|number} majorId 新选的专业 ID
   * @param {object} target 外部响应式对象（可选），同时清空其 classId
   */
  function handleMajorChange(majorId, target) {
    if (target) {
      target.classId = ''
    }
    classes.value = []
    fetchClasses(majorId)
  }

  return {
    majors,
    classes,
    fetchMajors,
    fetchClasses,
    handleDeptChange,
    handleMajorChange
  }
}
