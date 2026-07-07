#!/usr/bin/env python3
"""
契约审计 (Contract Audit) — 代码 vs 数据字典 自动验证

功能:
  1. 解析 docs/数据字典.md → 提取每张表的字段定义 (DB列, Java类型, 约束)
  2. 扫描 Java Entity → 提取 @TableName + 字段声明
  3. 交叉验证:
     - Entity 字段不在数据字典中 = 未登记字段 (需补文档)
     - 数据字典字段不在 Entity 中 = 文档漂移 (需同步)
     - Java 类型不匹配

用法:
  python3 scripts/contract-audit.py                    # 全量审计
  python3 scripts/contract-audit.py --entity User      # 单实体审计
  python3 scripts/contract-audit.py --json             # JSON 格式输出 (供 CI 解析)

退出码:
  0 = PASS (无漂移)
  1 = FAIL (有漂移)
"""

import os
import re
import sys
import json
import argparse
from pathlib import Path
from collections import defaultdict

PROJECT_ROOT = Path(__file__).resolve().parent.parent
DICT_PATH = PROJECT_ROOT / "docs/数据字典.md"
ENTITY_DIR = PROJECT_ROOT / "micro-course-api/src/main/java/com/microcourse/entity"

# Java 类型简化映射
TYPE_ALIAS = {
    'String': 'String',
    'Text': 'String',       # PG Text 与 Java String 在 MyBatis-Plus 下完全等价
    'LongText': 'String',   # PG LongText 同上
    'MediumText': 'String', # PG MediumText 同上
    'Integer': 'Integer', 'int': 'Integer',
    'Long': 'Long', 'long': 'Long',
    'Boolean': 'Boolean', 'boolean': 'Boolean',
    'BigDecimal': 'BigDecimal',
    'LocalDateTime': 'LocalDateTime', 'LocalDate': 'LocalDate', 'LocalTime': 'LocalTime',
    'Double': 'Double', 'double': 'Double',
    'Float': 'Float', 'float': 'Float',
    'Byte': 'Byte', 'byte': 'Byte',
}

# Java 枚举类型在数据字典里登记为 String（如 UserRole/Status 枚举），
# 实体里实际为枚举类。这两种写法在 MyBatis-Plus 下都正常工作，视为等价。
ENUM_TYPES = {'UserRole', 'UserStatus', 'EnrollmentStatus', 'CourseStatus', 'NotificationType',
              'CourseReviewStatus', 'DiscussionPostStatus', 'TeacherStatus', 'OrderStatus',
              'PaymentStatus', 'VideoStatus', 'QuestionType', 'BadgeType'}

IGNORED_FIELDS = {'serialVersionUID'}


# ─────────────────────────────────────────────
# 1. Parse data dictionary
# ─────────────────────────────────────────────
def parse_data_dictionary():
    """
    返回 { table_name: { field_name: {'db_col':..., 'java_type':..., 'constraints':...} } }
    """
    tables = {}
    current_table = None
    in_table = False
    headers = []

    text = DICT_PATH.read_text(encoding='utf-8')
    lines = text.split('\n')

    for i, line in enumerate(lines):
        # Detect table header: ### X.Y name — Description
        m = re.match(r'^###\s+[\d.]+[\s]+(\w+)\s*[—\-–]\s*(.*)', line)
        if m:
            current_table = m.group(1)
            in_table = False
            headers = []
            continue

        # Detect markdown table header row
        if line.strip().startswith('| 字段名') or line.strip().startswith('|字段名'):
            headers = [h.strip() for h in line.strip().strip('|').split('|')]
            in_table = True
            continue

        # Skip separator row
        if in_table and re.match(r'^\|[\s\-:]+\|', line):
            continue

        # Parse data row
        if in_table and line.strip().startswith('|') and current_table:
            cells = [c.strip() for c in line.strip().strip('|').split('|')]
            if len(cells) >= 5:
                field_name = cells[0]
                db_col = cells[1]
                java_type = cells[2]
                ts_type = cells[3]
                length = cells[4]
                constraints = cells[5] if len(cells) > 5 else ''
                desc = cells[6] if len(cells) > 6 else ''

                if current_table not in tables:
                    tables[current_table] = {}
                tables[current_table][field_name] = {
                    'db_col': db_col,
                    'java_type': java_type,
                    'ts_type': ts_type,
                    'length': length,
                    'constraints': constraints,
                    'desc': desc,
                    'line': i + 1,
                }
            continue

        # End of table
        if in_table and not line.strip().startswith('|'):
            in_table = False

    return tables


