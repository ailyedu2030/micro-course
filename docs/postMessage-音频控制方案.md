# postMessage 音频控制方案

> **提出**: Hermes 教授分身  
> **实施**: 总工程师  
> **日期**: 2026-07-12  
> **状态**: 已实施  

---

## 一句话

HTML 课件内不用 `<audio>` 标签，用 `postMessage` 指挥平台播放器。

## 协议版本：v1

所有消息类型为 `slide-audio`（HTML→平台）和 `slide-audio-state`（平台→HTML）。

### HTML → SlidePlayer（指令）

```json
// 请求播放音频
{ "type": "slide-audio", "action": "play" }

// 请求暂停
{ "type": "slide-audio", "action": "pause" }

// 跳转到指定时间（秒）
{ "type": "slide-audio", "action": "seek", "time": 30.5 }

// 设置倍速
{ "type": "slide-audio", "action": "speed", "rate": 1.5 }

// 查询当前状态
{ "type": "slide-audio", "action": "get-state" }
```

### SlidePlayer → HTML（事件）

```json
// 音频开始播放
{ "type": "slide-audio-state", "state": "playing", "time": 0, "duration": 120.0 }

// 音频暂停
{ "type": "slide-audio-state", "state": "paused", "time": 15.2, "duration": 120.0 }

// 音频自然结束
{ "type": "slide-audio-state", "state": "ended" }

// 音频已加载
{ "type": "slide-audio-state", "state": "loaded", "duration": 120.0 }

// 倍速变更
{ "type": "slide-audio-state", "state": "speed-changed", "rate": 1.5 }

// 时间更新（每秒）
{ "type": "slide-audio-state", "state": "time-update", "time": 30.0, "duration": 120.0 }
```

## 安全校验

SlidePlayer 收到 message 后：

```js
// origin 校验：srcdoc iframe 的 origin 为 null
if (event.origin !== null) return  // 拒绝来自非同源窗口

// 格式校验
const msg = event.data
if (typeof msg !== 'object' || msg === null) return
if (msg.type !== 'slide-audio') return
if (!['play', 'pause', 'seek', 'speed', 'get-state'].includes(msg.action)) return

// 执行对应操作
handleMessage(msg)
```

## 交互流程

```
学生看到 HTML 课件（含互动元素）
  → HTML 页面调用 parent.postMessage({type:'slide-audio', action:'play'}, '*')
  → SlidePlayer 收到，调用 playAudio()
  → 平台 <audio> 开始播放，音频时间更新
  → SlidePlayer postMessage({type:'slide-audio-state', state:'playing'}, '*')
  → HTML 页面收到，显示"正在播放"状态
  → 音频结束，平台 postMessage({type:'slide-audio-state', state:'ended'})
  → HTML 页面收到，亮起"📋 复制模板"等下一步按钮
```

## 实现位置

- 监听器注册：`onMounted` 中 `window.addEventListener('message', onSlideAudioMessage)`
- 清理：`onUnmounted` 中 `window.removeEventListener(...)`
- 指令处理：`onSlideAudioMessage(event)` 函数，根据 event.data.action 分发
- 状态推送：在 `onAudioLoaded()`/`playAudio()`/`onTimeUpdate()`/`onAudioEnded()` 中追加 postMessage

## 测试

| 场景 | 预期 |
|------|------|
| iframe 发 `{action:'play'}` | 平台调用 playAudio()，音频开始播放 |
| iframe 发 `{action:'pause'}` | 平台音频暂停 |
| iframe 发 `{action:'seek', time:30}` | 平台跳转到 30s |
| 音频开始播放 | iframe 收到 `{state:'playing'}` |
| 音频结束 | iframe 收到 `{state:'ended'}` |
| 非 `slide-audio` 消息 | 忽略 |
| 非 null origin 消息 | 忽略 |
