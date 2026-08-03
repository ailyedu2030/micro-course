// 微课平台 · i18n 缺失键扫描
// 用法: node scripts/check-i18n-keys.mjs [src/i18n/zh-CN.js] [src/i18n/en-US.js]
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
  const stack = [] // [{ indent, key }]
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
console.log('used keys:', used.size)
console.log('MISSING zh:', JSON.stringify(missingZh))
console.log('MISSING en:', JSON.stringify(missingEn))
process.exit(missingZh.length + missingEn.length > 0 ? 1 : 0)
