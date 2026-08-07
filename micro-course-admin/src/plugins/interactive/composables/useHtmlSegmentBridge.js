/**
 * useHtmlSegmentBridge.js · HTML 分段 a11y 桥（L0 U-3 兜底修复）
 *
 * 背景：
 * - 后端 SlideServiceImpl.enhanceHtmlSegments 在读时向 HTML 注入 `data-segment="N"`
 *   属性、`.active` 高亮 CSS 与 bridge.js（点击段 → postMessage segment-active；
 *   接收 segment-activated → 切换高亮），但未注入任何无障碍属性：
 *   `[data-segment]` 元素对键盘用户不可聚焦、无语义角色、无 aria-label，
 *   视障 / 老年 / 键盘流用户无法操作（违反第 7 套治理 a11y 要求）。
 * - 本模块在播放器侧对 srcdoc 内容做**二次增强**（纯字符串处理，不改后端、
 *   不改落库内容）：
 *   1. 给 `[data-segment="N"]` 元素补 `tabindex="0"` / `role="button"` /
 *      `aria-label="第 N 段"`（键盘可达 + 语义化）；
 *   2. 追加幂等 a11y 桥脚本：Enter/Space 激活段（postMessage segment-active，
 *      与后端点击桥同协议）；接收 `segment-activated` 时同步 `aria-current="true"`
 *      （后端桥只切 .active 类，不维护 aria-current）。
 *
 * 用法：
 *   import { enhanceHtmlContentForA11y } from '@/plugins/interactive/composables/useHtmlSegmentBridge'
 *   const srcDoc = enhanceHtmlContentForA11y(page.htmlContent, page.segments)
 *
 * 兼容性：sandbox srcdoc iframe（opaque origin）内脚本照常执行；
 * 注入脚本带 __slideA11yBridge 幂等标记，重复调用不重复注入。
 */

const A11Y_BRIDGE_MARKER = '__slideA11yBridge'

/**
 * 给 html 字符串中所有 `data-segment="N"` 元素补键盘可达 + 语义属性。
 * 纯字符串处理：只改 opening tag，不解析/不执行，不改内容与落库。
 * 幂等：元素已含 tabindex 属性则跳过，避免重复增强。
 */
function injectA11yAttributes(html, segments) {
  let out = html
  for (const seg of segments || []) {
    const idx = seg?.index
    if (idx == null) continue
    const label = `第 ${idx} 段`
    // 匹配 <tag ... data-segment="idx" ...>（含自闭合），属性可能出现在任意顺序
    // 注：JS 不支持 PCRE 的 (?is) 内联修饰符，需用独立的 g/i/s 标志（ES2018+ dotAll）
    const re = new RegExp(`(<[a-z][a-z0-9-]*\\b[^>]*?data-segment=["']${idx}["'][^>]*?)>`, 'gis')
    out = out.replace(re, (m, openTag) => {
      if (/\b(tabindex|role|aria-label)=/.test(openTag)) return m
      // 把 a11y 属性追加在开标签尾部（> 之前），不破坏原有属性顺序
      const [tagName] = openTag.match(/^<[a-z][a-z0-9-]*/i) || ['<div']
      const selfClose = /\/\s*$/.test(openTag) ? ' /' : ''
      const cleaned = openTag.replace(/\/\s*$/, '')
      return `${cleaned} tabindex="0" role="button" aria-label="${label}"${selfClose}>`
    })
  }
  return out
}

/**
 * a11y 桥接脚本（追加到 HTML 尾部；脚本在解析时执行，委托式监听无需 DOM ready）：
 * - keydown Enter / Space 且目标在 [data-segment] 内 → preventDefault + postMessage segment-active；
 * - click [data-segment] → 本地立即同步 aria-current（不等父页回显，消除高亮延迟）；
 * - 接收 slide-audio-state-v2 / segment-activated → 同步 .active 类与 aria-current（与后端桥互补）。
 * 返回压缩单行脚本字符串。
 */
function buildA11yBridgeScript() {
  return `<script>(function(){if(window.${A11Y_BRIDGE_MARKER})return;window.${A11Y_BRIDGE_MARKER}=true;`
    + `function post(m){try{parent.postMessage(m,'*')}catch(e){}}`
    + `function applyAria(index){document.querySelectorAll('[data-segment]').forEach(function(n){`
    + `var active=Number(n.getAttribute('data-segment'))===index;`
    + `n.classList.toggle('active',active);`
    + `if(active)n.setAttribute('aria-current','true');else n.removeAttribute('aria-current');`
    + `})}`
    + `document.addEventListener('keydown',function(e){`
    + `if(e.key!=='Enter'&&e.key!==' ')return;`
    + `var el=e.target&&e.target.closest?e.target.closest('[data-segment]'):null;`
    + `if(!el)return;e.preventDefault();`
    + `var idx=Number(el.getAttribute('data-segment'));`
    + `applyAria(idx);`
    + `post({type:'slide-audio-v2',version:2,action:'segment-active',index:idx});`
    + `});`
    + `document.addEventListener('click',function(e){`
    + `var el=e.target&&e.target.closest?e.target.closest('[data-segment]'):null;`
    + `if(el)applyAria(Number(el.getAttribute('data-segment')));`
    + `});`
    + `window.addEventListener('message',function(e){`
    + `var m=e.data;if(!m||m.type!=='slide-audio-state-v2')return;`
    + `if(m.state==='segment-activated'&&m.index!=null)applyAria(m.index);`
    + `});`
    + `})();</script>`
}

/**
 * 入口：对后端增强过的 htmlContent 做 a11y 二次增强。
 * @param {string} htmlContent  后端返回（已含 data-segment / 高亮 CSS / 点击桥）的 HTML
 * @param {Array<{index:number}>} segments  当前页段元数据
 * @returns {string} 增强后的 HTML（无 segments / 无 data-segment 时原样返回）
 */
export function enhanceHtmlContentForA11y(htmlContent, segments) {
  if (!htmlContent || typeof htmlContent !== 'string') return htmlContent
  if (!Array.isArray(segments) || segments.length === 0) return htmlContent
  const out = injectA11yAttributes(htmlContent, segments)
  // 幂等：已注入过 a11y 桥则不重复追加
  if (out.includes(A11Y_BRIDGE_MARKER)) return out
  const script = buildA11yBridgeScript()
  const bodyIdx = out.lastIndexOf('</body>')
  if (bodyIdx < 0) return out + script
  return out.slice(0, bodyIdx) + script + out.slice(bodyIdx)
}
