/**
 * a11y-utils 单元测试
 * ====================
 *
 * 验证: 基线加载、豁免过滤、严重程度分类、门禁判定
 *
 * 运行:
 *   node e2e/a11y-utils.test.mjs
 */
/* eslint-env node */

// 用 createRequire 只加载 assert（但它不支持 ESM top-level）
import { strict as assert } from 'node:assert';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { existsSync, unlinkSync, writeFileSync } from 'node:fs';

const __dirname = dirname(fileURLToPath(import.meta.url));

// 动态导入（确保 fresh 实例）
const utilsPath = resolve(__dirname, 'a11y-utils.js');

async function runTests() {
  let passed = 0;
  let failed = 0;

  function test(name, fn) {
    try {
      fn();
      console.log(`  ✅ ${name}`);
      passed++;
    } catch (err) {
      console.log(`  ❌ ${name}: ${err.message}`);
      failed++;
    }
  }

  // ---- 重置 ----
  const utils = await import(utilsPath);
  utils.resetBaseline();

  // ================================================================
  console.log('\n📋 测试: loadBaseline');
  // ================================================================

  test('加载真实基线文件', () => {
    const baseline = utils.loadBaseline(resolve(__dirname, 'a11y-baseline.json'));
    assert.ok(baseline, 'baseline 不应为 null');
    assert.ok(Array.isArray(baseline.exemptions), 'exemptions 应为数组');
    assert.ok(Array.isArray(baseline.knownViolations), 'knownViolations 应为数组');
    console.log(`     exemptions: ${baseline.exemptions.length}, knownViolations: ${baseline.knownViolations.length}`);
  });

  test('加载不存在的文件返回空基线', () => {
    utils.resetBaseline();
    const baseline = utils.loadBaseline('/tmp/nonexistent-baseline.json');
    assert.ok(baseline, '空基线不应为 null');
    assert.deepEqual(baseline.exemptions, []);
    assert.deepEqual(baseline.knownViolations, []);
  });

  // ================================================================
  console.log('\n📋 测试: isExempted');
  // ================================================================

  const mockBaseline = {
    exemptions: [
      {
        ruleId: 'landmark-unique',
        selectors: ['.el-breadcrumb[aria-label="Breadcrumb"]'],
        reason: 'Element Plus upstream',
      },
    ],
    knownViolations: [],
  };

  test('匹配 ruleId + selector 应被豁免', () => {
    const violation = {
      id: 'landmark-unique',
      nodes: [{ target: ['.el-breadcrumb[aria-label="Breadcrumb"]'] }],
    };
    const result = utils.isExempted(violation, mockBaseline);
    assert.equal(result.exempted, true);
    assert.ok(result.reason);
  });

  test('匹配 ruleId 但 selector 不匹配不应豁免', () => {
    const violation = {
      id: 'landmark-unique',
      nodes: [{ target: ['.custom-breadcrumb'] }],
    };
    const result = utils.isExempted(violation, mockBaseline);
    assert.equal(result.exempted, false);
  });

  test('不匹配 ruleId 不应豁免', () => {
    const violation = {
      id: 'color-contrast',
      nodes: [{ target: ['.some-element'] }],
    };
    const result = utils.isExempted(violation, mockBaseline);
    assert.equal(result.exempted, false);
  });

  test('无 selectors 的豁免匹配所有 ruleId', () => {
    const broadBaseline = {
      exemptions: [{ ruleId: 'color-contrast', reason: 'broad' }],
    };
    const violation = {
      id: 'color-contrast',
      nodes: [{ target: ['.anything'] }],
    };
    const result = utils.isExempted(violation, broadBaseline);
    assert.equal(result.exempted, true);
  });

  test('空 violations nodes 列表不崩溃', () => {
    const violation = { id: 'landmark-unique', nodes: [] };
    const result = utils.isExempted(violation, mockBaseline);
    assert.equal(result.exempted, false);
  });

  test('空的 baseline 不应豁免任何内容', () => {
    const emptyBaseline = { exemptions: [] };
    const violation = { id: 'anything', nodes: [{ target: ['.x'] }] };
    const result = utils.isExempted(violation, emptyBaseline);
    assert.equal(result.exempted, false);
  });

  // ================================================================
  console.log('\n📋 测试: filterBaselineViolations');
  // ================================================================

  test('过滤后返回 remaining 和 removed', () => {
    const violations = [
      { id: 'landmark-unique', nodes: [{ target: ['.el-breadcrumb[aria-label="Breadcrumb"]'] }] },
      { id: 'color-contrast', nodes: [{ target: ['.x'] }] },
    ];
    const { remaining, removed } = utils.filterBaselineViolations(violations, mockBaseline);
    assert.equal(remaining.length, 1);
    assert.equal(removed.length, 1);
    assert.equal(remaining[0].id, 'color-contrast');
    assert.equal(removed[0].violation.id, 'landmark-unique');
  });

  // ================================================================
  console.log('\n📋 测试: classifyViolations');
  // ================================================================

  test('按 severity 正确分类', () => {
    const violations = [
      { id: 'a', impact: 'critical' },
      { id: 'b', impact: 'serious' },
      { id: 'c', impact: 'moderate' },
      { id: 'd', impact: 'minor' },
      { id: 'e', impact: 'critical' },
    ];
    const classified = utils.classifyViolations(violations);
    assert.equal(classified.critical.length, 2);
    assert.equal(classified.serious.length, 1);
    assert.equal(classified.moderate.length, 1);
    assert.equal(classified.minor.length, 1);
  });

  test('无 impact 的违规归为 minor', () => {
    const violations = [{ id: 'x' }];
    const classified = utils.classifyViolations(violations);
    assert.equal(classified.minor.length, 1);
  });

  // ================================================================
  console.log('\n📋 测试: shouldBlock');
  // ================================================================

  test('0 违规 → 不阻断', () => {
    const result = utils.shouldBlock([]);
    assert.equal(result.block, false);
    assert.equal(result.blocks, 0);
  });

  test('1 critical → 阻断', () => {
    const result = utils.shouldBlock([{ id: 'x', impact: 'critical' }]);
    assert.equal(result.block, true);
    assert.equal(result.blocks, 1);
    assert.equal(result.critical, 1);
  });

  test('1 serious → 阻断', () => {
    const result = utils.shouldBlock([{ id: 'x', impact: 'serious' }]);
    assert.equal(result.block, true);
    assert.equal(result.blocks, 1);
    assert.equal(result.serious, 1);
  });

  test('1 moderate → 不阻断（但记录）', () => {
    const result = utils.shouldBlock([{ id: 'x', impact: 'moderate' }]);
    assert.equal(result.block, false);
    assert.equal(result.blocks, 0);
    assert.equal(result.moderate, 1);
  });

  test('1 minor → 不阻断', () => {
    const result = utils.shouldBlock([{ id: 'x', impact: 'minor' }]);
    assert.equal(result.block, false);
    assert.equal(result.blocks, 0);
    assert.equal(result.minor, 1);
  });

  test('混合违规: critical+moderate → 阻断', () => {
    const result = utils.shouldBlock([
      { id: 'a', impact: 'critical' },
      { id: 'b', impact: 'moderate' },
    ]);
    assert.equal(result.block, true);
    assert.equal(result.blocks, 1);
    assert.equal(result.critical, 1);
    assert.equal(result.moderate, 1);
  });

  // ================================================================
  console.log('\n📋 测试: formatViolationSummary');
  // ================================================================

  test('无违规返回指定信息', () => {
    const summary = utils.formatViolationSummary([]);
    assert.ok(summary.includes('无违规'));
  });

  test('格式化违规摘要包含 ID 和 impact', () => {
    const violations = [{ id: 'color-contrast', impact: 'serious', description: 'test', nodes: [{ target: ['.x'] }] }];
    const summary = utils.formatViolationSummary(violations);
    assert.ok(summary.includes('color-contrast'));
    assert.ok(summary.includes('serious'));
    assert.ok(summary.includes('.x'));
  });

  // ================================================================
  console.log('\n📋 测试: 真实基线 + 已知违规场景');
  // ================================================================

  test('真实基线: landmark-unique 在 el-breadcrumb 上可豁免', () => {
    const baseline = utils.loadBaseline(resolve(__dirname, 'a11y-baseline.json'));
    const violation = {
      id: 'landmark-unique',
      impact: 'moderate',
      nodes: [{ target: ['.el-breadcrumb[aria-label="Breadcrumb"]'] }],
    };
    const { exempted } = utils.isExempted(violation, baseline);
    assert.equal(exempted, true, 'el-breadcrumb landmark-unique 应被基线豁免');
  });

  test('真实基线: aria-prohibited-attr 在 header-collapse-btn 上不可豁免', () => {
    const baseline = utils.loadBaseline(resolve(__dirname, 'a11y-baseline.json'));
    const violation = {
      id: 'aria-prohibited-attr',
      impact: 'serious',
      nodes: [{ target: ['.header-collapse-btn'] }],
    };
    const { exempted } = utils.isExempted(violation, baseline);
    assert.equal(exempted, false, '自定义组件的 aria-prohibited-attr 不应被豁免');
  });

  // ---- 完整门禁流程仿真 ----
  console.log('\n📋 仿真: 完整门禁流程');

  test('场景A: 0 axe 违规 → PASS', () => {
    const baseline = utils.loadBaseline(resolve(__dirname, 'a11y-baseline.json'));
    const violations = [];
    const { remaining } = utils.filterBaselineViolations(violations, baseline);
    const gate = utils.shouldBlock(remaining);
    assert.equal(gate.block, false);
    assert.equal(gate.blocks, 0);
    console.log('     → 退出码 0 (通过)');
  });

  test('场景B: 1 critical 违规不在基线中 → FAIL', () => {
    const baseline = utils.loadBaseline(resolve(__dirname, 'a11y-baseline.json'));
    const violations = [
      { id: 'label', impact: 'critical', nodes: [{ target: ['#el-id-5859-23'] }], description: 'form element missing label' },
    ];
    const { remaining } = utils.filterBaselineViolations(violations, baseline);
    const gate = utils.shouldBlock(remaining);
    assert.equal(gate.block, true);
    assert.equal(gate.blocks, 1);
    assert.equal(gate.critical, 1);
    console.log('     → 退出码 1 (P0 缺陷)');
  });

  test('场景C: 仅豁免的违规 → PASS (无阻断)', () => {
    const baseline = utils.loadBaseline(resolve(__dirname, 'a11y-baseline.json'));
    const violations = [
      { id: 'landmark-unique', impact: 'moderate', nodes: [{ target: ['.el-breadcrumb[aria-label="Breadcrumb"]'] }] },
    ];
    const { remaining, removed } = utils.filterBaselineViolations(violations, baseline);
    assert.equal(removed.length, 1, '应被豁免');
    assert.equal(remaining.length, 0, '无剩余违规');
    const gate = utils.shouldBlock(remaining);
    assert.equal(gate.block, false);
    console.log('     → 退出码 0 (通过, 豁免项已记录)');
  });

  test('场景D: 仅 moderate/minor 违规 → PASS + 警告', () => {
    const baseline = utils.loadBaseline(resolve(__dirname, 'a11y-baseline.json'));
    const violations = [
      { id: 'region', impact: 'moderate', nodes: [{ target: ['.logo-text'] }], description: 'content not in landmark' },
    ];
    const { remaining } = utils.filterBaselineViolations(violations, baseline);
    const gate = utils.shouldBlock(remaining);
    assert.equal(gate.block, false);
    assert.equal(gate.moderate, 1);
    console.log('     → 退出码 0 (P2 警告，非阻断)');
  });

  // ================================================================
  // 结果
  // ================================================================
  console.log(`\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━`);
  console.log(`总计: ${passed + failed} | ✅ ${passed} | ❌ ${failed}`);
  console.log(`━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n`);

  process.exit(failed > 0 ? 1 : 0);
}

runTests().catch(err => {
  console.error('\n🚨 测试运行异常:', err);
  process.exit(1);
});
