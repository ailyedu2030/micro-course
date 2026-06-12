#!/usr/bin/env node
/**
 * Token replacer for student views
 * Replaces hardcoded px values with design tokens
 */

const fs = require('fs')
const path = require('path')

const files = [
  'src/views/student/VideoPlayer.vue',
  'src/views/student/ExerciseTake.vue',
  'src/views/student/CourseDetail.vue',
  'src/views/student/MyCourses.vue',
  'src/views/student/WeeklyReport.vue',
  'src/views/student/CourseSquare.vue',
  'src/views/student/MyReviews.vue',
  'src/views/student/Profile.vue',
  'src/views/student/LearningCenter.vue',
]

// Replacement maps
const spacingMap = {
  'padding: 0 16px': 'padding: 0 var(--space-4)',
  'padding: 16px 20px': 'padding: var(--space-4) var(--space-5)',
  'padding: 16px': 'padding: var(--space-4)',
  'padding: 12px 16px': 'padding: var(--space-3) var(--space-4)',
  'padding: 0 12px': 'padding: 0 var(--space-3)',
  'padding: 12px 12px': 'padding: var(--space-3)',
  'padding: 12px;': 'padding: var(--space-3);',
  'padding: 16px;': 'padding: var(--space-4);',
  'padding: 20px 24px': 'padding: var(--space-5) var(--space-6)',
  'padding: 20px': 'padding: var(--space-5)',
  'padding: 10px 12px': 'padding: var(--space-3) var(--space-3)',
  'padding: 10px 16px': 'padding: var(--space-3) var(--space-4)',
  'padding: 14px 16px': 'padding: var(--space-3) var(--space-4)',
  'padding: 8px 12px': 'padding: var(--space-2) var(--space-3)',
  'padding: 8px': 'padding: var(--space-2)',
  'padding: 6px 12px': 'padding: var(--space-2) var(--space-3)',
  'padding: 6px': 'padding: var(--space-2)',
  'padding: 4px 8px': 'padding: var(--space-1) var(--space-2)',
  'padding: 4px': 'padding: var(--space-1)',
  'padding: 2px 6px': 'padding: 2px var(--space-2)',
  'padding: 24px': 'padding: var(--space-6)',
  'padding: 24px 16px': 'padding: var(--space-6) var(--space-4)',
  'padding: 60px 0': 'padding: var(--space-8) 0',
  'padding: 32px 16px': 'padding: var(--space-7) var(--space-4)',
  'padding: 32px 0': 'padding: var(--space-7) 0',
  'padding: 0 48px': 'padding: 0 var(--space-8)',
  'gap: 8px': 'gap: var(--space-2)',
  'gap: 6px': 'gap: var(--space-2)',
  'gap: 12px': 'gap: var(--space-3)',
  'gap: 16px': 'gap: var(--space-4)',
  'gap: 4px': 'gap: var(--space-1)',
  'gap: 20px': 'gap: var(--space-5)',
  'gap: 24px': 'gap: var(--space-6)',
  'gap: 10px': 'gap: var(--space-3)',
  'gap: 14px': 'gap: var(--space-3)',
  'margin-bottom: 12px': 'margin-bottom: var(--space-3)',
  'margin-bottom: 16px': 'margin-bottom: var(--space-4)',
  'margin-bottom: 20px': 'margin-bottom: var(--space-5)',
  'margin-bottom: 24px': 'margin-bottom: var(--space-6)',
  'margin-bottom: 32px': 'margin-bottom: var(--space-7)',
  'margin-bottom: 8px': 'margin-bottom: var(--space-2)',
  'margin-bottom: 4px': 'margin-bottom: var(--space-1)',
  'margin-bottom: 0': 'margin-bottom: 0',
  'margin: 0 0 12px': 'margin: 0 0 var(--space-3)',
  'margin: 0 0 16px': 'margin: 0 0 var(--space-4)',
  'margin: 0 0 20px': 'margin: 0 0 var(--space-5)',
  'margin: 0 0 4px': 'margin: 0 0 var(--space-1)',
  'margin: 0 0 8px': 'margin: 0 0 var(--space-2)',
  'margin: 0 0 24px': 'margin: 0 0 var(--space-6)',
  'margin: 0 0 6px': 'margin: 0 0 var(--space-2)',
  'margin: 0 0 2px': 'margin: 0 0 2px',
  'margin: 0 0 14px': 'margin: 0 0 var(--space-3)',
  'margin: 0 0 32px': 'margin: 0 0 var(--space-7)',
  'margin: 0 0 10px': 'margin: 0 0 var(--space-3)',
  'margin: 12px 0 0': 'margin: var(--space-3) 0 0',
  'margin: 16px 0 0': 'margin: var(--space-4) 0 0',
  'margin: 8px 0 0': 'margin: var(--space-2) 0 0',
  'margin: 4px 0 0': 'margin: var(--space-1) 0 0',
  'margin: 24px 0': 'margin: var(--space-6) 0',
  'margin: 0 auto 24px': 'margin: 0 auto var(--space-6)',
  'margin: 0 auto 16px': 'margin: 0 auto var(--space-4)',
  'margin: 0 auto 12px': 'margin: 0 auto var(--space-3)',
  'margin: 0 auto 20px': 'margin: 0 auto var(--space-5)',
  'margin: 0 auto 8px': 'margin: 0 auto var(--space-2)',
  'margin: 0 auto': 'margin: 0 auto',
  'margin-bottom: 0': 'margin-bottom: 0',
  'margin-top: 16px': 'margin-top: var(--space-4)',
  'margin-top: 12px': 'margin-top: var(--space-3)',
  'margin-top: 20px': 'margin-top: var(--space-5)',
  'margin-top: 24px': 'margin-top: var(--space-6)',
  'margin-top: 8px': 'margin-top: var(--space-2)',
  'margin-top: 4px': 'margin-top: var(--space-1)',
  'margin-top: 6px': 'margin-top: var(--space-2)',
  'margin-top: 10px': 'margin-top: var(--space-3)',
  'margin-left: 8px': 'margin-left: var(--space-2)',
  'margin-left: 12px': 'margin-left: var(--space-3)',
  'margin-left: 16px': 'margin-left: var(--space-4)',
  'margin-left: 4px': 'margin-left: var(--space-1)',
  'margin-right: 8px': 'margin-right: var(--space-2)',
  'margin-right: 12px': 'margin-right: var(--space-3)',
  'margin-right: 4px': 'margin-right: var(--space-1)',
  'margin: var(--space-2) 0 0': 'margin: var(--space-2) 0 0',
  'margin: calc(var(--space-4) * -1) var(--space-5) var(--space-4)': 'margin: calc(var(--space-4) * -1) var(--space-5) var(--space-4)',
  'min-width: 0': 'min-width: 0',
  'width: 48px': 'width: 48px',
  'height: 48px': 'height: 48px',
  'width: 36px': 'width: 36px',
  'height: 36px': 'height: 36px',
  'width: 32px': 'width: 32px',
  'height: 32px': 'height: 32px',
  'width: 28px': 'width: 28px',
  'height: 28px': 'height: 28px',
  'width: 24px': 'width: 24px',
  'height: 24px': 'height: 24px',
  'width: 20px': 'width: 20px',
  'height: 20px': 'height: 20px',
  'width: 16px': 'width: 16px',
  'height: 16px': 'height: 16px',
  'width: 14px': 'width: 14px',
  'height: 14px': 'height: 14px',
  'width: 12px': 'width: 12px',
  'height: 12px': 'height: 12px',
  'width: 80px': 'width: 80px',
  'width: 60px': 'width: 60px',
  'width: 72px': 'width: 72px',
  'width: 40px': 'height: 40px',
  'height: 40px': 'height: 40px',
  'top: 48px': 'top: 48px',
  'top: 80px': 'top: 80px',
  'top: 60px': 'top: 60px',
  'top: 0': 'top: 0',
  'z-index: 100': 'z-index: 100',
  'z-index: 10': 'z-index: 10',
  'z-index: 9999': 'z-index: 9999',
  'border-bottom: 1px solid': 'border-bottom: 1px solid',
  'border-left: 1px solid': 'border-left: 1px solid',
  'border-top: 1px solid': 'border-top: 1px solid',
  'border-right: 1px solid': 'border-right: 1px solid',
  'border: 1px solid': 'border: 1px solid',
  'border: 2px solid': 'border: 2px solid',
  'border: 3px solid': 'border: 3px solid',
  'height: 120px': 'height: 120px',
  'height: 80px': 'height: 80px',
  'height: 220px': 'height: 220px',
  'height: 180px': 'height: 180px',
  'height: 140px': 'height: 140px',
  'height: 160px': 'height: 160px',
  'height: 56px': 'height: 56px',
  'max-width: 1280px': 'max-width: 1280px',
  'max-width: 1200px': 'max-width: 1200px',
  'max-width: 960px': 'max-width: 960px',
  'max-width: 600px': 'max-width: 600px',
  'width: 360px': 'width: 360px',
  'width: 280px': 'width: 280px',
  'width: 240px': 'width: 240px',
  'width: 200px': 'width: 200px',
  'width: 140px': 'width: 140px',
  'left: 50%': 'left: 50%',
  'right: 0': 'right: 0',
  'right: -6px': 'right: -6px',
  'bottom: 0': 'bottom: 0',
  'bottom: 80px': 'bottom: 80px',
  'inset: 0': 'inset: 0',
  'max-height: 400px': 'max-height: 400px',
  'max-height: 300px': 'max-height: 300px',
  'max-height: 260px': 'max-height: 260px',
  'min-height: 2.8em': 'min-height: 2.8em',
  'height: 2.8em': 'height: 2.8em',
}

