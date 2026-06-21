// P3-3（Phase C-3）：前端 bundle 体积门禁
// ----------------------------------------------------------------------------
// 起始阈值保守：单个 JS chunk ≤ 1.5MB。
// 当前最大 chunk：dist/assets/vendor-element-*.js ≈ 1.10MB（element-plus 全量）。
//   故阈值设 1.5MB 留缓冲，避免现有产物立即挂红（UX 零退化硬约束 #1 / #5）。
// 渐进收紧路线：
//   Phase D  → 1.0MB（需先做 element-plus 按需引入 / 进一步 code split）
//   Phase E  → 0.5MB
// 说明：本项目 package.json "type":"module"，故使用 ESM 语法（import）。
// ----------------------------------------------------------------------------
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const distDir = path.join(__dirname, '..', 'dist', 'assets')

if (!fs.existsSync(distDir)) {
  console.error('❌ dist/assets 不存在，请先运行 `npm run build`')
  process.exit(1)
}

// P3-3 门禁：单个 JS 文件 ≤ 1.5MB（保守起步）
const MAX_SIZE = 1.5 * 1024 * 1024

const jsFiles = fs.readdirSync(distDir).filter((f) => f.endsWith('.js'))
let failed = false
let totalBytes = 0

for (const file of jsFiles) {
  const size = fs.statSync(path.join(distDir, file)).size
  totalBytes += size
  if (size > MAX_SIZE) {
    console.error(
      `❌ ${file} 超出 ${(MAX_SIZE / 1024 / 1024).toFixed(2)}MB ` +
        `(实际: ${(size / 1024 / 1024).toFixed(2)}MB)`,
    )
    failed = true
  } else {
    console.log(`✓ ${file}: ${(size / 1024).toFixed(2)}KB`)
  }
}

console.log(
  `\n总 JS 体积: ${(totalBytes / 1024 / 1024).toFixed(2)}MB，文件数: ${jsFiles.length}`,
)

if (failed) {
  console.error('\n❌ bundle-size 门禁未通过')
  process.exit(1)
}

console.log('✓ bundle-size 门禁通过')
