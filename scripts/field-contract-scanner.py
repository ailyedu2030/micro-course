#!/usr/bin/env python3
"""
字段完全性契约扫描器 (Field Completeness Contract Scanner)

用法: python3 scripts/field-contract-scanner.py
输出: docs/开发规划/FIELDS_CONTRACT.md

功能:
1. 提取所有实体类字段 → 字段集 A（"后端正典"）
2. 提取所有 Vue 文件表单 prop + 表格列 prop → 字段集 B（"前端引用"）
3. 交叉比对 → ORPHAN / MATCH / FIELD_MISMATCH
4. 对每个 FK/关联字段 → 检查对应实体是否有管理入口
"""

import os
import re
import glob
from pathlib import Path

PROJECT_ROOT = Path("/Users/jackie/微课平台")
ENTITY_DIR = PROJECT_ROOT / "micro-course-api/src/main/java/com/microcourse/entity"
DTO_DIR = PROJECT_ROOT / "micro-course-api/src/main/java/com/microcourse/dto"
VIEW_DIR = PROJECT_ROOT / "micro-course-admin/src/views"
API_DIR = PROJECT_ROOT / "micro-course-admin/src/api"
ROUTER_FILE = PROJECT_ROOT / "micro-course-admin/src/router/index.js"
CONTROLLER_DIR = PROJECT_ROOT / "micro-course-api/src/main/java/com/microcourse/controller"

# ─────────────────────────────────────────────────
# 1. Extract entity fields
# ─────────────────────────────────────────────────
def extract_entity_fields():
    """Return { EntityName: { fieldName: {type, comment, is_fk} } }"""
    entities = {}
    for f in sorted(list(ENTITY_DIR.glob("*.java")) + list(DTO_DIR.glob("*.java"))):
        name = f.stem
        fields = {}
        content = f.read_text()
        lines = content.split('\n')
        for i, line in enumerate(lines):
            # Match: private Type fieldName;
            m = re.match(r'\s+private\s+(\w+(?:<[\w,]+>)?)\s+(\w+)\s*[;=]', line)
            if m and not line.strip().startswith('//') and not line.strip().startswith('/*'):
                ftype = m.group(1)
                fname = m.group(2)
                # Skip static, serialVersionUID
                if fname in ('serialVersionUID',):
                    continue
                # Get comment from line above
                comment = ''
                if i > 0:
                    cm = re.search(r'//\s*(.+)', lines[i-1])
                    if cm:
                        comment = cm.group(1).strip()
                # Check if it's a FK field (ending in Id, or named xxxId)
                is_fk = fname.endswith('Id') or fname.endswith('ids') or fname == 'id'
                fields[fname] = {'type': ftype, 'comment': comment, 'is_fk': is_fk}
        if fields:
            entities[name] = fields
    return entities

# ─────────────────────────────────────────────────
# 2. Extract frontend references
# ─────────────────────────────────────────────────
def extract_frontend_fields():
    """Return { 'vue_file_path': { 'props': [...], 'form_fields': [...], 'api_fields': [...] } }"""
    vue_refs = {}
    
    for f in sorted(VIEW_DIR.rglob("*.vue")):
        rel_path = str(f.relative_to(PROJECT_ROOT))
        content = f.read_text()
        
        # Find all el-table-column prop
        table_props = re.findall(r'el-table-column[^>]*prop="(\w+)"', content)
        
        # Find all el-form-item prop
        form_props = re.findall(r'el-form-item[^>]*prop="(\w+)"', content)
        
        # Find all v-model references that are field-like
        vmodels = re.findall(r'v-model[^=]*=["\'](?:formData|createForm|editForm|searchForm|form)\.(\w+)', content)
        
        # Find all :form-data prop references
        form_data = re.findall(r'[:]form-data[^=]*=["\'](\w+)', content)
        
        # Collect unique fields
        all_fields = list(set(table_props + form_props + vmodels + form_data))
        
        if all_fields:
            vue_refs[rel_path] = {
                'table_props': table_props,
                'form_props': form_props,
                'vmodels': vmodels,
                'all': all_fields
            }
    
    return vue_refs