const radiusMap = {
  'border-radius: 4px': 'border-radius: var(--radius-sm)',
  'border-radius: 8px': 'border-radius: var(--radius-md)',
  'border-radius: 12px': 'border-radius: var(--radius-lg)',
  'border-radius: 16px': 'border-radius: var(--radius-xl)',
  'border-radius: 20px': 'border-radius: var(--radius-2xl)',
  'border-radius: 50%': 'border-radius: var(--radius-circle)',
  'border-radius: 9999px': 'border-radius: var(--radius-pill)',
  'border-radius: 3px': 'border-radius: var(--radius-sm)',
  'border-radius: 2px': 'border-radius: var(--radius-sm)',
  'border-radius: 6px': 'border-radius: var(--radius-sm)',
  'border-radius: 9px': 'border-radius: 9px',
  'border-radius: 10px': 'border-radius: 10px',
  'border-radius: 14px': 'border-radius: 14px',
  'border-radius: 0': 'border-radius: 0',
  'border-radius: 36px': 'border-radius: 36px',
  'border-radius: 60px': 'border-radius: 60px',
}

const fontSizeMap = {
  'font-size: 12px': 'font-size: var(--text-xs)',
  'font-size: 13px': 'font-size: var(--text-sm)',
  'font-size: 14px': 'font-size: var(--text-base)',
  'font-size: 16px': 'font-size: var(--text-md)',
  'font-size: 18px': 'font-size: var(--text-lg)',
  'font-size: 20px': 'font-size: var(--text-xl)',
  'font-size: 24px': 'font-size: var(--text-2xl)',
  'font-size: 32px': 'font-size: var(--text-3xl)',
  'font-size: 36px': 'font-size: 36px',
  'font-size: 22px': 'font-size: 22px',
  'font-size: 11px': 'font-size: 11px',
  'font-size: 10px': 'font-size: 10px',
  'font-size: 48px': 'font-size: 48px',
  'font-size: 15px': 'font-size: 15px',
}

