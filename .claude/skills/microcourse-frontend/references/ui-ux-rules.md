# UI/UX 规则（摘自 ui-ux-pro-max）

> 本文件提取 [ui-ux-pro-max] skill 中适用于 Element Plus 管理后台的规则子集
> 所有前端页面必须遵守

## 1. 色彩系统

| 用途 | 色值 | Element Plus token |
|------|------|-------------------|
| 主色 | #409eff | --el-color-primary |
| 主色 hover | #337ecc | --el-color-primary-light-3 |
| 成功 | #67c23a | --el-color-success |
| 警告 | #e6a23c | --el-color-warning |
| 危险 | #f56c6c | --el-color-danger |
| 信息 | #909399 | --el-color-info |
| 背景 | #f0f2f5 |（主内容区背景） |
| 卡片 | #ffffff |（卡片/表格背景） |
| 侧边栏 | #304156 |（暗色侧边栏） |

## 2. 间距系统（4px 基础）

| 场景 | 间距 | 用途 |
|------|------|------|
| 4px | 图标与文字间距 | |
| 8px | 表单 item 内部间距 | |
| 12px | 按钮组间距 | |
| 16px | 卡片内 padding、表单 item 间距 | |
| 20px | 卡片间距（上下） | |
| 24px | 页面主内容区 padding | |
| 32px | 大区块分隔 | |

## 3. 字体/排版

| 层级 | 大小 | 用途 | 字重 |
|------|------|------|------|
| 页面标题 | 20px | 卡片外大标题 | 600 |
| 卡片标题 | 16px | 表格上方文字 | 500 |
| 正文 | 14px | 表格内容、表单标签、按钮文字 | 400 |
| 辅助 | 12px | 提示文字、时间戳 | 400 |

## 4. 圆角

| 组件 | radius |
|------|--------|
| el-card | 8px |
| el-button | 4px |
| el-input / el-select | 4px |
| el-dialog | 8px |
| el-tag | 4px |
| el-pagination button | 4px |

## 5. 过渡动画

| 场景 | 时长 | 缓动 |
|------|------|------|
| 侧边栏折叠 | 0.3s | ease |
| hover 效果 | 0.2s | ease |
| 弹窗打开/关闭 | 0.3s | ease |
| loading 出现 | 0.2s | ease |

## 6. 可访问性

| 规则 | 实现 |
|------|------|
| 对比度 ≥ 4.5:1 | Element Plus 默认主题满足 |
| 焦点环可见 | 不覆盖默认 :focus-visible |
| 图标按钮加 aria-label | `<el-button aria-label="删除">` |
| 表格排序可键盘操作 | el-table sortable 默认支持 |

## 7. 响应式

| 断点 | 行为 |
|------|------|
| ≥ 1280px | 完整侧边栏 + 表格全列 |
| 1024-1279px | 折叠侧边栏 |
| < 1024px | **不支持**（管理后台最小宽度 1280px） |

## 8. 禁止项

- ❌ 不使用内联样式（`style="..."`），用 scoped CSS class
- ❌ 不使用 `!important`
- ❌ 文字颜色不用纯黑 #000 / 纯白 #fff（用 #333 / #f5f5f5）
- ❌ 表单不用 placeholder 代替 label
- ❌ 弹窗宽度超过 600px（除非表格嵌入弹窗）
- ❌ 表格不用固定高度（用 max-height 自适应）
