# 微专业课程广场专区 · 设计规范

> 基于 ui-ux-pro-max 设计体系
> 平台：Web (Vue 3 + Element Plus)
> 可访问性：WCAG AA
> 设计日期：2026-06-23

## 颜色系统

| Token | 值 | 用途 |
|-------|-----|------|
| `--ms-primary` | `#6366f1` | 专区主色（紫） |
| `--ms-primary-light` | `#818cf8` | 专区悬浮色 |
| `--ms-bg` | `linear-gradient(135deg, #6366f1 0%, #8b5cf6 50%, #a78bfa 100%)` | 专区背景渐变 |
| `--ms-card-bg` | `#ffffff` | 卡片背景 |
| `--ms-gold` | `#f59e0b` | 金标主推荐色 |
| `--ms-gold-light` | `#fbbf24` | 金标光晕动画 |
| `--ms-text` | `#1e293b` | 正文色 |
| `--ms-text-secondary` | `#64748b` | 辅助色 |
| `--ms-new-badge` | `#10b981` | NEW 徽章绿 |

## 专区布局（CourseSquare 插入位置）

```
Hero → [★ 微专业专区] → 筛选卡 → 精选推荐 → 课程套件 → 全部课程
```

## 组件设计令牌

### 金标主推卡 (MicroSpecialtyGoldCard)
- 尺寸：402px × 280px（主卡） / 260px × 180px（副卡）
- 背景：白色 + 金色 2px 边框
- 金标：右上角 45° 金色角标，`background: linear-gradient(135deg, #f59e0b, #fbbf24)`，文字 "🔥 学校重点推荐"
- 内容：封面（60%高度）/ 标题 / 学院 / 负责人 / 学分 / 教师数 / 课程数
- CTA: "立即了解 →" 按钮
- 悬浮：上升 4px + 阴影增大

### 常规卡 (MicroSpecialtyCard) 
- 尺寸：248px × 210px
- 背景：白色 + 8px 圆角 + 2px 阴影
- 内容：封面（50%高度）/ 标题 / 学院 / 学分 / 质量分热度条
- NEW 角标：新微专业（保护期 7 天内）显示绿色 NEW 徽章
- 质量分热度条：0-10 分，渐变色 `#ef4444 → #f59e0b → #22c55e`

### 专区容器
- 全宽背景渐变（紫）
- 内边距：`padding: 32px 40px`
- 标题 "🎯 微专业 · 学校重点培养项目" font-size 22px, color white, weight 700
- 副标题 "修读多门课程获得微专业结业证书" font-size 14px, color rgba(255,255,255,0.85)
- 横滑容器：`display: flex; overflow-x: auto; gap: 16px; scroll-snap-type: x mandatory;`

### 响应式断点

| 断点 | 卡片列数 |
|------|---------|
| ≥ 1200px | 金标 1 大卡 + 4 常规卡 |
| 768-1199px | 金标 1 小卡 + 2 常规卡 |
| < 768px | 金标 1 小卡 + 1 常规卡（横滑） |

### 空态
"暂无微专业项目，敬请期待" + 插画（Element Plus `<el-empty>` 自定义 image）

### Loading 态
`<el-skeleton>` 仿卡片布局，3 张骨架卡片

### Error 态
`<el-result status="error" title="加载失败" sub-title="请检查网络后重试">` + 重试按钮

### 过渡动画
- 卡片入场：`animation: fadeInUp 0.4s ease`
- 横滑滚动：`scroll-behavior: smooth`
- 悬浮：`transition: all 0.2s ease`