const fontWeightMap = {
  'font-weight: 400': 'font-weight: var(--weight-regular)',
  'font-weight: 500': 'font-weight: var(--weight-medium)',
  'font-weight: 600': 'font-weight: var(--weight-semibold)',
  'font-weight: 700': 'font-weight: var(--weight-bold)',
}

const lineHeightMap = {
  'line-height: 1': 'line-height: 1',
  'line-height: 1.5': 'line-height: var(--leading-normal)',
  'line-height: 1.6': 'line-height: var(--leading-relaxed)',
  'line-height: 1.8': 'line-height: 1.8',
  'line-height: 2': 'line-height: 2',
  'line-height: 1.2': 'line-height: 1.2',
  'line-height: 1.4': 'line-height: 1.4',
  'line-height: 1.25': 'line-height: var(--leading-tight)',
  'line-height: 1.375': 'line-height: var(--leading-snug)',
}

function applyReplacements(content, filename) {
  let count = 0

  // Line height
  for (const [from, to] of Object.entries(lineHeightMap)) {
    if (content.includes(from)) {
      content = content.split(from).join(to)
      count += (content.split(to).length - 1)
    }
  }

  // Font weight
  for (const [from, to] of Object.entries(fontWeightMap)) {
    if (content.includes(from)) {
      content = content.split(from).join(to)
      count += (content.split(to).length - 1)
    }
  }

  // Font size
  for (const [from, to] of Object.entries(fontSizeMap)) {
    if (content.includes(from)) {
      content = content.split(from).join(to)
      count += (content.split(to).length - 1)
    }
  }

  // Border radius
  for (const [from, to] of Object.entries(radiusMap)) {
    if (content.includes(from)) {
      content = content.split(from).join(to)
      count += (content.split(to).length - 1)
    }
  }

  // Spacing - handle multi-value padding/margin first (more specific)
  // Then handle single values
  const multiValues = [
    ['padding: 0 16px', 'padding: 0 var(--space-4)'],
    ['padding: 16px 20px', 'padding: var(--space-4) var(--space-5)'],
    ['padding: 12px 16px', 'padding: var(--space-3) var(--space-4)'],
    ['padding: 24px 16px', 'padding: var(--space-6) var(--space-4)'],
    ['padding: 10px 12px', 'padding: var(--space-3) var(--space-3)'],
    ['padding: 8px 12px', 'padding: var(--space-2) var(--space-3)'],
    ['padding: 14px 16px', 'padding: var(--space-3) var(--space-4)'],
    ['padding: 0 48px', 'padding: 0 var(--space-8)'],
    ['padding: 32px 16px', 'padding: var(--space-7) var(--space-4)'],
    ['padding: 32px 0', 'padding: var(--space-7) 0'],
    ['padding: 0 12px', 'padding: 0 var(--space-3)'],
    ['padding: 60px 0', 'padding: var(--space-8) 0'],
    ['padding: 20px 24px', 'padding: var(--space-5) var(--space-6)'],
    ['padding: 16px;', 'padding: var(--space-4);'],
    ['padding: 12px;', 'padding: var(--space-3);'],
    ['padding: 8px;', 'padding: var(--space-2);'],
    ['padding: 4px;', 'padding: var(--space-1);'],
    ['padding: 6px 12px', 'padding: var(--space-2) var(--space-3)'],
    ['padding: 4px 8px', 'padding: var(--space-1) var(--space-2)'],
    ['padding: 0 16px;', 'padding: 0 var(--space-4);'],
    ['margin: 0 0 12px', 'margin: 0 0 var(--space-3)'],
    ['margin: 0 0 16px', 'margin: 0 0 var(--space-4)'],
    ['margin: 0 0 4px', 'margin: 0 0 var(--space-1)'],
    ['margin: 0 0 8px', 'margin: 0 0 var(--space-2)'],
    ['margin: 0 0 6px', 'margin: 0 0 var(--space-2)'],
    ['margin: 0 0 24px', 'margin: 0 0 var(--space-6)'],
    ['margin: 0 0 2px', 'margin: 0 0 2px'],
    ['margin: 0 0 32px', 'margin: 0 0 var(--space-7)'],
    ['margin: 0 0 14px', 'margin: 0 0 var(--space-3)'],
    ['margin: 0 0 20px', 'margin: 0 0 var(--space-5)'],
    ['margin: 0 0 10px', 'margin: 0 0 var(--space-3)'],
    ['margin: 12px 0 0', 'margin: var(--space-3) 0 0'],
    ['margin: 16px 0 0', 'margin: var(--space-4) 0 0'],
    ['margin: 8px 0 0', 'margin: var(--space-2) 0 0'],
    ['margin: 4px 0 0', 'margin: var(--space-1) 0 0'],
    ['margin: 0 auto 24px', 'margin: 0 auto var(--space-6)'],
    ['margin: 0 auto 16px', 'margin: 0 auto var(--space-4)'],
    ['margin: 0 auto 12px', 'margin: 0 auto var(--space-3)'],
    ['margin: 0 auto 20px', 'margin: 0 auto var(--space-5)'],
    ['margin: 0 auto 8px', 'margin: 0 auto var(--space-2)'],
    ['margin: 24px 0', 'margin: var(--space-6) 0'],
    ['margin-bottom: 12px', 'margin-bottom: var(--space-3)'],
    ['margin-bottom: 16px', 'margin-bottom: var(--space-4)'],
    ['margin-bottom: 20px', 'margin-bottom: var(--space-5)'],
    ['margin-bottom: 24px', 'margin-bottom: var(--space-6)'],
    ['margin-bottom: 8px', 'margin-bottom: var(--space-2)'],
    ['margin-bottom: 4px', 'margin-bottom: var(--space-1)'],
    ['margin-bottom: 0', 'margin-bottom: 0'],
    ['margin-top: 16px', 'margin-top: var(--space-4)'],
    ['margin-top: 12px', 'margin-top: var(--space-3)'],
    ['margin-top: 20px', 'margin-top: var(--space-5)'],
    ['margin-top: 8px', 'margin-top: var(--space-2)'],
    ['margin-top: 4px', 'margin-top: var(--space-1)'],
    ['margin-top: 6px', 'margin-top: var(--space-2)'],
    ['margin-top: 10px', 'margin-top: var(--space-3)'],
    ['margin-left: 8px', 'margin-left: var(--space-2)'],
    ['margin-left: 12px', 'margin-left: var(--space-3)'],
    ['margin-left: 16px', 'margin-left: var(--space-4)'],
    ['margin-left: 4px', 'margin-left: var(--space-1)'],
    ['margin-right: 8px', 'margin-right: var(--space-2)'],
    ['margin-right: 12px', 'margin-right: var(--space-3)'],
    ['margin-right: 4px', 'margin-right: var(--space-1)'],
    ['gap: 8px', 'gap: var(--space-2)'],
    ['gap: 6px', 'gap: var(--space-2)'],
    ['gap: 12px', 'gap: var(--space-3)'],
    ['gap: 16px', 'gap: var(--space-4)'],
    ['gap: 4px', 'gap: var(--space-1)'],
    ['gap: 20px', 'gap: var(--space-5)'],
    ['gap: 24px', 'gap: var(--space-6)'],
    ['gap: 10px', 'gap: var(--space-3)'],
    ['gap: 14px', 'gap: var(--space-3)'],
  ]

  for (const [from, to] of multiValues) {
    if (content.includes(from)) {
      content = content.split(from).join(to)
      count += (content.split(to).length - 1)
    }
  }

  // Single-value spacing
  const singleSpacing = [
    ['padding: 16px', 'padding: var(--space-4)'],
    ['padding: 12px', 'padding: var(--space-3)'],
    ['padding: 24px', 'padding: var(--space-6)'],
    ['padding: 20px', 'padding: var(--space-5)'],
    ['padding: 8px', 'padding: var(--space-2)'],
    ['padding: 6px', 'padding: var(--space-2)'],
    ['padding: 4px', 'padding: var(--space-1)'],
    ['padding: 10px', 'padding: var(--space-3)'],
    ['padding: 14px', 'padding: var(--space-3)'],
    ['padding: 2px', 'padding: 2px'],
    ['padding: 3px', 'padding: 3px'],
    ['padding: 0', 'padding: 0'],
    ['padding: 32px', 'padding: var(--space-7)'],
    ['padding: 48px', 'padding: var(--space-8)'],
  ]

  for (const [from, to] of singleSpacing) {
    if (content.includes(from)) {
      content = content.split(from).join(to)
      count += (content.split(to).length - 1)
    }
  }

  return { content, count }
}

const baseDir = path.join(__dirname, '..')

for (const file of files) {
  const filepath = path.join(baseDir, file)
  if (!fs.existsSync(filepath)) {
    console.log(`SKIP (not found): ${file}`)
    continue
  }

  const original = fs.readFileSync(filepath, 'utf8')
  const { content, count } = applyReplacements(original, file)

  if (count > 0) {
    fs.writeFileSync(filepath, content, 'utf8')
    console.log(`✓ ${file}: ${count} replacements`)
  } else {
    console.log(`○ ${file}: no changes`)
  }
}