// ==================== 课程学时校验工具 ====================
// 用于检测课程学时与章节学时之和 / 学分学时配比 不一致
// 返回分级：'ok' | 'warn' | 'error'

const TOLERANCE_PCT = 0.10  // 10% 误差容忍：小于此比例视为 warn

/**
 * 校验单门课程的学时一致性
 * @param {Object} course - 课程行 { hours, credits, chapters?: [{hours}] }
 * @returns {{ level: 'ok'|'warn'|'error', issues: Array }}
 */
export function validateCourseHours(course) {
  const issues = []
  const cred = Number(course.credits) || 0
  const hrs = Number(course.hours) || 0
  const chapters = course.chapters || []
  const chapterSum = chapters.reduce((s, ch) => s + (Number(ch.hours) || 0), 0)

  // 规则 1：课程学时 vs 章节学时之和（仅当既有学时又有章节时触发）
  if (hrs > 0 && chapters.length > 0) {
    const diff = hrs - chapterSum
    if (diff !== 0) {
      const absPct = Math.abs(diff) / Math.max(hrs, 1)
      const severity = absPct > TOLERANCE_PCT ? 'error' : 'warn'
      issues.push({
        key: 'hours',
        severity,
        message: `课程学时 ${hrs}h ≠ 章节学时之和 ${chapterSum}h（差 ${diff > 0 ? '+' : ''}${diff}h）`,
        diff,
        courseHours: hrs,
        chapterSum
      })
    }
  }

  // 规则 2：学分 / 学时 配比 (学分 × 16 应是建议最低学时)
  if (cred > 0 && hrs > 0 && hrs < cred * 16) {
    issues.push({
      key: 'credits',
      severity: 'warn',
      message: `学分 ${cred} 通常需至少 ${cred * 16} 学时，当前 ${hrs}h`,
      recommendHours: cred * 16,
      actualHours: hrs
    })
  }

  // 总体等级
  let level = 'ok'
  if (issues.some(i => i.severity === 'error')) level = 'error'
  else if (issues.some(i => i.severity === 'warn')) level = 'warn'

  return { level, issues }
}

/**
 * 校验一组课程，返回汇总
 * @returns {
 *   totalIssues,            // 总问题数
 *   errorCount,             // error 级问题数
 *   warningCount,           // warn 级问题数
 *   issueList,              // 详情列表 [{courseIndex, courseName, level, issues}]
 *   worstLevel              // 全局最严重等级 ('ok'|'warn'|'error')
 * }
 */
export function validateCourseList(courses) {
  const issueList = []
  let totalIssues = 0
  let errorCount = 0
  let warningCount = 0
  let worstLevel = 'ok'

  ;(courses || []).forEach((row, idx) => {
    const { level, issues } = validateCourseHours(row)
    if (issues.length === 0) return
    issueList.push({
      courseIndex: idx,
      courseName: row.courseName || `第${idx + 1}门课程`,
      hours: Number(row.hours) || 0,
      chapterSum: (row.chapters || []).reduce((s, c) => s + (Number(c.hours) || 0), 0),
      level,
      issues
    })
    totalIssues += issues.length
    issues.forEach(i => {
      if (i.severity === 'error') errorCount++
      else if (i.severity === 'warn') warningCount++
    })
    if (level === 'error') worstLevel = 'error'
    else if (level === 'warn' && worstLevel !== 'error') worstLevel = 'warn'
  })

  return { totalIssues, errorCount, warningCount, issueList, worstLevel }
}

/**
 * 一键修复：把所有不匹配课程行的 hours 改为章节学时之和
 * @returns 修改的课程行数
 */
export function autoFixCourseHours(courses) {
  let fixedCount = 0
  ;(courses || []).forEach((row) => {
    const chapters = row.chapters || []
    if (chapters.length > 0) {
      const chapterSum = chapters.reduce((s, c) => s + (Number(c.hours) || 0), 0)
      const hrs = Number(row.hours) || 0
      if (chapterSum !== hrs && hrs > 0) {
        row.hours = chapterSum
        fixedCount++
      }
    }
  })
  return fixedCount
}
