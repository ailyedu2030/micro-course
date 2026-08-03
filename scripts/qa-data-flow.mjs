// 微课平台 · 真实数据流夹具脚本
// 用途: 造出一条完整可用的学习链路（课程→章节→视频→题目→练习→选课→进度），
//       供浏览器深测"学习视图/答题页"等动态页；同时验证发布/选课/进度链路。
// 运行: node scripts/qa-data-flow.mjs
// 输出: /tmp/qa-data-flow.json (courseId/chapterId/videoId/questionId/exerciseId)

const BASE = process.env.QA_API_BASE || 'http://localhost:8089'

async function api(method, path, token, body) {
  const headers = { 'Content-Type': 'application/json' }
  if (token) headers.Authorization = 'Bearer ' + token
  const r = await fetch(BASE + '/api' + path, { method, headers, body: body ? JSON.stringify(body) : undefined })
  let data = null
  try { data = await r.json() } catch (e) {}
  return { status: r.status, code: data?.code, message: data?.message, data: data?.data }
}

async function login(user, pass) {
  const r = await api('POST', '/auth/login', null, { username: user, password: pass })
  if (r.code !== 200) throw new Error(`login ${user} failed: ${r.code} ${r.message}`)
  return r.data.accessToken
}

function record(name, ok, details) {
  console.log((ok ? '✓ ' : '✗ ') + name + (details ? ' ' + JSON.stringify(details).slice(0, 180) : ''))
  if (!ok) process.exitCode = 1
  return details
}

const teacher = await login('teacher1', 'password123')
const academic = await login('academic1', 'password123')
const admin = await login('admin', 'admin123')
const student = await login('student1', 'password123')

const suffix = Date.now().toString().slice(-6)
const out = {}

// 1. 教师创建课程
const created = await api('POST', '/courses', teacher, {
  title: 'QA数据流课程_' + suffix, categoryId: 1, learningMode: 'online-self-study', price: 0, isFree: true, courseType: 'VIDEO'
})
const courseId = created.data?.id
record('A1-创建课程', !!courseId, { id: courseId })
out.courseId = courseId

// 2. 设置封面（发布前置条件）
const cover = await api('PUT', `/courses/${courseId}`, teacher, {
  coverUrl: 'http://localhost:8088/uploads/covers/qa-cover.jpg', summary: 'QA 数据流测试课程'
})
record('A2-设置封面', cover.code === 200, { code: cover.code, msg: cover.message })

// 3. 创建章节 + 视频
const chapter = await api('POST', '/chapters', teacher, {
  title: '第一章 QA', courseId, description: '测试章节', sortOrder: 1, duration: 60
})
const chapterId = chapter.data?.id
record('A3-创建章节', !!chapterId, { id: chapterId })
out.chapterId = chapterId

const video = await api('POST', '/videos', teacher, {
  title: 'QA视频-第一课', chapterId, courseId, fileName: 'qa-video.mp4', duration: 120, sortOrder: 1,
  url: 'http://localhost:8089/uploads/videos/qa-video.mp4', m3u8Url: ''
})
const videoId = video.data?.id
record('A4-创建视频', !!videoId, { id: videoId })
out.videoId = videoId

// 4. 提交审核（应通过前置条件）
const submitted = await api('POST', `/courses/${courseId}/submit`, teacher)
record('A5-提交审核', submitted.code === 200, { code: submitted.code, msg: submitted.message })

// 5. 教务审核通过 + 管理员发布
const approved = await api('POST', `/courses/${courseId}/approve`, academic)
record('A6-教务审核通过', approved.code === 200, { code: approved.code, msg: approved.message })
const published = await api('POST', `/courses/${courseId}/publish`, admin)
record('A7-管理员发布', published.code === 200, { code: published.code, msg: published.message })

// 6. 学生选课
const enrolled = await api('POST', '/enrollments', student, { courseId, sourceChannel: 'SEARCH' })
record('A8-学生选课', enrolled.code === 200, { code: enrolled.code, msg: enrolled.message })

// 7. 学习进度：开始学习（创建进度）+ 查询
const progress = await api('POST', '/learning-progress/progress', student, {
  courseId, chapterId, videoProgress: 10, videoPosition: 12, totalWatchTime: 12
})
record('A9-创建学习进度', progress.code === 200, { code: progress.code, msg: progress.message })
const progressQuery = await api('GET', `/learning-progress/progress?courseId=${courseId}`, student)
record('A10-查询学习进度', progressQuery.code === 200 && Array.isArray(progressQuery.data), { count: progressQuery.data?.length })

// 8. 教师创建题目 + 练习（供答题页深测）
const question = await api('POST', '/questions', teacher, {
  courseId, teacherId: 2, questionType: 'SINGLE',
  content: 'QA数据流单选题：1+1=?', options: JSON.stringify(['1', '2', '3', '4']), answer: '2',
  score: 10, explanation: '基础运算', difficulty: 1, status: 1, chapterIds: [chapterId]
})
const questionId = question.data?.id
record('A11-创建题目', !!questionId, { id: questionId })
out.questionId = questionId

const exercise = await api('POST', '/exercises', teacher, {
  courseId, chapterId, title: 'QA第一章练习', passScore: 60, timeLimit: 10, maxAttempts: 3,
  questions: [{ questionId, score: 10 }], isExam: false
})
const exerciseId = exercise.data?.id
record('A12-创建练习', !!exerciseId, { id: exerciseId })
out.exerciseId = exerciseId

const fs = await import('node:fs/promises')
await fs.writeFile('/tmp/qa-data-flow.json', JSON.stringify(out, null, 2))
console.log('SAVED /tmp/qa-data-flow.json ' + JSON.stringify(out))