# ─────────────────────────────────────────────────
# 3. Extract controllers (management endpoints)
# ─────────────────────────────────────────────────
def extract_controllers():
    """Return { ControllerName: { base_path, methods } }"""
    controllers = {}
    for f in sorted(CONTROLLER_DIR.glob("*Controller.java")):
        name = f.stem.replace("Controller", "")
        content = f.read_text()
        # Find RequestMapping
        base = re.search(r'@RequestMapping\("([^"]*)"\)', content)
        base_path = base.group(1) if base else ''
        
        # Find all endpoints
        methods = set()
        for m in re.finditer(r'@(GetMapping|PostMapping|PutMapping|DeleteMapping)\(?"?([^")]*)', content):
            methods.add(f"{m.group(1)} {m.group(2)}")
        
        controllers[name] = {'base': base_path, 'methods': methods}
    return controllers

# ─────────────────────────────────────────────────
# 4. Extract API files (frontend API calls)
# ─────────────────────────────────────────────────
def extract_api_calls():
    """Return { api_file: [function_names] }"""
    apis = {}
    for f in sorted(API_DIR.glob("*.js")):
        content = f.read_text()
        funcs = re.findall(r'export\s+(?:async\s+)?function\s+(\w+)', content)
        if funcs:
            apis[f.name] = funcs
    return apis

# ─────────────────────────────────────────────────
# 5. Cross-reference: FK fields → management page check
# ─────────────────────────────────────────────────
def check_fk_mgmt(field_name, entity_name, controllers, vue_refs):
    """
    For a FK field like teacherId / counselorId / departmentId:
    - Does the target entity have a Controller?
    - Does the target entity have a Vue list page?
    Returns: (status, evidence)
    """
    # Map FK field name to likely entity
    fk_base = field_name.replace('Id', '').replace('ids', '')
    # Handle special mappings
    mappings = {
        'teacher': 'User',  # teacherId → User entity
        'counselor': 'User',
        'creator': 'User',
        'user': 'User',
        'student': 'User',
        'lead': 'User',
        'reviewer': 'User',
        'category': 'CourseCategory',
        'course': 'Course',
        'major': 'Major',
        'department': 'Department',
        'bundle': 'CourseBundle',
        'tag': 'Tag',
        'class': 'Classes',
        'chapter': 'CourseChapter',
        'lesson': 'Lesson',
        'video': 'Video',
        'question': 'Question',
        'exercise': 'Exercise',
        'enrollment': 'Enrollment',
        'micro_specialty': 'MicroSpecialty',
        'specialty': 'MicroSpecialty',
        'proposal': 'MicroSpecialtyProposal',
        'order': 'Order',
        'payment': 'Payment',
        'parent': None,  # self-ref
    }
    
    target_entity = mappings.get(fk_base.lower(), fk_base.capitalize())
    
    if target_entity is None:
        return ('SELF_REF', 'self-referencing')
    
    # Check if controller exists
    controller_name = f"{target_entity}Controller"
    has_controller = controller_name in controllers
    
    # Check if Vue list page exists with entity name
    vue_patterns = [
        f"views/{target_entity.lower()}",
        f"views/{target_entity.lower()}s",
        f"views/{target_entity.lower()}List",
        f"views/admin/{target_entity.lower()}",
        f"views/teacher/{target_entity.lower()}",
    ]
    has_vue_page = any(
        any(vp.startswith(p) for p in vue_patterns)
        for vp in vue_refs.keys()
    )
    
    if has_controller and has_vue_page:
        return ('MANAGED', f'{controller_name} + {[v for v in vue_refs.keys() if any(v.startswith(p) for p in vue_patterns)][:2]}')
    elif has_controller:
        return ('NO_VUE', f'{controller_name} exists, no Vue list page')
    elif has_vue_page:
        return ('NO_API', f'{[v for v in vue_refs.keys() if any(v.startswith(p) for p in vue_patterns)][:2]} exists, no Controller')
    else:
        return ('UNMANAGED', f'No controller or list page for {target_entity}')

