# 微课管理平台 · 设计系统 v1.0

## 色彩系统

| 用途 | 色值 | Element Plus Token |
|------|------|--------------------|
| 主色 | `#409eff` | `--el-color-primary` |
| 主色深色 | `#337ecc` | `--el-color-primary-dark-2` |
| 主色浅色 | `#ecf5ff` | `--el-color-primary-light-9` |
| 成功 | `#67c23a` | `--el-color-success` |
| 警告 | `#e6a23c` | `--el-color-warning` |
| 危险 | `#f56c6c` | `--el-color-danger` |
| 信息 | `#909399` | `--el-color-info` |
| 页面背景 | `#f0f2f5` | 主内容区 |
| 卡片背景 | `#ffffff` | 卡片/表格 |
| 侧边栏背景 | `#304156` | 暗色侧边栏 |
| 侧边栏文字 | `#bfcbd9` | 侧边栏菜单文字 |
| 侧边栏激活 | `#409eff` | 侧边栏激活项 |
| 主文字 | `#303133` | 主要文字 |
| 常规文字 | `#606266` | 正文 |
| 次要文字 | `#909399` | 辅助文字 |
| 占位文字 | `#c0c4cc` | 输入框占位 |

## 间距系统（4px 基线）

| Token | 值 | 用途 |
|-------|-----|------|
| `--space-xs` | 4px | 图标与文字 |
| `--space-sm` | 8px | 表单内部 |
| `--space-md` | 12px | 按钮组 |
| `--space-base` | 16px | 卡片 padding |
| `--space-lg` | 20px | 卡片间距 |
| `--space-xl` | 24px | 页面 padding |
| `--space-2xl` | 32px | 大区块 |
| `--space-3xl` | 48px+ | 大标题下边距 |

## 阴影层级

| Token | 值 | 用途 |
|-------|-----|------|
| `--shadow-sm` | `0 1px 2px rgba(0,0,0,0.05)` | 细小分割 |
| `--shadow-md` | `0 2px 8px rgba(0,0,0,0.08)` | 卡片默认 |
| `--shadow-lg` | `0 4px 16px rgba(0,0,0,0.1)` | 弹窗/下拉 |
| `--shadow-xl` | `0 8px 24px rgba(0,0,0,0.12)` | 大弹窗 |

## 排版层级

| 层级 | 大小 | 字重 | 用途 |
|------|------|------|------|
| H1 | 24px | 700 | 页面大标题 |
| H2 | 20px | 600 | 区块标题 |
| H3 | 16px | 500 | 卡片标题 |
| Body | 14px | 400 | 正文/表格/按钮 |
| Small | 12px | 400 | 辅助文字/时间戳 |

## 圆角

| 组件 | 圆角 |
|------|------|
| el-card | 8px |
| el-button | 4px |
| el-input/select | 4px |
| el-dialog | 8px |
| el-tag | 4px |
| 头像 | 50% |

## 过渡动画

| 场景 | 时长 | 缓动 |
|------|------|------|
| hover 效果 | 200ms | ease |
| 侧边栏折叠 | 300ms | ease |
| 弹窗打开 | 300ms | ease |
| loading | 200ms | ease |
| 页面切换 | 300ms | ease |

## 布局规则

| 断点 | 行为 |
|------|------|
| ≥ 1280px | 完整侧边栏 + 表格全列 |
| 1024-1279px | 折叠侧边栏 (64px) |
| 768-1023px | 移动端 Drawer 菜单 |
| < 768px | 学生端单列布局 |

## 禁止项

- ❌ `style="..."` 内联样式 → 用 `class=""`
- ❌ `!important` → 用更高优先级选择器
- ❌ `color: #000` / `color: #fff` → 用 `#303133` / `#f5f5f5`
- ❌ placeholder 替代 label → 必须用 `<label>` 或 `aria-label`
- ❌ 弹窗宽度 > 600px → 表格嵌入除外
- ❌ 表格固定高度 → 用 `max-height`
- ❌ emoji 作为图标 → 用 Element Plus SVG 图标
