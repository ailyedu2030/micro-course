#!/usr/bin/env python3
"""
generate-missing-tables.py · R6 给数据字典补 16 个缺失表的章节
============================================================
依据 contract-audit.py 报告, 数据字典 v1.7 缺 16 个 Entity @TableName 对应章节:
  Banner, CartItem, Certificate, CourseBundle, CourseBundleItem, CourseSection,
  ExerciseChapter, Grade, HermesCourseMapping, MicroSpecialtyCourseChapter,
  Order, Payment, PlatformShareConfig, PluginGrant, QuestionChapter, ReviewReport

这些表都是项目核心表（orders/payments/bundles 等），影响 AI 编码参考完整性。
R6 自动从 Entity 类提取字段并生成 stub 章节追加到 docs/数据字典.md 末尾，
每个章节标注 "R6 自动生成，staging 待人工审核"，后续可由 Phase 6 专题手工细化。
"""
import re
import sys
from pathlib import Path

ROOT = Path("/Users/jackie/微课平台")
DICT_PATH = ROOT / "docs/数据字典.md"
ENTITY_DIR = ROOT / "micro-course-api/src/main/java/com/microcourse/entity"

# 16 个缺失表
MISSING_TABLES = {
    "Banner": "banners",
    "CartItem": "cart_items",
    "Certificate": "certificates",
    "CourseBundle": "course_bundles",
    "CourseBundleItem": "course_bundle_items",
    "CourseSection": "course_sections",
    "ExerciseChapter": "exercise_chapters",
    "Grade": "grades",
    "HermesCourseMapping": "hermes_course_mapping",
    "MicroSpecialtyCourseChapter": "micro_specialty_course_chapters",
    "Order": "orders",
    "Payment": "payments",
    "PlatformShareConfig": "platform_share_config",
    "PluginGrant": "plugin_grants",
    "QuestionChapter": "question_chapters",
    "ReviewReport": "review_reports"
}

# Java primitive → TS type
PRIMITIVE_MAP = {
    "Long": "number", "Integer": "number", "Short": "number", "Byte": "number",
    "Double": "number", "Float": "number", "BigDecimal": "number",
    "String": "string", "Boolean": "boolean", "LocalDateTime": "string", "LocalDate": "string"
}

# 解析单个 Entity 文件
def parse_entity_java(entity_name: str):
    """从 entity/{Name}.java 解析 @TableName + 字段（含 @TableField db_col）

    R7-FIX: 只匹配 @TableField 注解的字段（确保 db_col 准确）。
    无 @TableField 注解的字段按字段名当作列名生成（与 R6 行为一致）。"""
    file_path = ENTITY_DIR / f"{entity_name}.java"
    if not file_path.exists():
        return None
    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()
    # @TableName("xxx")
    m = re.search(r'@TableName\("([^"]+)"\)', content)
    if not m:
        return None
    table_name = m.group(1)
    fields = []
    seen_fields = set()
    # 模式 1: @TableField("col") [可选注解] private Type name;
    pattern1 = re.compile(
        r'@TableField\("([^"]+)"\)\s*'
        r'(?:@\w+(?:\([^)]*\))?\s*)*'
        r'private\s+(\S+)\s+(\w+)\s*[;=]',
        re.DOTALL
    )
    for m in pattern1.finditer(content):
        db_col, java_type, field_name = m.group(1), m.group(2), m.group(3)
        if field_name in seen_fields or field_name == "serialVersionUID":
            continue
        seen_fields.add(field_name)
        ts_type = PRIMITIVE_MAP.get(java_type, "string")
        fields.append({
            "name": field_name,
            "db_col": db_col,
            "java_type": java_type,
            "ts_type": ts_type,
            "constraints": ""
        })
    # 模式 2: 简单 private Type name; (无 @TableField) — 字段名即列名
    # 必须确保行首不是 'private' 修饰符（避免重复模式 1 匹配）
    pattern2 = re.compile(
        r'^\s+private\s+(\S+)\s+(\w+)\s*;',
        re.MULTILINE
    )
    for m in pattern2.finditer(content):
        java_type, field_name = m.group(1), m.group(2)
        if field_name in seen_fields or field_name == "serialVersionUID":
            continue
        # 跳过 Lombok 风格（getter/setter 在文件中也有 'private'）
        # 简单启发: 字段必须在 class 体内且不在方法内 — 通过缩进判断
        seen_fields.add(field_name)
        ts_type = PRIMITIVE_MAP.get(java_type, "string")
        fields.append({
            "name": field_name,
            "db_col": field_name,
            "java_type": java_type,
            "ts_type": ts_type,
            "constraints": ""
        })
    return table_name, fields, file_path

# 生成 markdown 章节
def generate_section(entity_name, db_table, fields, section_number):
    lines = []
    # R6-FIX: 用 db_table 名（带下划线，如 course_bundles）作为章节标识，
    # 才能被 contract-audit.py 的 parse_data_dictionary 正则 ^###\s+[\d.]+[\s]+(\w+) 匹配
    # 并在 dict_tables 中找到对应表名（与 Entity @TableName 一致）。
    lines.append(f"### {section_number} {db_table} — {entity_name} 表")
    lines.append("")
    lines.append(f"> R6 自动生成（{entity_name} 实体 / @TableName(\"{db_table}\")），字段约束待人工审核。")
    lines.append("")
    lines.append("| 字段名 | DB 列 | Java 类型 | TS 类型 | 长度 | 约束 | 说明 |")
    lines.append("|--------|-------|-----------|---------|------|------|------|")
    for f in fields:
        lines.append(f"| {f['name']} | {f['db_col']} | {f['java_type']} | {f['ts_type']} | - | - | R6 待人工补 |")
    lines.append("")
    return "\n".join(lines)

def main():
    if not DICT_PATH.exists():
        print(f"❌ 数据字典不存在: {DICT_PATH}")
        sys.exit(1)
    with open(DICT_PATH, "r", encoding="utf-8") as f:
        content = f.read()

    sections_added = []
    section_counter = 12  # 接着已有 1.1-1.3 用户/院系/专业，继续 1.4-1.19
    for entity_name, db_table in MISSING_TABLES.items():
        parsed = parse_entity_java(entity_name)
        if not parsed:
            print(f"  ⚠️  {entity_name}.java 未找到或缺 @TableName，跳过")
            continue
        table_name, fields, file_path = parsed
        if table_name != db_table:
            print(f"  ⚠️  {entity_name} @TableName={table_name} ≠ 期望 {db_table}")
            continue
        section_num = f"1.{section_counter}"
        section_counter += 1
        section = generate_section(entity_name, db_table, fields, section_num)
        # 追加到文件末尾
        content = content.rstrip() + "\n\n" + section
        sections_added.append(f"{entity_name} ({len(fields)} fields)")
        print(f"  ✅ {entity_name} → {len(fields)} fields (### {section_num})")

    if not sections_added:
        print("ℹ️  没有可添加的章节")
        return

    with open(DICT_PATH, "w", encoding="utf-8") as f:
        f.write(content)
    print(f"\n✅ 已为 {len(sections_added)} 个表添加 stub 章节:")
    for s in sections_added:
        print(f"  - {s}")

if __name__ == "__main__":
    main()