# ─────────────────────────────────────────────────
# MAIN
# ─────────────────────────────────────────────────
def main():
    entities = extract_entity_fields()
    vue_refs = extract_frontend_fields()
    controllers = extract_controllers()
    apis = extract_api_calls()
    
    # Build reverse index: field_name → list of (entity, vue_file)
    field_usages = {}  # field_name → [(entity, vue_file, position)]
    
    for ename, efields in entities.items():
        for fname in efields:
            if fname not in field_usages:
                field_usages[fname] = []
            field_usages[fname].append(('entity', ename, fname))
    
    for vpath, vinfo in vue_refs.items():
        for fname in vinfo['all']:
            if fname not in field_usages:
                field_usages[fname] = []
            field_usages[fname].append(('vue', vpath, fname))
    
    # Generate report
    report = []
    report.append("# 字段完全性契约 (FIELDS CONTRACT)\n")
    report.append(f"> 生成时间: 2026-06-24\n")
    report.append(f"> 实体数: {len(entities)} | Vue 视图数: {len(vue_refs)} | Controller 数: {len(controllers)} | API 文件数: {len(apis)}\n")
    
    report.append("---\n")
    report.append("## 总览\n")
    
    # Count orphans
    orphans = []
    matches = []
    
    for fname, usages in sorted(field_usages.items()):
        entity_refs = [u for u in usages if u[0] == 'entity']
        vue_refs_list = [u for u in usages if u[0] == 'vue']
        
        if entity_refs and not vue_refs_list:
            continue  # Backend-only field → not a problem
        if vue_refs_list and not entity_refs:
            orphans.append((fname, vue_refs_list))
        elif vue_refs_list and entity_refs:
            matches.append((fname, entity_refs, vue_refs_list))
    
    report.append(f"- ✅ 前后端匹配: {len(matches)} 字段\n")
    report.append(f"- ⚠️ 前端孤儿 (有前端引用无后端实体): {len(orphans)} 字段\n")
    report.append(f"- 后端实体字段总数: {sum(len(f) for f in entities.values())}\n")
    report.append(f"- 前端引用字段总数: {sum(len(v['all']) for v in vue_refs.values())}\n\n")
    
    # Orphans section
    report.append("## ⚠️ 前端孤儿字段\n\n")
    report.append("这些字段在前端表单/表格中出现，但**在任意实体类中都不存在**。可能是：\n")
    report.append("1. 拼写错误 (实体字段名写错了)\n")
    report.append("2. 已废弃字段 (曾有过但被删了)\n")
    report.append("3. 计算字段 (前端自行维护)\n\n")
    
    # Filter out genuinely suspicious ones
    suspicious = []
    for fname, refs in orphans:
        # Skip common non-entity fields
        common = {'page', 'size', 'total', 'totalElements', 'totalPages', 'items',
                   'loading', 'dialogVisible', 'searchForm', 'formData',
                   'sortOrder', 'createdAt', 'updatedAt', 'deletedAt',
                   'createdAtRange', 'expanded', 'options', 'visible',
                   'upload', 'fileList', 'file', 'imageUrl',
                   'currentPage', 'pageSize', 'selection',
                   'label', 'value', 'key', 'code', 'name', 'title',
                   'teacherName', 'userName', 'realName', 'categoryName',
                   'majorName', 'departmentName', 'courseName', 'className',
                   'statusText', 'statusType', 'statusLabel',
                   'typeText', 'typeLabel',
                   # JOIN display names — not entity fields, not orphans
                   'applicantName', 'authorName', 'collegeName', 'chapterName',
                   'microSpecialtyTitle', 'specialtyCollege', 'studentName',
                   'teacherCollege', 'teacherName', 'userName',
                   # Frontend computed / UI fields
                   'replyCount', 'usageCount', 'analysis', 'module',
                   'row', 'contentHtml', 'inviteStatus', 'classIds', 'chapterIds',
                   # System setting fields (AdminSetting entity)
                   'platformName', 'serverUrl', 'smtpHost',
                   # Frontend temp variables (mapped to correct entity field before submit)
                   'partialScoreRule',
                   # JOIN display / computed fields (not entity fields)
                   'completionRate', 'confirmPassword', 'courseTitle',
                   'creatorName', 'enrollmentCount', 'featuredAt', 'keyword',
                   'link'}
        common_lower = {x.lower() for x in common}
        if fname.lower() not in common_lower:
            suspicious.append((fname, refs))
    
    report.append(f"### 可疑前端孤儿 ({len(suspicious)} 个)\n\n")
    report.append("| # | 字段名 | 出现位置 | 建议行动 |\n")
    report.append("|---|--------|---------|---------|\n")
    for i, (fname, refs) in enumerate(sorted(suspicious), 1):
        positions = ', '.join(f'{r[1]}:{r[2]}' for r in refs[:3])
        report.append(f"| {i} | `{fname}` | {positions} | ❓ 需人工判断 |\n")
    
    if not suspicious:
        report.append("(无)\n")
    
    report.append("\n")
    
    # FK field management status
    report.append("## 🔗 关联字段 (FK) 管理入口状态\n\n")
    report.append("检查每个外键字段对应的实体是否有完整的管理 CRUD。\n\n")
    report.append("| # | 实体 | FK 字段 | 关联实体 | 管理状态 | 证据 | 建议 |\n")
    report.append("|---|------|---------|---------|---------|------|------|\n")
    
    fk_issues = []
    for ename, efields in sorted(entities.items()):
        for fname, finfo in sorted(efields.items()):
            if finfo['is_fk'] and fname != 'id':
                status, evidence = check_fk_mgmt(fname, ename, controllers, vue_refs)
                clr = {'MANAGED': '✅', 'SELF_REF': '⏭️', 'NO_VUE': '⚠️', 'NO_API': '⚠️', 'UNMANAGED': '❌'}
                if status != 'MANAGED' and status != 'SELF_REF':
                    fk_issues.append((status, ename, fname, evidence))
                    report.append(f"| {len(fk_issues)} | {ename} | `{fname}` ({finfo['comment']}) | ? | {clr.get(status, '?')} {status} | {evidence[:60]} | **需修复** |\n")
    
    if not fk_issues:
        report.append("| - | (全部关联字段均有管理入口) | | | ✅ | | |\n")
    
    report.append("\n")
    
    # FK orphan summary
    unmanaged_fks = [x for x in fk_issues if x[0] == 'UNMANAGED']
    no_vue_fks = [x for x in fk_issues if x[0] == 'NO_VUE']
    no_api_fks = [x for x in fk_issues if x[0] == 'NO_API']
    
    report.append(f"### FK 管理缺口汇总\n\n")
    report.append(f"- ❌ 完全无管理: {len(unmanaged_fks)} 个\n")
    report.append(f"- ⚠️ 有 API 无前端: {len(no_vue_fks)} 个\n")
    report.append(f"- ⚠️ 有前端无 API: {len(no_api_fks)} 个\n")
    report.append(f"- ✅ 有完整管理: 其余\n\n")
    
    # Top offenders
    report.append("## 🚨 优先修复排行榜 (按业务影响)\n\n")
    
    # Hardcode known issues from engineer audit
    report.append("| 排名 | 字段 | 实体 | 问题 | 修复方案 | 成本 |\n")
    report.append("|------|------|------|------|---------|------|\n")
    report.append("| 1 | `counselorId` | Classes | ⚠️ 用 TEACHER 替代 | 加 COUNSELOR 角色或删字段 | 1-2 天 |\n")
    report.append("| 2 | `teacherId` | Course | ⚠️ 手输数字 | 改 el-select + 屏蔽输入 | 30 分钟 |\n")
    report.append("| 3 | `collegeId` | MicroSpecialtyList | ❌ 字段名错 | 统一为 offerDepartmentId | 30 分钟 |\n")
    report.append("| 4 | `offerDepartmentId` | MicroSpecialty | 命名不一致 | 统一为 departmentId | 1 小时 |\n")
    report.append("| 5 | `teacherNo` | User | ⚠️ 只显不编 | 加编辑弹窗 input | 2 小时 |\n")
    report.append("| 6+ | (详见 FK 表) | | 管理入口缺失 | 补 CRUD 或删字段 | 不定 |\n\n")
    
    # Controller summary
    report.append("## 📋 Controller 清单 (管理入口基线)\n\n")
    report.append("| Controller | 基础路径 | 方法数 | 覆盖实体 |\n")
    report.append("|-----------|---------|--------|--------|\n")
    for cname, cinfo in sorted(controllers.items()):
        report.append(f"| {cname} | {cinfo['base']} | {len(cinfo['methods'])} | - |\n")
    
    report.append("\n")
    
    # Entity field count
    report.append("## 📊 实体字段统计\n\n")
    report.append("| 实体 | 字段数 | FK 字段 |\n")
    report.append("|------|--------|--------|\n")
    for ename, efields in sorted(entities.items()):
        fk_count = sum(1 for f in efields.values() if f['is_fk'] and f['comment'])
        report.append(f"| {ename} | {len(efields)} | {fk_count} |\n")
    
    report.append("\n---\n")
    report.append("> 此契约为准入门禁基线。任何前端新字段必须匹配后端实体，否则 BLOCK。\n")
    
    # Write report
    output_path = PROJECT_ROOT / "docs/开发规划/FIELDS_CONTRACT.md"
    os.makedirs(output_path.parent, exist_ok=True)
    output_path.write_text('\n'.join(report))
    
    print(f"✅ FIELDS_CONTRACT.md 已生成 → {output_path}")
    print(f"   实体: {len(entities)} | 前端引用: {sum(len(v['all']) for v in vue_refs.values())}")
    print(f"   可疑孤儿: {len(suspicious)} | FK 管理缺口: {len(unmanaged_fks)+len(no_vue_fks)+len(no_api_fks)}")

if __name__ == '__main__':
    main()