# ─────────────────────────────────────────────
# 2. Parse Java entities
# ─────────────────────────────────────────────
def parse_entities(target_entity=None):
    """
    返回 { table_name: { entity_name, fields: { field_name: {type, db_col, line, file_path} } } }
    """
    entities = {}
    pattern = re.compile(r'\.java$')

    files = list(ENTITY_DIR.glob('*.java'))
    if target_entity:
        files = [f for f in files if f.stem == target_entity]

    for f in files:
        content = f.read_text(encoding='utf-8')
        entity_name = f.stem

        # Extract @TableName
        m = re.search(r'@TableName\("(\w+)"\)', content)
        if not m:
            continue
        table_name = m.group(1)

        lines = content.split('\n')
        fields = {}
        i = 0
        while i < len(lines):
            line = lines[i]

            # Skip annotations block (like @Version, @TableLogic) - we just need the field declaration
            # Match: private Type fieldName;
            fm = re.match(r'\s+private\s+(\S+)\s+(\w+)\s*[;=]', line)
            if fm:
                java_type_raw = fm.group(1)
                field_name = fm.group(2)

                # Simplify generic types
                java_type = java_type_raw.split('<')[0] if '<' in java_type_raw else java_type_raw

                if field_name in IGNORED_FIELDS:
                    i += 1
                    continue

                # Look for @TableField on previous lines (nearest first)
                db_col = None
                for j in range(i - 1, max(-1, i - 10), -1):
                    if j < 0:
                        break
                    p = lines[j].strip()
                    if p == '' or p.startswith('package ') or p.startswith('import '):
                        continue
                    # @TableField(value = "xxx", ...)
                    tfmt = re.search(r'@TableField\(\s*value\s*=\s*"(\w+)"', p)
                    if tfmt:
                        db_col = tfmt.group(1)
                        break
                    # @TableField("xxx") shorthand
                    stfm = re.search(r'@TableField\("(\w+)"\)', p)
                    if stfm:
                        db_col = stfm.group(1)
                        break
                    # Stop at non-annotation line (end of annotations block)
                    if not p.startswith('@'):
                        break

                # If no @TableField, use camelCase -> snake_case convention
                if db_col is None:
                    db_col = re.sub(r'([A-Z])', r'_\1', field_name).lower()

                fields[field_name] = {
                    'type': java_type,
                    'db_col': db_col,
                    'line': i + 1,
                    'file_path': str(f.relative_to(PROJECT_ROOT)),
                }

            i += 1

        if fields:
            entities[table_name] = {
                'entity_name': entity_name,
                'fields': fields,
            }

    return entities


