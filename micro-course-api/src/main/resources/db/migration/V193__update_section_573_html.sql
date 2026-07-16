-- V193: 更新 section 573 的 slide_page.html_content
-- 修复 B3 (R3 T3 根因): DB 存的是旧版本 41613 字节 HTML (无 AUDIO_SEG_NN_URL 占位符)
-- 本地文件 50920 字节, 15 个 AUDIO_SEG_NN_URL 占位符, 15 个 <audio> 标签
-- 部署后 toPageVO() line 624 contains("AUDIO_SEG_") 会返回 true, 占位符替换逻辑触发
-- 配合 B1 multipart.location 修复后, getPages 返回 htmlContent 长度 = 50920

UPDATE slide_pages
   SET html_content = '<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>1.1 24 倍效率从何而来</title>
<style>
  * { box-sizing: border-box; margin: 0; padding: 0; }

  html, body {
    width: 100%; height: 100%;
    background: #050505;
    color: #f5f5f5;
    font-family: -apple-system, "PingFang SC", "Microsoft YaHei", sans-serif;
    overflow: hidden;
  }

  /* 16:9 舞台,加圆角让屏幕有"机器"质感 */
  .stage {
    position: absolute;
    top: 50%; left: 50%;
    transform: translate(-50%, -50%);
    width: min(100vw, 177.78vh);
    height: min(56.25vw, 100vh);
    background: #0a0a0a;
    overflow: hidden;
    border-radius: 12px;
    box-shadow: 0 30px 80px rgba(0,0,0,0.6);
  }

  /* slide 默认隐藏 */
  .slide {
    position: absolute;
    inset: 0;
    display: none;
    overflow: hidden;
  }
  .slide.active { display: block; }

  /* ====== 通用: 左侧大图标/数字 + 右侧文字 ====== */
  .split {
    display: grid;
    grid-template-columns: 1fr 1.2fr;
    height: 100%;
    padding: 60px 80px 100px;
    gap: 60px;
    align-items: center;
  }
  .split.full { grid-template-columns: 1fr; padding: 80px; }
  .split.right-heavy { grid-template-columns: 1fr 1.5fr; }

  /* 单页居中布局 (slide 3/8/10/14) */
  .center-stage {
    position: absolute;
    inset: 0;
    display: flex;
    flex-direction: column;
    justify-content: center;
    align-items: center;
    padding: 60px 80px 100px;
    text-align: center;
  }
  .center-stage > * { width: 100%; }
  .center-stage .quiz-list { max-width: 700px; }
  .center-stage .summary,
  .center-stage .flow { max-width: 1000px; }

  .visual {
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
    position: relative;
    overflow: hidden;
  }

  .content {
    display: flex;
    flex-direction: column;
    justify-content: center;
  }

  /* ====== 巨型数字: 视觉锚 ====== */
  .mega-number {
    font-size: 360px;
    font-weight: 900;
    line-height: 0.85;
    letter-spacing: -0.05em;
    background: linear-gradient(180deg, #4ade80 0%, #166534 100%);
    -webkit-background-clip: text;
    background-clip: text;
    -webkit-text-fill-color: transparent;
    text-shadow: 0 0 80px rgba(74, 222, 128, 0.3);
  }
  .mega-number.red {
    background: linear-gradient(180deg, #f87171 0%, #7f1d1d 100%);
    -webkit-background-clip: text;
    background-clip: text;
    -webkit-text-fill-color: transparent;
  }
  .mega-number.yellow {
    background: linear-gradient(180deg, #fbbf24 0%, #78350f 100%);
    -webkit-background-clip: text;
    background-clip: text;
    -webkit-text-fill-color: transparent;
  }
  .mega-unit {
    font-size: 72px;
    font-weight: 700;
    color: #4ade80;
    vertical-align: top;
    margin-left: 8px;
  }

  /* ====== 标题字号 ====== */
  h1 {
    font-size: 88px;
    font-weight: 800;
    line-height: 1.05;
    letter-spacing: -0.04em;
    color: #fff;
    margin-bottom: 24px;
  }
  h2 {
    font-size: 64px;
    font-weight: 700;
    line-height: 1.1;
    color: #fff;
    margin-bottom: 24px;
    letter-spacing: -0.03em;
  }
  .meta {
    font-size: 14px;
    color: #666;
    letter-spacing: 0.2em;
    text-transform: uppercase;
    font-weight: 600;
    margin-bottom: 16px;
  }

  /* ====== 正文 ====== */
  p {
    font-size: 28px;
    line-height: 1.6;
    color: #d0d0d0;
    margin: 12px 0;
  }
  .lead {
    font-size: 36px;
    color: #fff;
    font-weight: 500;
    line-height: 1.4;
    margin: 16px 0;
  }

  /* ====== 章节标签 (小但醒目) ====== */
  .tag {
    display: inline-block;
    padding: 6px 16px;
    background: rgba(74, 222, 128, 0.15);
    border: 1px solid #4ade80;
    color: #4ade80;
    border-radius: 999px;
    font-size: 14px;
    font-weight: 600;
    letter-spacing: 0.1em;
    margin-bottom: 20px;
  }
  .tag.red { background: rgba(248, 113, 113, 0.15); border-color: #f87171; color: #f87171; }
  .tag.yellow { background: rgba(251, 191, 36, 0.15); border-color: #fbbf24; color: #fbbf24; }

  /* ====== 锚情境: 大引号 + 卡片 ====== */
  .quote-card {
    background: linear-gradient(135deg, #1a1a1a 0%, #0d0d0d 100%);
    border-radius: 20px;
    padding: 40px 48px;
    border-left: 6px solid #4ade80;
    box-shadow: 0 20px 60px rgba(0, 0, 0, 0.5);
    position: relative;
  }
  .quote-card::before {
    content: ''"'';
    position: absolute;
    top: -20px;
    left: 20px;
    font-size: 120px;
    font-weight: 900;
    color: #4ade80;
    opacity: 0.3;
    line-height: 1;
  }
  .quote-card p {
    font-size: 28px;
    color: #ddd;
    line-height: 1.7;
  }

  /* ====== 对比 ====== */
  .compare {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 24px;
    margin: 24px 0;
  }
  .compare > div {
    background: linear-gradient(135deg, #161616 0%, #0d0d0d 100%);
    border-radius: 16px;
    padding: 24px;
    border-top: 4px solid;
    box-shadow: 0 8px 32px rgba(0,0,0,0.3);
  }
  .compare .bad { border-top-color: #f87171; }
  .compare .good { border-top-color: #4ade80; }
  .compare h3 {
    font-size: 22px;
    margin-bottom: 12px;
    font-weight: 600;
  }
  .compare .bad h3 { color: #fca5a5; }
  .compare .good h3 { color: #86efac; }
  .prompt {
    background: #000;
    padding: 14px 16px;
    border-radius: 8px;
    font-family: "SF Mono", Consolas, monospace;
    font-size: 15px;
    color: #fbbf24;
    margin: 8px 0;
    line-height: 1.5;
  }
  .reply {
    background: rgba(255,255,255,0.03);
    padding: 10px 14px;
    border-radius: 6px;
    font-size: 15px;
    color: #888;
    margin-top: 8px;
  }
  .reply.good { color: #86efac; background: rgba(74, 222, 128, 0.08); }
  .reply.bad { color: #fca5a5; background: rgba(248, 113, 113, 0.08); }

  /* ====== 流程对比: 大数字 + 短描述 ====== */
  .flow {
    display: grid;
    grid-template-columns: 1fr auto 1fr;
    gap: 32px;
    align-items: center;
    margin: 24px 0;
  }
  .flow > div:not(.arrow-box) {
    background: #161616;
    border-radius: 16px;
    padding: 32px;
    text-align: center;
  }
  .flow .new {
    background: linear-gradient(135deg, #0d2818 0%, #0a0a0a 100%);
    border: 2px solid #4ade80;
  }
  .flow .flow-num {
    font-size: 96px;
    font-weight: 900;
    line-height: 1;
    color: #4ade80;
    margin-bottom: 8px;
  }
  .flow .old .flow-num { color: #888; }
  .flow .flow-label {
    font-size: 16px;
    color: #888;
    letter-spacing: 0.1em;
    text-transform: uppercase;
    margin-bottom: 8px;
  }
  .flow .flow-desc {
    font-size: 18px;
    color: #ddd;
    line-height: 1.5;
  }
  .arrow-box {
    font-size: 64px;
    color: #4ade80;
    text-align: center;
  }

  /* ====== 自测题: 圆圈大 + 选项文字 ====== */
  .quiz-list {
    display: flex;
    flex-direction: column;
    gap: 14px;
    max-width: 700px;
    margin: 0 auto;
  }
  .quiz-btn {
    display: flex;
    align-items: center;
    gap: 20px;
    background: linear-gradient(135deg, #161616 0%, #0d0d0d 100%);
    color: #f5f5f5;
    border: 2px solid #2a2a2a;
    border-radius: 14px;
    padding: 18px 24px;
    font-size: 22px;
    cursor: pointer;
    font-family: inherit;
    text-align: left;
    transition: all 0.2s;
  }
  .quiz-btn:hover {
    border-color: #4ade80;
    transform: translateX(4px);
  }
  .quiz-btn .circle {
    width: 40px; height: 40px;
    background: #2a2a2a;
    color: #888;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    font-weight: 700;
    font-size: 18px;
    flex-shrink: 0;
  }
  .quiz-btn.correct {
    border-color: #4ade80;
    background: linear-gradient(135deg, #0d2818 0%, #0a1f12 100%);
  }
  .quiz-btn.correct .circle { background: #4ade80; color: #000; }
  .quiz-btn.wrong {
    border-color: #f87171;
    background: linear-gradient(135deg, #2a0d0d 0%, #1f0a0a 100%);
  }
  .quiz-btn.wrong .circle { background: #f87171; color: #000; }

  /* ====== Quiz 解析面板 ====== */
  .quiz-explain {
    margin-top: 24px;
    border-radius: 14px;
    padding: 24px 28px;
    display: none;
    animation: slideUp 0.4s ease-out;
    max-width: 800px;
    margin-left: auto;
    margin-right: auto;
  }
  .quiz-explain.show { display: block; }
  .quiz-explain.correct {
    background: linear-gradient(135deg, #0d2818 0%, #0a1f12 100%);
    border: 2px solid #4ade80;
  }
  .quiz-explain.wrong {
    background: linear-gradient(135deg, #2a1f0d 0%, #1f1a0a 100%);
    border: 2px solid #fbbf24;
  }
  .quiz-explain-head {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 12px;
    font-size: 22px;
  }
  .quiz-explain.correct .quiz-explain-head { color: #4ade80; }
  .quiz-explain.wrong .quiz-explain-head { color: #fbbf24; }
  .quiz-explain-icon {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 36px; height: 36px;
    border-radius: 50%;
    font-weight: 800;
    font-size: 20px;
  }
  .quiz-explain.correct .quiz-explain-icon { background: #4ade80; color: #000; }
  .quiz-explain.wrong .quiz-explain-icon { background: #fbbf24; color: #000; }
  .quiz-explain-body {
    color: #ddd;
    font-size: 15px;
    line-height: 1.5;
    margin-bottom: 12px;
  }
  .quiz-explain-body strong {
    color: #4ade80;
    font-weight: 700;
  }
  .quiz-continue-btn {
    background: #4ade80;
    color: #000;
    border: none;
    padding: 10px 28px;
    border-radius: 8px;
    font-size: 16px;
    font-weight: 700;
    cursor: pointer;
    font-family: inherit;
    transition: background 0.2s;
  }
  .quiz-continue-btn:hover { background: #22c55e; }

  @keyframes slideUp {
    from { opacity: 0; transform: translateY(12px); }
    to { opacity: 1; transform: translateY(0); }
  }

  /* ====== 模拟器 ====== */
  .sim {
    background: linear-gradient(135deg, #0d0d0d 0%, #000 100%);
    border-radius: 16px;
    padding: 24px;
    border: 1px solid #2a2a2a;
    box-shadow: 0 20px 60px rgba(0,0,0,0.5);
  }
  .sim-title {
    display: flex;
    align-items: center;
    gap: 8px;
    padding-bottom: 12px;
    margin-bottom: 16px;
    border-bottom: 1px solid #1f1f1f;
    font-size: 13px;
    color: #666;
  }
  .sim-dot { width: 12px; height: 12px; border-radius: 50%; }
  .sim-dot.r { background: #ff5f57; }
  .sim-dot.y { background: #ffbd2e; }
  .sim-dot.g { background: #28c840; }
  .step {
    display: flex;
    align-items: center;
    gap: 16px;
    padding: 14px 16px;
    margin: 6px 0;
    border-radius: 10px;
    background: #0a0a0a;
    border-left: 4px solid transparent;
    font-size: 18px;
    color: #888;
    cursor: pointer;
    transition: all 0.3s;
  }
  .step:hover { background: #111; color: #fff; }
  .step.done {
    border-left-color: #4ade80;
    color: #666;
    background: rgba(74, 222, 128, 0.03);
  }
  .step.active {
    border-left-color: #4ade80;
    color: #fff;
    background: rgba(74, 222, 128, 0.08);
    box-shadow: 0 0 20px rgba(74, 222, 128, 0.1);
  }
  .step-num {
    width: 28px; height: 28px;
    background: #1f1f1f;
    color: #666;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    font-weight: 700;
    font-size: 13px;
    flex-shrink: 0;
  }
  .step.done .step-num { background: #166534; color: #86efac; }
  .step.active .step-num { background: #4ade80; color: #000; }
  .sim-controls {
    display: flex;
    gap: 12px;
    margin-top: 16px;
    align-items: center;
  }
  .sim-btn {
    background: #4ade80;
    color: #000;
    border: none;
    border-radius: 8px;
    padding: 10px 20px;
    font-size: 16px;
    font-weight: 600;
    cursor: pointer;
    font-family: inherit;
  }
  .sim-btn:hover { background: #22c55e; }
  .sim-btn.reset { background: #2a2a2a; color: #fff; }
  .sim-btn.reset:hover { background: #3a3a3a; }
  .sim-status { margin-left: auto; font-size: 14px; color: #666; }

  /* ====== 任务卡 ====== */
  .task {
    background: linear-gradient(135deg, #0d2818 0%, #0a0a0a 100%);
    border: 2px solid #4ade80;
    border-radius: 20px;
    padding: 36px 44px;
    box-shadow: 0 20px 60px rgba(74, 222, 128, 0.1);
  }
  .task h3 {
    font-size: 32px;
    color: #fff;
    margin-bottom: 24px;
    font-weight: 700;
  }
  .task ol {
    padding-left: 28px;
    counter-reset: step;
    list-style: none;
  }
  .task ol li {
    font-size: 22px;
    margin: 16px 0;
    color: #ddd;
    position: relative;
    padding-left: 8px;
  }
  .task ol li::before {
    counter-increment: step;
    content: counter(step);
    position: absolute;
    left: -28px;
    top: 2px;
    width: 22px; height: 22px;
    background: #4ade80;
    color: #000;
    border-radius: 50%;
    text-align: center;
    line-height: 22px;
    font-size: 13px;
    font-weight: 700;
  }
  .task li strong { color: #fbbf24; }

  /* ====== 总结列表 ====== */
  .summary {
    margin: 24px 0;
  }
  .summary-item {
    display: flex;
    align-items: flex-start;
    gap: 20px;
    padding: 20px 28px;
    margin: 12px 0;
    background: linear-gradient(135deg, #161616 0%, #0d0d0d 100%);
    border-radius: 14px;
    border-left: 5px solid #4ade80;
    font-size: 22px;
    color: #ddd;
    line-height: 1.5;
  }
  .summary-num {
    font-size: 32px;
    font-weight: 800;
    color: #4ade80;
    line-height: 1;
    min-width: 40px;
  }

  /* ====== 控制栏 ====== */
  .controls {
    position: absolute;
    bottom: 20px;
    left: 0; right: 0;
    display: flex;
    justify-content: center;
    align-items: center;
    gap: 16px;
    z-index: 10;
  }
  .ctrl-btn {
    background: rgba(255,255,255,0.05);
    backdrop-filter: blur(10px);
    border: 1px solid #333;
    color: #f5f5f5;
    width: 48px;
    height: 48px;
    border-radius: 50%;
    font-size: 18px;
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    font-family: inherit;
  }
  .ctrl-btn:hover { background: rgba(255,255,255,0.12); }
  .ctrl-btn.play {
    width: 64px;
    height: 64px;
    background: #4ade80;
    color: #000;
    border-color: #4ade80;
    font-size: 14px;
    font-weight: 700;
  }
  .ctrl-btn.play:hover { background: #22c55e; }
  .page-num {
    position: absolute;
    bottom: 24px;
    right: 32px;
    color: #666;
    font-size: 14px;
    z-index: 10;
  }

  .done-banner {
    position: fixed;
    top: 24px;
    left: 50%;
    transform: translateX(-50%);
    background: #4ade80;
    color: #000;
    padding: 16px 32px;
    border-radius: 12px;
    font-size: 18px;
    font-weight: 700;
    z-index: 100;
    display: none;
    box-shadow: 0 10px 40px rgba(74, 222, 128, 0.4);
  }
  .done-banner.show { display: block; }

  /* ====== 网格背景 (subtle 装饰) ====== */
  .grid-bg {
    position: absolute;
    inset: 0;
    background-image:
      linear-gradient(rgba(255,255,255,0.02) 1px, transparent 1px),
      linear-gradient(90deg, rgba(255,255,255,0.02) 1px, transparent 1px);
    background-size: 40px 40px;
    pointer-events: none;
  }
  /* 渐变光晕 */
  .glow {
    position: absolute;
    border-radius: 50%;
    filter: blur(80px);
    opacity: 0.4;
    pointer-events: none;
  }
  .glow.green { background: #4ade80; }
  .glow.red { background: #f87171; }
  .glow.yellow { background: #fbbf24; }
</style>
</head>
<body>
<div class="stage">

  <!-- ============== Slide 1: 标题页 (左大字 + 右简介) ============== -->
  <div class="slide active" data-slide="1" data-duration="6" data-narrator="同学们好,欢迎来到《AI工具与Harness工程》。我是沈老师。今天我们开始第一课。">
    <div class="grid-bg"></div>
    <div class="glow green" style="width: 400px; height: 400px; top: -100px; left: -100px;"></div>
    <div class="split">
      <div class="visual" style="flex-direction: column; gap: 0;">
        <div class="mega-number">24<span class="mega-unit">×</span></div>
        <div style="font-size: 24px; color: #4ade80; letter-spacing: 0.3em; margin-top: 16px; font-weight: 600;">EFFICIENCY</div>
      </div>
      <div class="content">
        <div class="meta">第 1 章 · 第 1 节</div>
        <h1>24 倍效率<br>从何而来</h1>
        <p class="lead">深入分析小明入职第一个月<br>如何用 AI 把 2 小时的工作<br>压缩到 5 分钟</p>
        <div class="tag">▶ 点击开始 · 约 30 分钟</div>
      </div>
    </div>
  </div>

  <!-- ============== Slide 2: 锚情境 (大引号 + 故事) ============== -->
  <div class="slide" data-slide="2" data-duration="5" data-narrator="上周五下午五点,小明刚入职三个月,老板甩给他五万行销售数据。"><div class="js-marker" data-type="" data-duration="6" data-correct="" data-page="1" style="display:none"></div>
    
    <div class="grid-bg"></div>
    <div class="split">
      <div class="visual" style="flex-direction: column;">
        <div class="mega-number yellow" style="font-size: 200px;">5<span class="mega-unit" style="color: #fbbf24;">min</span></div>
        <div style="font-size: 20px; color: #fbbf24; letter-spacing: 0.2em; margin-top: 12px; font-weight: 600;">V.S. 2 HOURS</div>
      </div>
      <div class="content">
        <div class="meta">锚情境 · ANCHOR STORY</div>
        <h2>上周五<br>下午五点</h2>
        <div class="quote-card">
          <p>老板甩给他 5 万行销售数据,说<strong style="color: #fff;">"明天开会要用"</strong>。</p>
          <p style="margin-top: 20px;">小明打开 AI 工具,输入一句话。五分钟后,把报告发到老板邮箱。</p>
          <p style="margin-top: 20px; color: #4ade80; font-weight: 700;">老板回了一个字: <span style="font-size: 36px;">"牛"</span></p>
        </div>
      </div>
    </div>
  </div>

  <!-- ============== Slide 3: 引入 (4 个猜测选项) ============== -->
  <div class="slide" data-slide="3" data-duration="4" data-narrator="小明原本做这份工作需要多久?让我们猜一猜。"><div class="js-marker" data-type="" data-duration="5" data-correct="" data-page="2" style="display:none"></div>
    
    <div class="center-stage">
      <div class="meta">[B] 引入</div>
      <h2>原本要多久?</h2>
      <p class="lead" style="text-align: center;">小明做销售数据周报<br>原本需要多久?</p>
      <div class="quiz-list">
        <button class="quiz-btn" type="button"><span class="circle">A</span>30 分钟</button>
        <button class="quiz-btn" type="button"><span class="circle">B</span>2 小时</button>
        <button class="quiz-btn" type="button"><span class="circle">C</span>半天</button>
        <button class="quiz-btn" type="button"><span class="circle">D</span>一整天</button>
      </div>
    </div>
  </div>

  <!-- ============== Slide 4: 目标 (3 条总结) ============== -->
  <div class="slide" data-slide="4" data-duration="5" data-narrator="带着这三个目标,我们开始本节课。"><div class="js-marker" data-type="warm" data-duration="4" data-correct="B" data-page="3" style="display:none"></div>
    
    <div class="split full">
      <div class="content" style="text-align: center;">
        <div class="meta">[O] 学习目标</div>
        <h2>看完这节<br>你能做 3 件事</h2>
        <div class="summary" style="margin-top: 32px;">
          <div class="summary-item">
            <div class="summary-num">1</div>
            <div>说出 <span style="color: #4ade80;">24 倍效率</span>背后的 3 个本质原因</div>
          </div>
          <div class="summary-item">
            <div class="summary-num">2</div>
            <div>解释"<span style="color: #4ade80;">操作员 → 指挥官</span>"的角色升级</div>
          </div>
          <div class="summary-item">
            <div class="summary-num">3</div>
            <div>识别 AI 工具<span style="color: #f87171;">不适用</span>的 3 类场景</div>
          </div>
        </div>
      </div>
    </div>
  </div>

  <!-- ============== Slide 5: 段1 - 故事 (左大图标 + 右文字) ============== -->
  <div class="slide" data-slide="5" data-duration="8" data-narrator="小明原本做销售数据周报,要打开 Excel,排序求和,做透视表,画图表,粘贴到 Word。两小时。"><div class="js-marker" data-type="" data-duration="5" data-correct="" data-page="4" style="display:none"></div>
    
    <div class="grid-bg"></div>
    <div class="split">
      <div class="visual">
        <div class="mega-number red">2<span class="mega-unit" style="color: #f87171;">h</span></div>
        <div style="font-size: 18px; color: #f87171; letter-spacing: 0.2em; margin-top: 16px;">传统人工流程</div>
      </div>
      <div class="content">
        <div class="meta">[P] 段 1 · 24 倍效率的本质</div>
        <h2>小明原本<br>怎么做?</h2>
        <div class="quote-card" style="padding: 24px 32px;">
          <p style="font-size: 22px; margin: 8px 0; color: #888;">📂 打开 Excel,选中数据</p>
          <p style="font-size: 22px; margin: 8px 0; color: #888;">📊 排序,求和,做透视表</p>
          <p style="font-size: 22px; margin: 8px 0; color: #888;">📈 画图表,粘贴到 Word 调整格式</p>
          <p style="font-size: 22px; margin: 16px 0 0 0; color: #f87171; font-weight: 700;">→ 共耗时 2 小时</p>
        </div>
      </div>
    </div>
  </div>

  <!-- ============== Slide 6: 段1 - 对比 ============== -->
  <div class="slide" data-slide="6" data-duration="10" data-narrator="小明第一次用 AI 时,把这段操作描述成一句话。"><div class="js-marker" data-type="" data-duration="8" data-correct="" data-page="5" style="display:none"></div>
    
    <div class="split full">
      <div class="content">
        <div class="meta">[P] 段 1 · Prompt 对比</div>
        <h2 style="text-align: center;">看出区别了吗?</h2>
        <div class="compare">
          <div class="bad">
            <h3>❌ 差的 Prompt</h3>
            <div class="prompt">"帮我看看这份数据"</div>
            <div style="font-size: 12px; color: #666; margin: 4px 0;">↑ 模糊,AI 要猜</div>
            <div class="reply bad">AI: 请问您想了解什么?</div>
          </div>
          <div class="good">
            <h3>✅ 好的 Prompt</h3>
            <div class="prompt">"读取 sales.csv,按月份汇总销售额,画趋势图"</div>
            <div style="font-size: 12px; color: #666; margin: 4px 0;">↑ 具体,AI 一次做对</div>
            <div class="reply good">AI: 已完成,7-9 月下滑 12%</div>
          </div>
        </div>
      </div>
    </div>
  </div>

  <!-- ============== Slide 7: 段1 - 抽象 (流程对比) ============== -->
  <div class="slide">
    
    <div class="split full">
      <div class="content">
        <div class="meta">[P] 段 1 · 抽象概念</div>
        <h2 style="text-align: center;">24 倍效率的本质<br>= <span style="color: #4ade80;">工作流重构</span></h2>
        <div class="flow" style="margin-top: 40px;">
          <div class="old">
            <div class="flow-num">5</div>
            <div class="flow-label">步骤</div>
            <div class="flow-desc">加载 → 清洗 → 汇总 → 画图 → 报告<br><br><span style="color: #666;">⏱ 2 小时</span></div>
          </div>
          <div class="arrow-box">→</div>
          <div class="new">
            <div class="flow-num">1</div>
            <div class="flow-label">步骤</div>
            <div class="flow-desc">一句话需求 → AI 完成<br><br><span style="color: #4ade80;">⏱ 5 分钟</span></div>
          </div>
        </div>
      </div>
    </div>
  </div>

  <!-- ============== Slide 8: 段间自测 #1 ============== -->
  <div class="slide" data-slide="8" data-duration="6" data-narrator="好,现在考考你。哪个 Prompt 更可能让 AI 一次做对?" data-quiz="1" data-correct="B"><div class="js-marker" data-type="" data-duration="8" data-correct="" data-page="7" style="display:none"></div>
    
    <div class="split full">
      <div class="content" style="text-align: center;">
        <div class="meta">自测 #1 · CHECKPOINT</div>
        <h2>哪个 Prompt<br>更可能一次做对?</h2>
        <div class="quiz-list" style="margin-top: 32px;">
          <button class="quiz-btn" type="button" data-opt="A"><span class="circle">A</span>"分析一下销售数据"</button>
          <button class="quiz-btn" type="button" data-opt="B"><span class="circle">B</span>"用 Python 算各品类总销售额并画柱状图"</button>
          <button class="quiz-btn" type="button" data-opt="C"><span class="circle">C</span>"看看数据有什么问题"</button>
          <button class="quiz-btn" type="button" data-opt="D"><span class="circle">D</span>"帮我做个好看的图表"</button>
        </div>
      </div>
    </div>
  </div>

  <!-- ============== Slide 9: 段2 - 角色 (左新角色 + 右旧角色) ============== -->
  <div class="slide" data-slide="9" data-duration="8" data-narrator="小明说,我最大的变化不是做得快了,是我做的事不一样了。"><div class="js-marker" data-type="quiz" data-duration="6" data-correct="B" data-page="8" style="display:none"></div>
    
    <div class="grid-bg"></div>
    <div class="split">
      <div class="visual" style="flex-direction: column;">
        <div style="font-size: 180px;">👨‍✈️</div>
        <div style="font-size: 24px; color: #4ade80; letter-spacing: 0.2em; margin-top: 24px; font-weight: 700;">指挥官</div>
      </div>
      <div class="content">
        <div class="meta">[P] 段 2 · 角色升级</div>
        <h2>从操作员<br>到指挥官</h2>
        <div class="compare" style="margin-top: 24px;">
          <div class="bad">
            <h3>👨‍💻 操作员</h3>
            <p style="font-size: 18px; margin: 6px 0; color: #888;">· 执行具体步骤</p>
            <p style="font-size: 18px; margin: 6px 0; color: #888;">· 关注过程正确</p>
            <p style="font-size: 18px; margin: 6px 0; color: #888;">· 出错怪数据</p>
          </div>
          <div class="good">
            <h3>👨‍✈️ 指挥官</h3>
            <p style="font-size: 18px; margin: 6px 0; color: #ddd;">· 决定分析方向</p>
            <p style="font-size: 18px; margin: 6px 0; color: #ddd;">· 关注结果有用</p>
            <p style="font-size: 18px; margin: 6px 0; color: #ddd;">· 出错怪判断</p>
          </div>
        </div>
      </div>
    </div>
  </div>

  <!-- ============== Slide 10: 段间自测 #2 ============== -->
  <div class="slide" data-slide="10" data-duration="6" data-narrator="好,那现在问一个更深的问题。" data-quiz="2" data-correct="B"><div class="js-marker" data-type="" data-duration="8" data-correct="" data-page="9" style="display:none"></div>
    
    <div class="center-stage">
      <div class="meta">自测 #2 · CHECKPOINT</div>
      <h2>从操作员到指挥官<br>最关键的能力变化?</h2>
      <div class="quiz-list" style="margin-top: 32px;">
        <button class="quiz-btn" type="button" data-opt="A"><span class="circle">A</span>学会写更复杂的代码</button>
        <button class="quiz-btn" type="button" data-opt="B"><span class="circle">B</span>学会定义问题、验证结果</button>
        <button class="quiz-btn" type="button" data-opt="C"><span class="circle">C</span>学会用更多 AI 工具</button>
        <button class="quiz-btn" type="button" data-opt="D"><span class="circle">D</span>学会更久地加班</button>
      </div>
    </div>
  </div>

  <!-- ============== Slide 11: 段3 - 边界 ============== -->
  <div class="slide" data-slide="11" data-duration="8" data-narrator="但不是所有事都适合 AI。小张让 AI 写战略规划,被老板说全是空话。"><div class="js-marker" data-type="quiz" data-duration="6" data-correct="B" data-page="10" style="display:none"></div>
    
    <div class="center-stage">
      <div class="meta">[P] 段 3 · AI 的边界</div>
      <h2 style="text-align: center;">AI 不适用的<br>3 类场景</h2>
      <div class="summary" style="margin-top: 32px;">
        <div class="summary-item" style="border-left-color: #f87171;">
          <div class="summary-num" style="color: #f87171;">1</div>
          <div>
            <strong style="color: #fca5a5;">需要深度业务理解</strong><br>
            <span style="color: #888; font-size: 18px;">战略规划 · 品牌定位 — AI 不知道你的公司</span>
          </div>
        </div>
        <div class="summary-item" style="border-left-color: #fbbf24;">
          <div class="summary-num" style="color: #fbbf24;">2</div>
          <div>
            <strong style="color: #fde68a;">需要情感共鸣</strong><br>
            <span style="color: #888; font-size: 18px;">客户投诉 · 危机公关 — AI 写不出"让人感动的话"</span>
          </div>
        </div>
        <div class="summary-item" style="border-left-color: #888;">
          <div class="summary-num" style="color: #888;">3</div>
          <div>
            <strong style="color: #d1d5db;">需要承担后果</strong><br>
            <span style="color: #888; font-size: 18px;">合规签字 · 法律决策 — AI 不坐牢</span>
          </div>
        </div>
      </div>
    </div>
  </div>

  <!-- ============== Slide 12: 动手任务 ============== -->
  <div class="slide" data-slide="12" data-duration="10" data-narrator="现在轮到你做小明做过的事了。哪怕只是一小步。" data-task="true"><div class="js-marker" data-type="" data-duration="8" data-correct="" data-page="11" style="display:none"></div>
    
    <div class="split">
      <div class="visual">
        <div class="mega-number yellow" style="font-size: 160px;">📸</div>
        <div style="font-size: 18px; color: #fbbf24; letter-spacing: 0.2em; margin-top: 16px; font-weight: 700;">动手任务</div>
      </div>
      <div class="content">
        <div class="meta">[P后] 实践作业</div>
        <h2>你的一天<br>工作清单</h2>
        <div class="task">
          <h3>📋 4 步完成任务</h3>
          <ol>
            <li>列出今天做的 <strong>3 件事</strong></li>
            <li>判断每件能否用 AI 加速 (Y/N)</li>
            <li>用 AI 试做其中 <strong>1 件</strong></li>
            <li>截图保存,<strong>上传任务区</strong></li>
          </ol>
        </div>
      </div>
    </div>
  </div>

  <!-- ============== Slide 13: 拟真演示 ============== -->
  <div class="slide" data-slide="13" data-duration="30" data-narrator="下面我们看小明当时怎么做的。点自动播放,看 5 步是怎么一步步走完的。" data-interactive="true"><div class="js-marker" data-type="task" data-duration="10" data-correct="" data-page="12" style="display:none"></div>
    
    <div class="split">
      <div class="visual" style="flex-direction: column; gap: 24px;">
        <div style="font-size: 140px;">🤖</div>
        <div style="text-align: center;">
          <div style="font-size: 18px; color: #4ade80; letter-spacing: 0.2em; font-weight: 700;">5 步动画</div>
          <div style="font-size: 14px; color: #888; margin-top: 8px;">点击右侧播放按钮</div>
        </div>
      </div>
      <div class="content">
        <div class="meta">[演示] 替代屏幕录制</div>
        <h2 style="font-size: 40px;">小明怎么<br>5 分钟完成?</h2>
        <div class="sim">
          <div class="sim-title">
            <span class="sim-dot r"></span><span class="sim-dot y"></span><span class="sim-dot g"></span>
            <span style="margin-left: auto;">AI 工具 · OpenCode</span>
          </div>
          <div class="step" data-step="1">
            <span class="step-num">1</span>
            <span>打开 AI 工具 → 输入框就绪</span>
          </div>
          <div class="step" data-step="2">
            <span class="step-num">2</span>
            <span>输入: <span style="color: #fbbf24;">用 pandas 读取 sales.csv</span></span>
          </div>
          <div class="step" data-step="3">
            <span class="step-num">3</span>
            <span>补充: <span style="color: #fbbf24;">输出每月销售额,画趋势图</span></span>
          </div>
          <div class="step" data-step="4">
            <span class="step-num">4</span>
            <span>点击 ▶ 运行 → AI 输出代码并执行</span>
          </div>
          <div class="step" data-step="5">
            <span class="step-num">5</span>
            <span>得到结果: <span style="color: #4ade80;">7-9 月下滑 12%</span></span>
          </div>
          <div class="sim-controls">
            <button class="sim-btn" id="btnPlay" type="button">▶ 自动播放</button>
            <button class="sim-btn reset" id="btnReset" type="button">↻ 重置</button>
            <span class="sim-status" id="simStatus"></span>
          </div>
        </div>
      </div>
    </div>
  </div>

  <!-- ============== Slide 14: 总结 ============== -->
  <div class="slide" data-slide="14" data-duration="6" data-narrator="今天你学会了 3 件事。我们一起回顾。"><div class="js-marker" data-type="interactive" data-duration="30" data-correct="" data-page="13" style="display:none"></div>
    
    <div class="grid-bg"></div>
    <div class="center-stage">
      <div class="meta">[S] 总结 · RECAP</div>
      <h2 style="text-align: center;">今天你学会了<br>3 件事</h2>
      <div class="summary" style="margin-top: 40px;">
        <div class="summary-item">
          <div class="summary-num">1</div>
          <div><strong style="color: #4ade80;">24 倍效率的本质</strong>是工作流重构,不是 AI 变聪明</div>
        </div>
        <div class="summary-item">
          <div class="summary-num">2</div>
          <div>你的角色从 <span style="color: #f87171;">操作员</span> 升级为 <span style="color: #4ade80;">指挥官</span>,价值在于决策和判断</div>
        </div>
        <div class="summary-item">
          <div class="summary-num">3</div>
          <div>AI 不适合做需要深度业务理解、情感共鸣、承担后果的事</div>
        </div>
      </div>
    </div>
  </div>

  <!-- ============== Slide 15: 下一节 ============== -->
  <div class="slide" data-slide="15" data-duration="6" data-narrator="小明今天搞定了销售数据周报。明天,老板会让他处理 Q3 销量下滑分析。"><div class="js-marker" data-type="" data-duration="6" data-correct="" data-page="14" style="display:none"></div>
    
    <div class="grid-bg"></div>
    <div class="glow yellow" style="width: 500px; height: 500px; bottom: -200px; right: -200px;"></div>
    <div class="split">
      <div class="visual">
        <div style="font-size: 160px;">🎬</div>
        <div style="font-size: 20px; color: #fbbf24; letter-spacing: 0.2em; margin-top: 16px; font-weight: 700;">下一节</div>
      </div>
      <div class="content">
        <div class="meta">[预告] NEXT</div>
        <h2>1.2<br>OpenCode vs Trae</h2>
        <p class="lead">什么时候用什么工具?<br>两个 AI 工具的实战对比</p>
        <div class="tag yellow">预计 30 分钟</div>
        <p style="font-size: 18px; color: #888; margin-top: 16px;">⚠️ 别忘了先完成本次截图任务</p>
      </div>
    </div>
  </div>

  <!-- ============== 控制栏 ============== -->
  <div class="controls">
    <button class="ctrl-btn" id="btnPrev" type="button" aria-label="上一页">‹</button>
    <button class="ctrl-btn play" id="btnPlayAll" type="button" aria-label="自动播放">▶</button>
    <button class="ctrl-btn" id="btnNext" type="button" aria-label="下一页">›</button>
  </div>
  <div class="page-num"><span id="pageCurrent">1</span> / <span id="pageTotal">15</span></div>

  <div class="done-banner" id="doneBanner">🎉 本节完成!别忘了截图任务和反思日志</div>

</div>

<script>
(function() {
  const slides = document.querySelectorAll(''.slide'');
  const total = slides.length;
  document.getElementById(''pageTotal'').textContent = total;

  // ====== ★ 立即给每个 slide 注入配置 (必须在所有事件绑定之前!) ======
  const slideConfigs = {
    3:  { duration: 4,  quiz: ''warm'', correct: ''B'' },
    8:  { duration: 6,  quiz: ''normal'', correct: ''B'' },
    10: { duration: 6,  quiz: ''normal'', correct: ''B'' },
    12: { duration: 10, task: true },
    13: { duration: 30, interactive: true },
  };
  slides.forEach((s, i) => {
    s._idx = i + 1;
    const cfg = slideConfigs[i + 1] || { duration: 8 };
    s._duration = cfg.duration;
    s._interactive = !!cfg.interactive;
    s._quizType = cfg.quiz || '''';
    s._quizCorrect = cfg.correct || '''';
    s._hasTask = !!cfg.task;
  });

  // ====== 状态 ======
  let current = 1;
  let playing = false;
  let advanceTimer = null;      // 自动翻页定时器
  let stepTimer = null;          // 拟真步骤定时器
  let demoIdx = 0;               // 拟真当前步骤
  let demoDone = false;          // 拟真是否完成
  let pendingAdvance = false;    // 等待交互页完成后继续自动翻页

  // ====== DOM 缓存 ======
  const $ = id => document.getElementById(id);
  const btnPrev = $(''btnPrev'');
  const btnNext = $(''btnNext'');
  const btnPlayAll = $(''btnPlayAll'');
  const btnPlay = $(''btnPlay'');
  const btnReset = $(''btnReset'');
  const pageCurrent = $(''pageCurrent'');
  const doneBanner = $(''doneBanner'');
  const simStatus = $(''simStatus'');

  // ====== 通用: 显示提示横幅 ======
  function showToast(message, type = ''info'', duration = 2500) {
    let toast = document.getElementById(''toast'');
    if (!toast) {
      toast = document.createElement(''div'');
      toast.id = ''toast'';
      toast.style.cssText = ''position:fixed;top:24px;left:50%;transform:translateX(-50%);'' +
        ''padding:14px 28px;border-radius:10px;font-size:16px;font-weight:600;'' +
        ''z-index:200;box-shadow:0 10px 40px rgba(0,0,0,0.4);transition:opacity 0.3s;opacity:0;'';
      document.body.appendChild(toast);
    }
    const colors = {
      info: ''background:#3b82f6;color:white;'',
      success: ''background:#4ade80;color:#000;'',
      error: ''background:#f87171;color:white;'',
      warning: ''background:#fbbf24;color:#000;'',
    };
    toast.style.cssText += colors[type] || colors.info;
    toast.textContent = message;
    toast.style.opacity = ''1'';
    clearTimeout(toast._timer);
    toast._timer = setTimeout(() => { toast.style.opacity = ''0''; }, duration);
  }

  // ====== 核心: 翻页 ======
  function show(n) {
    if (n < 1 || n > total) return;
    slides.forEach(s => s.classList.remove(''active''));
    current = n;
    slides[n - 1].classList.add(''active'');
    pageCurrent.textContent = n;
    updateNavButtons();
    pendingAdvance = false;  // 翻到新页,清除等待标记

    // 进入交互页:如果自动播放中,启动演示
    if (slides[n - 1]._interactive) {
      if (playing) {
        // 延迟启动,等用户看清标题
        setTimeout(() => {
          if (current === n && playing) startDemo();
        }, 1000);
      }
    }
  }

  function next() {
    if (current < total) show(current + 1);
    else finish();
  }

  function prev() {
    if (current > 1) {
      pause();
      show(current - 1);
    } else {
      showToast(''已经是第一页'', ''info'', 1200);
    }
  }

  // ====== 自动播放 ======
  function play() {
    playing = true;
    btnPlayAll.textContent = ''⏸'';  // 只用符号
    btnPlayAll.classList.add(''playing'');
    btnPlayAll.setAttribute(''aria-label'', ''暂停自动播放'');
    showToast(''▶ 自动播放开始'', ''success'', 1500);
    schedule();
  }

  function pause() {
    playing = false;
    btnPlayAll.textContent = ''▶'';  // 只用符号
    btnPlayAll.classList.remove(''playing'');
    btnPlayAll.setAttribute(''aria-label'', ''开始自动播放'');
    if (advanceTimer) clearTimeout(advanceTimer);
    advanceTimer = null;
  }

  function togglePlay() {
    if (playing) pause();
    else play();
  }

  // ====== 自动翻页调度 ======
  function schedule() {
    if (!playing) return;
    if (advanceTimer) clearTimeout(advanceTimer);

    const slide = slides[current - 1];
    // 交互页:等用户操作完成
    if (slide._interactive) {
      pendingAdvance = true;
      return;
    }
    // 自测页:等用户答完 (用 _quizAnswered 标记)
    if (slide._quizType && !slide._quizAnswered) {
      pendingAdvance = true;
      return;
    }
    // 截图任务页:等用户确认
    if (slide._hasTask && !slide._taskCompleted) {
      pendingAdvance = true;
      return;
    }

    const duration = (slide._duration || 8) * 1000 + 1500;
    advanceTimer = setTimeout(() => {
      if (current < total) {
        next();
        schedule();
      } else {
        pause();
        finish();
      }
    }, duration);
  }

  // 触发"等待状态完成后继续"
  function resumeAdvance() {
    if (!playing || !pendingAdvance) return;
    pendingAdvance = false;
    // 短暂延迟,让 UI 反馈先呈现
    setTimeout(schedule, 1500);
  }

  // ====== 按钮边界状态 ======
  function updateNavButtons() {
    btnPrev.disabled = current === 1;
    btnNext.disabled = current === total;
  }

  // ====== 拟真演示 ======
  function startDemo() {
    demoIdx = 0;
    demoDone = false;
    const steps = document.querySelectorAll(''.step'');
    steps.forEach(s => s.classList.remove(''active'', ''done''));
    btnPlay.textContent = ''▶ 播放中'';
    btnPlay.disabled = true;  // 播放中禁用按钮
    simStatus.textContent = '''';

    function tick() {
      if (demoIdx >= steps.length) {
        demoDone = true;
        btnPlay.textContent = ''✓ 完成,继续'';
        btnPlay.disabled = false;
        simStatus.textContent = ''5 步全部完成'';
        // 触发自动播放续期
        if (playing && pendingAdvance) {
          resumeAdvance();
        } else if (!playing) {
          // 手动模式:提示用户点"完成,继续"
          showToast(''演示完成,点"完成,继续"翻页'', ''success'', 3000);
        }
        return;
      }
      steps.forEach(s => s.classList.remove(''active''));
      for (let i = 0; i < demoIdx; i++) steps[i].classList.add(''done'');
      steps[demoIdx].classList.add(''active'');
      simStatus.textContent = `步骤 ${demoIdx + 1} / ${steps.length}`;
      demoIdx++;
      stepTimer = setTimeout(tick, 1500);
    }
    tick();
  }

  function resetDemo() {
    if (stepTimer) clearTimeout(stepTimer);
    demoIdx = 0;
    demoDone = false;
    document.querySelectorAll(''.step'').forEach(s => s.classList.remove(''active'', ''done''));
    btnPlay.textContent = ''▶ 自动播放'';
    btnPlay.disabled = false;
    simStatus.textContent = '''';
  }

  // ====== 完成处理 ======
  function finish() {
    pause();
    doneBanner.classList.add(''show'');
    showToast(''🎉 本节完成!'', ''success'', 4000);
    // 5秒后把 banner 转为可关闭
    setTimeout(() => {
      doneBanner.innerHTML = ''🎉 本节完成! <button onclick="location.reload()" style="margin-left:16px;background:white;color:#000;border:none;padding:6px 16px;border-radius:6px;cursor:pointer;font-weight:600;">重新开始</button>'';
    }, 4000);
    setTimeout(() => doneBanner.classList.remove(''show''), 12000);
  }

  // ====== 通用: 显示 quiz 解析面板 ======
  function showQuizFeedback(slide, isCorrect, explanation) {
    // 创建或获取解析面板
    let panel = slide.querySelector(''.quiz-explain'');
    if (!panel) {
      panel = document.createElement(''div'');
      panel.className = ''quiz-explain'';
      // 插在 quiz-list 后面
      const list = slide.querySelector(''.quiz-list'');
      if (list && list.parentNode) list.parentNode.insertBefore(panel, list.nextSibling);
    }
    panel.className = ''quiz-explain show '' + (isCorrect ? ''correct'' : ''wrong'');
    panel.innerHTML = `
      <div class="quiz-explain-head">
        <span class="quiz-explain-icon">${isCorrect ? ''✓'' : ''✗''}</span>
        <strong>${isCorrect ? ''答对了!'' : ''再想想''}</strong>
      </div>
      <div class="quiz-explain-body">${explanation}</div>
      <button class="quiz-continue-btn" type="button">继续 ▶</button>
    `;
    // 滚动到解析
    setTimeout(() => panel.scrollIntoView({ behavior: ''smooth'', block: ''center'' }), 100);
    // 绑定继续按钮
    panel.querySelector(''.quiz-continue-btn'').addEventListener(''click'', () => {
      panel.classList.remove(''show'');
      if (playing) resumeAdvance();
      else next();
    });
  }

  // ====== 自测题处理 (按内部索引,完全独立于 HTML data-* 属性) ======
  slides.forEach(slide => {
    if (!slide._quizType) return;
    const correct = slide._quizCorrect;
    const btns = slide.querySelectorAll(''.quiz-btn'');
    btns.forEach((btn, idx) => {
      const opt = String.fromCharCode(65 + idx);
      btn.addEventListener(''click'', () => {
        if (slide._quizAnswered) return;
        if (opt === correct) {
          btn.classList.add(''correct'');
          slide._quizAnswered = true;
          let explanation = ''答对了!'';
          if (btns[1] && btns[1].textContent.includes(''Python'')) {
            explanation = ''好的 Prompt 包含 3 要素: <strong>工具(Python)</strong> + <strong>操作(算总销售额)</strong> + <strong>输出(柱状图)</strong>。A、C、D 都缺少关键信息。'';
          } else if (btns[1] && btns[1].textContent.includes(''定义问题'')) {
            explanation = ''从操作员到指挥官,关键不是技术变难,而是 <strong>从"做"变成"想"</strong>。定义问题和验证结果都是"想"的能力。'';
          } else if (slide._quizType === ''warm'') {
            explanation = ''猜对了!小明原本要 2 小时,现在 5 分钟完成。'';
          }
          showQuizFeedback(slide, true, explanation);
          showToast(''✓ 答对了!'', ''success'', 2500);
        } else {
          btn.classList.add(''wrong'');
          const correctBtn = btns[correct.charCodeAt(0) - 65];
          if (correctBtn) correctBtn.classList.add(''correct'');
          slide._quizAnswered = true;
          let explanation = '''';
          if (btns[1] && btns[1].textContent.includes(''Python'')) {
            explanation = ''绿色高亮的 B 选项包含完整信息:A 是"分析"——太模糊;C 是"看看问题"——没说怎么做;D 是"好看"——没说内容。'';
          } else if (btns[1] && btns[1].textContent.includes(''定义问题'')) {
            explanation = ''A、C、D 都在"工具"层面打转。真正的升级是 <strong>思维层级</strong>:操作员想"怎么执行",指挥官想"做对没"。'';
          } else if (slide._quizType === ''warm'') {
            explanation = ''小明原本做销售数据周报要 2 小时——打开 Excel、排序求和、做透视表、画图表、粘贴到 Word 调整格式。所以答案是 B。'';
          }
          showQuizFeedback(slide, false, explanation);
          showToast(''✗ 看下方解析'', ''warning'', 3000);
          if (playing) pause();
        }
      });
    });
  });

  // ====== 截图任务确认 (按内部索引) ======
  slides.forEach(slide => {
    if (!slide._hasTask) return;
    const taskEl = slide.querySelector(''.task'');
    if (taskEl) {
      const btn = document.createElement(''button'');
      btn.type = ''button'';
      btn.textContent = ''✓ 我已完成任务'';
      btn.style.cssText = ''margin-top:24px;width:100%;background:#4ade80;color:#000;'' +
        ''border:none;padding:14px;border-radius:10px;font-size:18px;font-weight:700;'' +
        ''cursor:pointer;font-family:inherit;transition:all 0.2s;'';
      btn.addEventListener(''mouseover'', () => btn.style.background = ''#22c55e'');
      btn.addEventListener(''mouseout'', () => btn.style.background = ''#4ade80'');
      btn.addEventListener(''click'', () => {
        if (slide._taskCompleted) return;
        slide._taskCompleted = true;
        btn.textContent = ''✓ 已确认'';
        btn.style.background = ''#166534'';
        btn.style.color = ''#86efac'';
        btn.disabled = true;
        showToast(''✓ 任务确认! 进入下一节'', ''success'', 2000);
        if (playing) resumeAdvance();
        else setTimeout(next, 1500);
      });
      taskEl.appendChild(btn);
    }
  });

  // ====== 控制按钮 ======
  btnPrev.addEventListener(''click'', () => { pause(); prev(); });
  btnNext.addEventListener(''click'', () => { pause(); next(); });
  btnPlayAll.addEventListener(''click'', togglePlay);
  btnPlay.addEventListener(''click'', () => {
    if (demoDone) next();
    else startDemo();
  });
  btnReset.addEventListener(''click'', resetDemo);

  // ====== 键盘快捷键 ======
  document.addEventListener(''keydown'', e => {
    if (e.target.tagName === ''INPUT'' || e.target.tagName === ''TEXTAREA'') return;
    if (e.key === ''ArrowRight'' || e.key === '' '') {
      e.preventDefault();
      pause();
      next();
    } else if (e.key === ''ArrowLeft'') {
      e.preventDefault();
      pause();
      prev();
    } else if (e.key === ''p'' || e.key === ''P'') {
      e.preventDefault();
      togglePlay();
    } else if (e.key === ''r'' || e.key === ''R'') {
      e.preventDefault();
      resetDemo();
      showToast(''↻ 拟真演示已重置'', ''info'', 1500);
    } else if (e.key === ''Escape'') {
      pause();
      showToast(''已暂停'', ''info'', 1200);
    }
  });

  // ====== 启动 ======
  // (slideConfigs 已在 IIFE 顶部定义并赋给 s._xxx)
  updateNavButtons();
  show(1);
  setTimeout(() => {
    showToast(''💡 提示: 空格/→ 翻页, ← 上一页, P 自动播放, R 重置'', ''info'', 5000);
  }, 1500);
})();
</script>

<!-- 15 段独立音频 (HTML 自己控制) -->
<!-- 容器控制合并音频 / HTML 分段控制独立音频 -->
<!-- 优先使用 HTML 分段控制,容器合并音频作为兜底 -->
<audio id="segAudio_01" preload="auto" src="AUDIO_SEG_01_URL"></audio>
<audio id="segAudio_02" preload="auto" src="AUDIO_SEG_02_URL"></audio>
<audio id="segAudio_03" preload="auto" src="AUDIO_SEG_03_URL"></audio>
<audio id="segAudio_04" preload="auto" src="AUDIO_SEG_04_URL"></audio>
<audio id="segAudio_05" preload="auto" src="AUDIO_SEG_05_URL"></audio>
<audio id="segAudio_06" preload="auto" src="AUDIO_SEG_06_URL"></audio>
<audio id="segAudio_07" preload="auto" src="AUDIO_SEG_07_URL"></audio>
<audio id="segAudio_08" preload="auto" src="AUDIO_SEG_08_URL"></audio>
<audio id="segAudio_09" preload="auto" src="AUDIO_SEG_09_URL"></audio>
<audio id="segAudio_10" preload="auto" src="AUDIO_SEG_10_URL"></audio>
<audio id="segAudio_11" preload="auto" src="AUDIO_SEG_11_URL"></audio>
<audio id="segAudio_12" preload="auto" src="AUDIO_SEG_12_URL"></audio>
<audio id="segAudio_13" preload="auto" src="AUDIO_SEG_13_URL"></audio>
<audio id="segAudio_14" preload="auto" src="AUDIO_SEG_14_URL"></audio>
<audio id="segAudio_15" preload="auto" src="AUDIO_SEG_15_URL"></audio>
</body>
</html>'
 WHERE section_id = 573
   AND content_type = 'HTML_DIRECT';
