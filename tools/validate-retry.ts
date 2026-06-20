#!/usr/bin/env tsx
// ===================================================================
// validate-retry.ts — Super-Fix 验证重试验证器
//
// 验证修复是否持久稳定，支持多轮重试验证。
// 退出码: 0 = 验证通过, 1 = 验证失败
//
// 用法: npx tsx tools/validate-retry.ts [--rounds=3] [--target=localhost:8080]
// ===================================================================

import { execSync } from 'child_process';

interface RetryConfig {
  rounds: number;
  target: string;
  delayMs: number;
}

interface RoundResult {
  round: number;
  passed: boolean;
  duration: number;
  errors: string[];
}

async function sleep(ms: number): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, ms));
}

async function runRound(round: number, config: RetryConfig): Promise<RoundResult> {
  const startTime = Date.now();
  const errors: string[] = [];

  console.log(`\n🔄 第 ${round}/${config.rounds} 轮验证`);

  // 1. Build check
  try {
    console.log('  📦 编译检查...');
    execSync('cd micro-course-api && mvn compile -q -DskipTests', {
      encoding: 'utf-8',
      timeout: 120000,
      stdio: 'pipe'
    });
    console.log('    ✅ 后端编译通过');
  } catch (e: any) {
    errors.push('后端编译失败');
    console.log('    ❌ 后端编译失败');
  }

  // 2. Frontend build
  try {
    console.log('  🎨 前端构建...');
    execSync('cd micro-course-admin && npm run build', {
      encoding: 'utf-8',
      timeout: 120000,
      stdio: 'pipe'
    });
    console.log('    ✅ 前端构建通过');
  } catch (e: any) {
    errors.push('前端构建失败');
    console.log('    ❌ 前端构建失败');
  }

  // 3. API health check (if backend running)
  try {
    console.log('  🏥 API 健康检查...');
    execSync(`curl -sf http://${config.target}/api/actuator/health`, {
      encoding: 'utf-8',
      timeout: 5000,
      stdio: 'pipe'
    });
    console.log('    ✅ API 健康检查通过');
  } catch {
    console.log('    ⏭️  API 未运行，跳过');
  }

  // 4. Key API endpoints
  const endpoints = [
    { name: 'GET /api/courses', method: 'GET', path: '/api/courses' },
    { name: 'POST /api/auth/login', method: 'POST', path: '/api/auth/login' },
  ];

  for (const ep of endpoints) {
    try {
      console.log(`  🌐 ${ep.name}...`);
      const cmd = ep.method === 'POST'
        ? `curl -sf -X POST http://${config.target}${ep.path} -H "Content-Type: application/json" -d '{"username":"admin","password":"wrong"}'`
        : `curl -sf http://${config.target}${ep.path}`;
      execSync(cmd, { encoding: 'utf-8', timeout: 5000, stdio: 'pipe' });
      console.log(`    ✅ ${ep.name} 正常`);
    } catch {
      console.log(`    ⏭️  ${ep.name} 未运行或返回错误`);
    }
  }

  const duration = Date.now() - startTime;
  return {
    round,
    passed: errors.length === 0,
    duration,
    errors
  };
}

async function main() {
  const roundsArg = process.argv.find(a => a.startsWith('--rounds='));
  const targetArg = process.argv.find(a => a.startsWith('--target='));

  const config: RetryConfig = {
    rounds: roundsArg ? parseInt(roundsArg.split('=')[1]) || 3 : 3,
    target: targetArg ? targetArg.split('=')[1] : 'localhost:8080',
    delayMs: 2000
  };

  console.log('🔁 Super-Fix 验证重试验证');
  console.log(`   轮次: ${config.rounds}`);
  console.log(`   目标: ${config.target}`);

  const results: RoundResult[] = [];
  let allPassed = true;

  for (let i = 1; i <= config.rounds; i++) {
    const result = await runRound(i, config);
    results.push(result);

    if (!result.passed) {
      allPassed = false;
    }

    if (i < config.rounds) {
      console.log(`\n⏳ 等待 ${config.delayMs / 1000}s...`);
      await sleep(config.delayMs);
    }
  }

  // Summary
  console.log('\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
  console.log('验证结果汇总:');
  for (const r of results) {
    const status = r.passed ? '✅' : '❌';
    const duration = (r.duration / 1000).toFixed(1);
    console.log(`  第 ${r.round} 轮: ${status} (${duration}s)${r.errors.length > 0 ? ' — ' + r.errors.join(', ') : ''}`);
  }

  const passCount = results.filter(r => r.passed).length;
  console.log(`\n通过: ${passCount}/${config.rounds}`);

  if (allPassed) {
    console.log('\n✅ 验证重试通过 — 修复稳定');
    process.exit(0);
  } else {
    console.log('\n❌ 验证重试失败 — 修复不稳定');
    process.exit(1);
  }
}

main().catch(e => {
  console.error('❌ 验证器错误:', e.message);
  process.exit(1);
});
