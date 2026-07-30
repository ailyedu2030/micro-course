#!/usr/bin/env python3
"""
add-viewonly-tables.py · R9 给数据字典补 10 个 view-only 关联表章节
=============================================================
R9 审计发现 10 张表在 DB 真实存在但无 Java Entity (Phase 14-15 引入的关联表，
如 proposal_courses/section_quizzes 等). 写脚本从 PG catalog 自动提取 schema
并追加到 docs/数据字典.md, 同时给 audit 加白名单避免 advisory 重复。
"""
import re
import subprocess
from pathlib import Path

ROOT = Path("/Users/jackie/微课平台")
DICT_PATH = ROOT / "docs/数据字典.md"

# 10 个 view-only 关联表 (DB 真实存在, Java 无 Entity)
VIEW_ONLY_TABLES = [
    "question_tag_relations",
    "slide_pages",
    "proposal_courses",
    "proposal_team_members",
    "proposal_signatures",
    "proposal_shared_units",
    "proposal_lead_courses",
    "section_quizzes",
    "section_tasks",
    "section_reflections",
]

# 通过 psql 获取 schema
def get_table_schema(table_name: str) -> list:
    """返回 [(col_name, data_type, not_null, default), ...]"""
    try:
        # R9-FIX: 用绝对路径 /usr/local/bin/docker, 因为 subprocess 找不到 PATH 中的 docker
        result = subprocess.run(
            ["/usr/local/bin/docker", "exec", "microcourse-pg-test",
             "psql", "-U", "postgres", "-d", "micro_course_test", "-c",
             f"\\d {table_name}"],
            env={"PGPASSWORD": "postgres", "PATH": "/usr/local/bin:/usr/bin:/bin"},
            capture_output=True, text=True, timeout=10
        )
        columns = []
        for line in result.stdout.split('\n'):
            # R9-FIX-4: psql \d 输出 line 可能有 leading | 也可能没有
            # 简单方法: line 含 '|' 即可。split 后取前 5 个数据列
            if '|' not in line:
                continue
            parts = [p.strip() for p in line.split('|')]
            # psql 默认 5 列 (col, dtype, collation, nullable, default)
            # 但部分 line 可能 col 数不同 (e.g. heading)
            if len(parts) < 5:
                continue
            # 跳过 separator 和 header
            if '---' in line or 'Indexes:' in line or 'Foreign-key' in line:
                continue
            col = parts[0]
            if col in ('Column',):
                continue
            # 数据行: col | dtype | (collation) | nullable | default
            # 实际: parts[0]=col, parts[1]=dtype, 倒数第 2=nullable, 倒数第 1=default
            dtype = parts[1] if len(parts) > 1 else ''
            nullable = parts[-2] if len(parts) >= 2 else ''
            default = parts[-1] if len(parts) >= 1 else ''
            # 对于" id | bigint | | not null | nextval(...)" parts 可能是
            # ['id', 'bigint', '', 'not null', "nextval(...)"] -> len 5
            # 对于" deleted_at | timestamp without time zone | | | " parts 是
            # ['deleted_at', 'timestamp without time zone', '', '', ''] -> len 5
            not_null = 'not null' in nullable.lower()
            has_default = bool(default) and default != '-' and 'nextval' not in default.lower()
            columns.append((col, dtype, not_null, has_default))
        return columns
    except Exception as e:
        print(f"  ⚠️  get_table_schema({table_name}) failed: {e}")
        return []

# 复数化表名作为章节名 (e.g. question_tag_relations -> question_tag_relations)
def section_title(table: str) -> str:
    return table

# 生成数据字典章节
def generate_section(table: str, columns: list) -> str:
    lines = []
    section_num = f"3.{VIEW_ONLY_TABLES.index(table) + 1}"
    lines.append(f"### {section_num} {table} — {table} 表 (view-only)")
    lines.append("")
    lines.append(f"> R9 自动生成: 关联表 / view-only. DB 真实存在, Java 端无对应 Entity (Phase 14-15 引入, 通过 SQL/Service 直接操作).")
    lines.append("")
    if not columns:
        lines.append("| 字段名 | DB 列 | Java 类型 | TS 类型 | 长度 | 约束 | 说明 |")
        lines.append("|--------|-------|-----------|---------|------|------|------|")
        lines.append("| id | id | Long | number | - | PK | 主键 |")
        lines.append("")
        return "\n".join(lines)
    lines.append("| 字段名 | DB 列 | Java 类型 | TS 类型 | 长度 | 约束 | 说明 |")
    lines.append("|--------|-------|-----------|---------|------|------|------|")
    for col, dtype, not_null, has_default in columns:
        col_type = "number" if "int" in dtype.lower() or "numeric" in dtype.lower() or "double" in dtype.lower() else (
            "boolean" if "bool" in dtype.lower() else "string"
        )
        java_type = "Long" if "bigint" in dtype.lower() else (
            "Integer" if "integer" in dtype.lower() else (
                "Boolean" if "bool" in dtype.lower() else (
                    "BigDecimal" if "numeric" in dtype.lower() else (
                        "LocalDateTime" if "timestamp" in dtype.lower() else "String"
                    )
                )
            )
        )
        constraint = "NOT NULL" if not_null else ""
        if has_default:
            constraint += " DEFAULT"
        lines.append(f"| {col} | {col} | {java_type} | {col_type} | - | {constraint.strip()} | |")
    lines.append("")
    return "\n".join(lines)

def main():
    if not DICT_PATH.exists():
        print(f"❌ 数据字典不存在: {DICT_PATH}")
        return
    with open(DICT_PATH, "r", encoding="utf-8") as f:
        content = f.read()

    sections_added = []
    for table in VIEW_ONLY_TABLES:
        # 已存在章节? 跳过
        if f"### 3.{VIEW_ONLY_TABLES.index(table) + 1} {table}" in content or f"### R9.3.{VIEW_ONLY_TABLES.index(table) + 1} {table}" in content:
            print(f"  ⏭  {table} 章节已存在, 跳过")
            continue
        cols = get_table_schema(table)
        if not cols:
            print(f"  ⚠️  {table} 字段未获取, 跳过")
            continue
        section = generate_section(table, cols)
        content = content.rstrip() + "\n\n" + section
        sections_added.append(f"{table} ({len(cols)} fields)")
        print(f"  ✅ {table} → {len(cols)} fields")

    if not sections_added:
        print("ℹ️  没有可添加的章节")
        return

    with open(DICT_PATH, "w", encoding="utf-8") as f:
        f.write(content)
    print(f"\n✅ 已为 {len(sections_added)} 个 view-only 表添加章节:")
    for s in sections_added:
        print(f"  - {s}")

if __name__ == "__main__":
    main()
