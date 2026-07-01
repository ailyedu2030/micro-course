# PDF 生成字体

## 安装

将中文字体文件复制到此目录。支持以下格式：

- `Songti.ttc` — 宋体（macOS 系统字体 `/System/Library/Fonts/Supplemental/Songti.ttc`）
- `STHeitiMedium.ttc` — 黑体（macOS 系统字体）

```bash
cp /System/Library/Fonts/Supplemental/Songti.ttc src/main/resources/fonts/
```

> **注意：** 字体文件受版权保护且体积较大（~50MB），不提交到 Git 仓库。
> 字体缺失时，PDF 生成器自动回退到内置 HELVETICA 字体（中文不渲染但程序不崩溃）。
