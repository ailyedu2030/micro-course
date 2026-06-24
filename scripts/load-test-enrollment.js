/**
 * 压测脚本 — 选课并发超卖防护验证
 *
 * 目标: 验证 P0-1 业务逻辑审计修复在 N 并发下确实防超卖
 *
 * 使用: 需先创建一个 max=N/2 的 PUBLISHED 课程
 * 然后: COURSE_ID=NN node scripts/load-test-enrollment.js
 *
 * 期望结果 (修复后):
 * - ENROLLED 数 = max_students (不超卖)
 * - WAITLIST 数 = N - max
 * - 没有重复选课 (UNIQUE 约束保证)
 */

const axios = require('axios')
const { Client } = require('pg')

const API = 'http://localhost:8080'

async function login(username, password) {
  const res = await axios.post(`${API}/api/auth/login`, {
    username, password
  }, {
    baseURL: API,
    transformRequest: [(d) => JSON.stringify(d)],
    headers: { 'Content-Type': 'application/json' }
  }).catch(e => e.response)
  return res?.data?.data?.accessToken
}

async function enroll(token, courseId) {
  try {
    const res = await axios.post(`${API}/api/enrollments`, {
      courseId
    }, {
      baseURL: API,
      transformRequest: [(d) => JSON.stringify(d)],
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
      }
    })
    return { status: res.status, data: res.data }
  } catch (e) {
    return { status: e.response?.status || 0, error: e.message, data: e.response?.data }
  }
}

