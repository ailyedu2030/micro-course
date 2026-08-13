// 微课平台 · i18n 完整性扫描（增强版）
// 用法: node scripts/check-i18n-keys.mjs [src/i18n/zh-CN.js] [src/i18n/en-US.js] [--report-only]
// 检测维度：
//   1. $t() 引用的 key 在 zh-CN/en-US 双语必须存在（缺失 → 报错）
//   2. Vue/JS 文件中含中文用户可见字符串字面量（应改用 $t()）→ 报错
//   2.x 例外：注释（// 或 /* */）、import/require
import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { execSync } from 'node:child_process'

const REPO = process.cwd()
const ROOT = process.argv[2] || join(REPO, 'micro-course-admin')
const zhPath = process.argv[3] || join(ROOT, 'src/i18n/zh-CN.js')
const enPath = process.argv[4] || join(ROOT, 'src/i18n/en-US.js')

function loadKeys(path) {
  const lines = readFileSync(path, 'utf8').split('\n')
  const flat = new Set()
  const stack = []
  for (const line of lines) {
    const indent = (line.match(/^(\s*)/) || ['', ''])[1].length
    const content = line.trim()
    if (!content) continue
    const seg = content.match(/^(\w+):\s*\{/)
    const kv = content.match(/^(\w+):\s*'([^']*)'/)
    if (seg) {
      while (stack.length && stack[stack.length - 1].indent >= indent) stack.pop()
      stack.push({ indent, key: seg[1] })
    } else if (kv) {
      while (stack.length && stack[stack.length - 1].indent >= indent) stack.pop()
      flat.add(stack.map(s => s.key).concat(kv[1]).join('.'))
    }
  }
  return flat
}

const files = execSync("find src -name '*.vue' -o -name '*.js'", { cwd: ROOT })
  .toString().trim().split('\n').filter(Boolean)
  .filter(f => !f.includes('__tests__'))

// 维度 1: $t() 引用 key 完整性
const used = new Set()
for (const f of files) {
  const s = readFileSync(join(ROOT, f), 'utf8')
  const re = /\$t\('([^']+)'\)/g
  let m
  while ((m = re.exec(s))) used.add(m[1])
}

const zh = loadKeys(zhPath)
const en = loadKeys(enPath)
const missingZh = [...used].filter(k => !zh.has(k)).sort()
const missingEn = [...used].filter(k => !en.has(k)).sort()

// 维度 2: 用户可见中文硬编码检测（行级，字符串字面量中含中文）
const STR_LIT_RE = /(['"])((?:\\.|(?!\1).)*?[\u4e00-\u9fa5][^'"\\]*?)\1/g

const cnHits = []
for (const f of files) {
  if (f.includes('src/i18n/')) continue
  const raw = readFileSync(join(ROOT, f), 'utf8')
  const lines = raw.split('\n')
  // 找出块注释区间（/* ... */），跳过
  const blockRanges = []
  let inBlock = false, bs = -1
  for (let i = 0; i < lines.length; i++) {
    const l = lines[i]
    if (!inBlock && /\/\*/.test(l)) { inBlock = true; bs = i }
    if (inBlock) {
      if (/\*\//.test(l)) { blockRanges.push([bs + 1, i + 1]); inBlock = false; bs = -1 }
    }
  }
  const inBlockLine = (n) => blockRanges.some(([s, e]) => n >= s && n <= e)

  for (let i = 0; i < lines.length; i++) {
    if (inBlockLine(i + 1)) continue
    let l = lines[i]
    // 去掉行内 // 注释
    const ci = l.indexOf('//')
    if (ci >= 0) l = l.slice(0, ci)
    for (const m of l.matchAll(STR_LIT_RE)) {
      const v = m[2]
      if (v.length < 2) continue
      // 排除 import 路径
      if (m.index > 0 && l.slice(0, m.index).match(/import\s+/)) continue
      // 排除 i18n key 引用（labelKey: 'menu.数据总览' / aria-label 中 $t(...) 拼接）
      if (/^\w+:\s*['"]/.test(l.slice(0, m.index).trim()) && /Key|key/i.test(l.slice(0, m.index).slice(-15))) continue
      // 排除 aria-label 中 $t() 拼接的 fallback（如 aria-label="$t(...) + record.title" 中含的中文为外层文案）
      // 简化：排除已用 $t() 模板的字符串行（这种行内若含中文通常是变量名）
      cnHits.push({ file: f, line: i + 1, value: v, ctx: lines[i].trim().slice(0, 120) })
    }
  }
}

console.log('=== i18n 完整性扫描 ===')
console.log(`used keys: ${used.size}`)
console.log(`MISSING zh: ${JSON.stringify(missingZh)}`)
console.log(`MISSING en: ${JSON.stringify(missingEn)}`)
console.log('\n=== 硬编码中文扫描 ===')
console.log(`中文硬编码行数: ${cnHits.length}`)
const byFile = {}
for (const h of cnHits) byFile[h.file] = (byFile[h.file] || 0) + 1
const topFiles = Object.entries(byFile).sort((a, b) => b[1] - a[1]).slice(0, 15)
console.log('Top 15 文件:')
for (const [f, n] of topFiles) console.log(`  ${n.toString().padStart(4)}  ${f}`)

const reportOnly = process.argv.includes('--report-only')
if (reportOnly) {
  console.log('\n[report-only 模式] 仅输出报告，不阻断')
  process.exit(0)
}

const hasError = missingZh.length + missingEn.length > 0 || cnHits.length > 0
if (hasError) {
  console.log('\n❌ i18n 治理门禁不通过')
  process.exit(1)
}
console.log('\n✅ i18n 治理门禁通过')
process.exit(0)