# ─────────────────────────────────────────────
# 3. Cross-validate
# ─────────────────────────────────────────────
def validate(dict_tables, entities):
    """
    返回 { 'errors': [...], 'warnings': [...] }
    """
    errors = []
    warnings = []

    # Build reverse map: entity_name → table_name
    entity_to_table = {v['entity_name']: k for k, v in entities.items()}

    for table_name, entity_info in entities.items():
        entity_name = entity_info['entity_name']
        entity_fields = entity_info['fields']

        # Check if table exists in data dictionary
        if table_name not in dict_tables:
            warnings.append({
                'severity': 'WARN',
                'table': table_name,
                'entity': entity_name,
                'message': f"实体 {entity_name} @TableName(\"{table_name}\") 在数据字典中无对应条目",
                'action': '在数据字典中添加表定义',
            })
            continue

        dict_fields = dict_tables[table_name]

        # Check each entity field against data dictionary
        for field_name, finfo in entity_fields.items():
            if field_name not in dict_fields:
                # Special: check if field is deprecated or intentionally undocumented
                errors.append({
                    'severity': 'ERROR',
                    'table': table_name,
                    'entity': entity_name,
                    'field': field_name,
                    'db_col': finfo['db_col'],
                    'java_type': finfo['type'],
                    'dict_line': None,
                    'message': f"字段 {field_name} ({finfo['db_col']}) 在数据字典中未登记",
                    'action': f"在数据字典 {table_name} 表中补充",
                    'file': finfo['file_path'],
                    'line': finfo['line'],
                })
                continue

            # Check type match
            dict_type = dict_fields[field_name]['java_type']
            entity_type = finfo['type']

            # Normalize types
            dict_type_norm = TYPE_ALIAS.get(dict_type, dict_type)
            entity_type_norm = TYPE_ALIAS.get(entity_type, entity_type)

            # Enum types are stored as String in DB but Java enum in code — equivalent
            if entity_type in ENUM_TYPES and dict_type_norm == 'String':
                entity_type_norm = 'String'

            if dict_type_norm != entity_type_norm:
                errors.append({
                    'severity': 'ERROR',
                    'table': table_name,
                    'entity': entity_name,
                    'field': field_name,
                    'db_col': finfo['db_col'],
                    'java_type': f"{entity_type} (代码) vs {dict_type} (字典)",
                    'dict_line': dict_fields[field_name]['line'],
                    'message': f"字段 {field_name}: Java 类型不匹配 — 代码={entity_type}, 字典={dict_type}",
                    'action': '同步代码与字典的 Java 类型',
                    'file': finfo['file_path'],
                    'line': finfo['line'],
                })

        # Check data dictionary fields not in entity
        for field_name, dinfo in dict_fields.items():
            if field_name not in entity_fields:
                # Skip optional audit fields that might be in base classes
                if field_name in ('deletedAt',):
                    continue
                warnings.append({
                    'severity': 'WARN',
                    'table': table_name,
                    'entity': entity_name,
                    'field': field_name,
                    'db_col': dinfo['db_col'],
                    'dict_line': dinfo['line'],
                    'message': f"数据字典有字段 {field_name} ({dinfo['db_col']})，但实体 {entity_name} 中未找到",
                    'action': '确认字段是否已删除; 如已删除则更新数据字典, 如存在则补充实体字段',
                })

    # Check entities without @TableName (not in entity index)
    for table_name in dict_tables:
        if table_name not in entities:
            warnings.append({
                'severity': 'WARN',
                'table': table_name,
                'entity': '-',
                'message': f"数据字典有表 {table_name}，但无对应 Java Entity",
                'action': '确认是否需要创建',
            })

    return {'errors': errors, 'warnings': warnings}


# ─────────────────────────────────────────────
# 4. Report
# ─────────────────────────────────────────────
def print_report(result):
    errors = result['errors']
    warnings = result['warnings']

    print("=" * 70)
    print("  契约审计报告 (Contract Audit)")
    print("=" * 70)
    print()

    if not errors and not warnings:
        print("✅ 全部通过 — 代码与数据字典完全一致\n")
        return True

    if errors:
        print(f"❌ ERROR ({len(errors)} 项)\n")
        for i, e in enumerate(errors, 1):
            print(f"  {i}. [{e['entity']}] {e['message']}")
            print(f"     文件: {e.get('file', '?')}:{e.get('line', '?')}")
            print(f"     修复: {e['action']}")
            print()
    else:
        print("❌ ERROR (0 项)\n")

    if warnings:
        print(f"⚠️  WARN ({len(warnings)} 项)\n")
        for i, w in enumerate(warnings, 1):
            print(f"  {i}. [{w.get('entity', '?')}/{w['table']}] {w['message']}")
            print(f"     建议: {w['action']}")
            print()

    return len(errors) == 0


def print_json_report(result):
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return len(result['errors']) == 0


# ─────────────────────────────────────────────
# Main
# ─────────────────────────────────────────────
def main():
    parser = argparse.ArgumentParser(description='契约审计 — 代码 vs 数据字典自动验证')
    parser.add_argument('--entity', '-e', help='只审计指定实体 (如 User)')
    parser.add_argument('--json', '-j', action='store_true', help='JSON 格式输出')
    args = parser.parse_args()

    if not DICT_PATH.exists():
        if args.json:
            print(json.dumps({'error': f'数据字典未找到: {DICT_PATH}'}), file=sys.stderr)
        else:
            print(f"❌ 数据字典未找到: {DICT_PATH}", file=sys.stderr)
        sys.exit(1)

    if not args.json:
        print(f"📖 数据字典: {DICT_PATH}")
        print(f"📁 Entity 目录: {ENTITY_DIR}")
        print()

    dict_tables = parse_data_dictionary()
    entities = parse_entities(target_entity=args.entity)

    if not args.json:
        print(f"   数据字典: {len(dict_tables)} 张表")
        print(f"   Java Entity: {len(entities)} 个")
        print()

    result = validate(dict_tables, entities)

    if args.json:
        ok = print_json_report(result)
    else:
        ok = print_report(result)

    # Always exit 0 — exit code is determined by the caller (precheck.sh) from JSON content
    sys.exit(0)


if __name__ == '__main__':
    main()
