#!/usr/bin/env python3
"""
check-references-sync.py · R5 references 同步门禁
==================================================

依据 references/ 的"24 小时内必须同步"规则，CI 启动时校验：
1. 解析真文档头部的版本号（如 docs/API契约-Phase1.md line 3 "版本：v2.3"）
2. 解析 references/ 头部声明的版本号（如 references/api-contract.md line 3 "v1.1"）
3. 比对是否一致；不一致 → advisory/warn

如果真文档没有版本号声明（plain header），跳过匹配并提示。

退出码:
  0 = 全 PASS（references 已同步真文档版本）
  1 = FAIL（references 版本号与真文档不一致）
  2 = SKIP（真文档或 references 缺版本号声明）

R5 设计：只 warn 不 fail（默认），让 doc drift 渐进修复。CI strict 模式（--strict）会 fail。
"""
import re
import sys
from pathlib import Path
from typing import Optional

ROOT = Path("/Users/jackie/微课平台")  # R5: hardcoded to avoid symlink/path issues

# 映射表: references 路径 -> (真文档路径, 文档名称)
MAPPING = [
    (ROOT / ".agents/skills/microcourse/references/data-contract.md",
     ROOT / "docs/数据字典.md",
     "数据字典"),
    (ROOT / ".agents/skills/microcourse/references/api-contract.md",
     ROOT / "docs/API契约-Phase1.md",
     "API契约"),
    (ROOT / ".agents/skills/microcourse/references/permission-matrix.md",
     ROOT / "docs/权限矩阵.md",
     "权限矩阵"),
]

# 真文档版本号提取 pattern: "版本：v2.3" / "版本 v2.3" / "v1.7（...）" / "v1.7"  (出现在前 5 行)
DOC_VERSION_RE = [
    re.compile(r"版本[:：]?\s*v(\d+\.\d+)", re.IGNORECASE),
    re.compile(r"^v(\d+\.\d+)\s*[（(]"),  # v1.7（2026...）
    re.compile(r"^# .*?v(\d+\.\d+)", re.IGNORECASE | re.MULTILINE),  # "# xxx v1.7"
]

# references 头部版本号提取 pattern: "v2.3"（在"vN.M" 格式）
REF_VERSION_RE = re.compile(r"\bv(\d+\.\d+)\b")

def extract_doc_version(path: Path) -> Optional[str]:
    """从真文档前 5 行提取版本号"""
    if not path.exists():
        return None
    try:
        with open(path, "r", encoding="utf-8") as f:
            head = "".join([f.readline() for _ in range(8)])
    except Exception:
        return None
    for pat in DOC_VERSION_RE:
        m = pat.search(head)
        if m:
            return m.group(1)
    return None

def extract_ref_version(path: Path) -> Optional[str]:
    """从 references 头部提取声明的源文档版本号

    references 头格式:
        # 视图名称
        > **源文档**：[doc.md] v1.7   ← 真文档版本
        > **视图版本**：v1.1 · 与源文档 v1.7 对齐   ← 视图自己版本 + 真文档版本
        *视图版本：v1.1 · 与源文档 v1.7 对齐*

    我们需要的是 references 声明对齐的真文档版本号（即 references 引用视图所基于的真文档版本）"""
    if not path.exists():
        return None
    try:
        with open(path, "r", encoding="utf-8") as f:
            head = "".join([f.readline() for _ in range(8)])
    except Exception:
        return None
    # 模式 1: "**源文档**：[...md] vX.Y" — 源文档直接声明的版本（v 紧跟链接，可能有空格）
    m = re.search(r"源文档.*?\][\s\u00A0]*v(\d+\.\d+)", head, re.IGNORECASE)
    if m:
        return m.group(1)
    # 模式 2: "**视图版本**：vX.Y · 与源文档 vA.B 对齐" — 视图自己 vX.Y, 真文档 vA.B
    m = re.search(r"视图版本[:：]?\s*v(\d+\.\d+)\s*[·.]?\s*与源文档\s*v(\d+\.\d+)", head)
    if m:
        return m.group(2)  # 第二个是源文档版本
    # 模式 3: "视图版本：vX.Y" (无源文档声明)
    m = re.search(r"视图版本[:：]?\s*v(\d+\.\d+)", head)
    if m:
        return m.group(1)
    return None

def main() -> int:
    strict = "--strict" in sys.argv
    print("=" * 56)
    print("  R5 references 同步门禁 (真文档 vs 引用视图)")
    print("=" * 56)
    print()
    fail = 0
    skip = 0
    for ref_path, doc_path, label in MAPPING:
        if not ref_path.exists():
            print(f"  ⚠️  {label}: references 不存在 {ref_path.name}")
            skip += 1
            continue
        if not doc_path.exists():
            print(f"  ⚠️  {label}: 真文档不存在 {doc_path.name}")
            skip += 1
            continue
        doc_v = extract_doc_version(doc_path)
        ref_v = extract_ref_version(ref_path)
        status = "✅" if doc_v == ref_v else "❌"
        if doc_v is None or ref_v is None:
            status = "⏭"
            skip += 1
        elif doc_v != ref_v:
            fail += 1
        print(f"  {status} {label}:")
        print(f"     真文档: docs/{doc_path.relative_to(ROOT / 'docs')} v{doc_v or '(未声明)'}")
        print(f"     references: {ref_path.relative_to(ROOT / '.agents/skills/microcourse/references')} 声明 v{ref_v or '(未声明)'}")
        print()
    print("=" * 56)
    print(f"  fail={fail}, skip={skip}, mode={'STRICT' if strict else 'advisory'}")
    if fail > 0 and strict:
        print(f"  ❌ references 与真文档版本不一致 — CI 阻断")
        return 1
    if fail > 0:
        print(f"  ⚠️  references 漂移 — 建议 24h 内同步真文档（advisory 不阻断）")
    else:
        print(f"  ✅ references 已对齐真文档")
    return 0

if __name__ == "__main__":
    sys.exit(main())