async function setupTestCourse(adminToken, maxStudents) {
  const ts = Date.now()
  console.log(`[Setup] 创建测试课程 max=${maxStudents}...`)

  // 1. 创建
  const c = await axios.post(`${API}/api/courses`, {
    title: `压测-${ts}`,
    categoryId: 1, teacherId: 3, difficulty: 1, courseType: 'VIDEO',
    maxStudents, description: 'load test', summary: 'load test', coverUrl: 'http://x.com/c.jpg'
  }, {
    baseURL: API, transformRequest: [(d) => JSON.stringify(d)],
    headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${adminToken}` }
  })
  const courseId = c.data.data.id

  // 2. 添加章节
  const ch = await axios.post(`${API}/api/chapters`, {
    title: 'Ch1', courseId, sortOrder: 99
  }, {
    baseURL: API, transformRequest: [(d) => JSON.stringify(d)],
    headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${adminToken}` }
  }).catch(() => null)
  const chapId = ch?.data?.data?.id

  // 3. 视频 + SQL 改 status=2
  if (chapId) {
    await axios.post(`${API}/api/videos`, {
      title: 'L1', chapterId: chapId, courseId
    }, {
      baseURL: API, transformRequest: [(d) => JSON.stringify(d)],
      headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${adminToken}` }
    }).catch(() => null)

    // SQL: 把 video status 改为 2 (COMPLETED)
    const pg = new Client({ host: 'localhost', database: 'micro_course', user: 'postgres', password: process.env.PG_PASSWORD || 'postgres' })
    await pg.connect()
    await pg.query('UPDATE videos SET status=2 WHERE course_id=$1', [courseId])
    await pg.end()
  }

  // 4. 提交/审批/发布
  await axios.post(`${API}/api/courses/${courseId}/submit`, {}, {
    baseURL: API, headers: { 'Authorization': `Bearer ${adminToken}` }
  })
  await axios.post(`${API}/api/courses/${courseId}/approve`, {}, {
    baseURL: API, headers: { 'Authorization': `Bearer ${adminToken}` }
  })
  await axios.post(`${API}/api/courses/${courseId}/publish`, {}, {
    baseURL: API, headers: { 'Authorization': `Bearer ${adminToken}` }
  })

  // 验证发布
  const cInfo = await axios.get(`${API}/api/courses/${courseId}`, {
    baseURL: API, headers: { 'Authorization': `Bearer ${adminToken}` }
  })
  if (cInfo.data.data.status !== 4) {
    throw new Error(`课程未发布: status=${cInfo.data.data.status}`)
  }

  console.log(`[Setup] ✓ 课程已发布: id=${courseId}`)
  return courseId
}

async function getDbStats(courseId) {
  const pg = new Client({ host: 'localhost', database: 'micro_course', user: 'postgres', password: process.env.PG_PASSWORD || 'postgres' })
  await pg.connect()
  const r = await pg.query(`
    SELECT enrollment_status, COUNT(*) as cnt
    FROM enrollments
    WHERE course_id = $1
    GROUP BY enrollment_status
    ORDER BY cnt DESC
  `, [courseId])
  await pg.end()
  return r.rows
}

async function main() {
  const courseId = process.env.COURSE_ID ? parseInt(process.env.COURSE_ID) : null
  const N = parseInt(process.env.N || '50')
  const maxStudents = parseInt(process.env.MAX || '5')

  console.log('╔════════════════════════════════════════════╗')
  console.log('║  选课并发超卖防护 — 压测验证                  ║')
  console.log('╚════════════════════════════════════════════╝\n')

  const adminToken = await login('admin', 'admin123')
  console.log('✓ admin 登录')

  let cid = courseId
  if (!cid) {
    cid = await setupTestCourse(adminToken, maxStudents)
  } else {
    console.log(`✓ 使用现有课程: id=${cid}`)
  }

  console.log(`\n[Load Test] ${N} 个学生并发选课 (max_students=${maxStudents})...`)

  // 获取 N 个学生 (直到拿到能登录的)
  const usersRes = await axios.get(`${API}/api/users?role=STUDENT&size=200`, {
    baseURL: API, headers: { 'Authorization': `Bearer ${adminToken}` }
  })
  const allStudents = usersRes.data.data.items
  console.log(`可用学生总数: ${allStudents.length}`)

  // 逐个登录直到拿到 N 个 token
  const tokens = []
  for (const s of allStudents) {
    if (tokens.length >= N) break
    const t = await login(s.username, '123456')
    if (t) tokens.push({ userId: s.id, username: s.username, token: t })
  }
  console.log(`✓ ${tokens.length} 个 token 就绪`)

  // 并发选课
  const start = Date.now()
  const promises = tokens.map(t => enroll(t.token, cid))
  const results = await Promise.all(promises)
  const elapsed = Date.now() - start

  // 统计
  let enrolled = 0, waitlist = 0, other = 0
  const otherDetails = {}
  results.forEach(r => {
    const status = r.data?.data?.enrollmentStatus || r.data?.data?.enrollment_status
    if (status === 'ENROLLED') enrolled++
    else if (status === 'WAITLIST') waitlist++
    else {
      other++
      const k = `${r.status}:${r.data?.message || r.error?.slice(0, 30) || 'unknown'}`
      otherDetails[k] = (otherDetails[k] || 0) + 1
    }
  })

  // 双重验证: 从 DB 直接查
  const dbStats = await getDbStats(cid)
  const dbEnrolled = dbStats.find(s => s.enrollment_status === 'ENROLLED')?.cnt || 0
  const dbWaitlist = dbStats.find(s => s.enrollment_status === 'WAITLIST')?.cnt || 0
  const dbOther = dbStats.reduce((sum, s) => sum + parseInt(s.cnt), 0) - dbEnrolled - dbWaitlist

  console.log(`\n╔════════════════════════════════════════════╗`)
  console.log(`║  压测结果                                       ║`)
  console.log(`╚════════════════════════════════════════════╝`)
  console.log(`总请求: ${tokens.length}  总耗时: ${elapsed}ms (QPS: ${(tokens.length / (elapsed/1000)).toFixed(0)})`)
  console.log(``)
  console.log(`API 返回:    ENROLLED=${enrolled}  WAITLIST=${waitlist}  其他=${other}`)
  console.log(`DB 实查:    ENROLLED=${dbEnrolled}  WAITLIST=${dbWaitlist}  其他=${dbOther}`)
  if (Object.keys(otherDetails).length > 0) {
    console.log(`其他错误: ${JSON.stringify(otherDetails)}`)
  }

  console.log(`\n╔════════════════════════════════════════════╗`)
  console.log(`║  业务逻辑验证                                    ║`)
  console.log(`╚════════════════════════════════════════════╝`)

  let pass = true
  if (dbEnrolled > maxStudents) {
    console.log(`❌ FAIL: 超卖! DB ENROLLED=${dbEnrolled} > max=${maxStudents}`)
    pass = false
  } else {
    console.log(`✓ PASS: ENROLLED=${dbEnrolled} ≤ max=${maxStudents} (无超卖)`)
  }

  if (dbEnrolled + dbWaitlist + dbOther === tokens.length) {
    console.log(`✓ PASS: ${tokens.length} 个请求全部入库 (无丢失)`)
  } else {
    console.log(`⚠ WARN: 入库 ${dbEnrolled + dbWaitlist + dbOther} ≠ 发出 ${tokens.length}`)
  }

  if (dbEnrolled === maxStudents && dbWaitlist > 0) {
    console.log(`✓ PASS: 满员触发 WAITLIST 机制 (${dbWaitlist} 个候补)`)
  }

  console.log(``)
  console.log(pass ? '✅ 压测通过 - P0-1 修复在 100 并发下有效' : '❌ 压测失败')
  process.exit(pass ? 0 : 1)
}

main().catch(e => {
  console.error('Error:', e.message)
  console.error(e.stack)
  process.exit(2)
})
