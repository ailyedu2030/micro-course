#!/usr/bin/env python3
"""
phase14-audit-fix · 工单任务书渲染器

用法:
  python3 render-task.py auditor M1-03
  python3 render-task.py investigator M1-03
  python3 render-task.py fixer M1-03

输出: JSON 任务书（按 spec §3 schema）
"""
import json
import sys
import os
from pathlib import Path

PROJECT_ROOT = Path("/Users/jackie/微课平台")
SPEC_DOC = PROJECT_ROOT / "docs/开发规划/phase14-audit-fix-spec.md"
PROGRESS_FILE = PROJECT_ROOT / ".audit-cache/phase14/progress.json"

# 72 个工单的元数据（ID + 后端文件 + 前端文件 + API 端点 + 验收重点）
TICKETS = {
    # === M1 学生端 9 ===
    "M1-01": {
        "title": "学生端'微专业' Tab 入口",
        "module": "M1",
        "backend_files": [],
        "frontend_files": [
            "micro-course-admin/src/router/index.js",
            "micro-course-admin/src/components/Layout.vue",
            "micro-course-admin/src/components/StudentLayout.vue",
            "micro-course-admin/src/config/menuConfig.js",
            "micro-course-admin/src/views/student/MyMicroSpecialties.vue"
        ],
        "api_endpoint": "—",
        "acceptance": [
            "路由 /student/my-micro-specialties 存在",
            "meta.roles 包含 'STUDENT'",
            "meta.menuTab=true menuLabel='微专业' menuIcon='Medal' menuOrder 数值合理",
            "StudentLayout 中 Tab 排序正确（广场1→我的课程2→消息3→我的4→微专业5→订单6）",
            "STUDENT 角色登录后可访问",
            "非 STUDENT 角色访问 → 重定向到对应首页",
            "未登录访问 → /login?redirect=..."
        ]
    },
    "M1-02": {
        "title": "学生'我的微专业'列表页",
        "module": "M1",
        "backend_files": [
            "micro-course-api/src/main/java/com/microcourse/controller/MicroSpecialtyEnrollmentController.java",
            "micro-course-api/src/main/java/com/microcourse/service/impl/MicroSpecialtyEnrollmentServiceImpl.java",
            "micro-course-api/src/main/java/com/microcourse/dto/microSpecialty/MicroSpecialtyEnrollmentVO.java"
        ],
        "frontend_files": [
            "micro-course-admin/src/views/student/MyMicroSpecialties.vue",
            "micro-course-admin/src/api/microSpecialty.js"
        ],
        "api_endpoint": "GET /api/micro-specialty-enrollments/my",
        "acceptance": [
            "GET /my 端点是已认证可访问",
            "返回当前学生用户的所有修读记录",
            "列表项含 id, microSpecialtyId, microSpecialtyTitle, status, progress, appliedAt, departmentName",
            "5+ 状态视觉区分（warning/primary/success/danger/info）",
            "前端有 loading/error/empty 三态",
            "点击跳转到 /student/micro-specialties/{id}",
            "操作按钮（重新申请/退课/查看证书）按状态分支正确显示"
        ]
    },
    "M1-03": {
        "title": "学生微专业详情页（公开）",
        "module": "M1",
        "backend_files": [
            "micro-course-api/src/main/java/com/microcourse/controller/MicroSpecialtyController.java",
            "micro-course-api/src/main/java/com/microcourse/service/impl/MicroSpecialtyServiceImpl.java",
            "micro-course-api/src/main/java/com/microcourse/dto/microSpecialty/MicroSpecialtyDetailVO.java"
        ],
        "frontend_files": [
            "micro-course-admin/src/views/student/MicroSpecialtyDetail.vue"
        ],
        "api_endpoint": "GET /api/micro-specialties/{id}",
        "acceptance": [
            "GET /{id} 是 permitAll 公开端点",
            "DRAFT/CANCELLED 状态不可公开访问",
            "详情含基本信息/课程编排/教师团队/学分/申请按钮",
            "前端 MyMicroSpecialties 详情页 921 行（MicroSpecialtyDetail.vue）功能完整",
            "未登录用户可查看公开信息但申请按钮需登录",
            "已申请学生看到进度+状态（不在 MyMicroSpecialties 跳转逻辑错位）"
        ]
    },
    "M1-04": {
        "title": "学生申请修读",
        "module": "M1",
        "backend_files": [
            "micro-course-api/src/main/java/com/microcourse/controller/MicroSpecialtyEnrollmentController.java",
            "micro-course-api/src/main/java/com/microcourse/service/impl/MicroSpecialtyEnrollmentServiceImpl.java"
        ],
        "frontend_files": [
            "micro-course-admin/src/views/student/MicroSpecialtyDetail.vue"
        ],
        "api_endpoint": "POST /api/micro-specialty-enrollments/apply",
        "acceptance": [
            "POST /apply 端点 isAuthenticated()",
            "重复申请（PENDING/APPROVED/IN_PROGRESS）→ 400 + 友好错误",
            "已 CANCELLED 微专业申请 → 403 + 友好错误",
            "成功后通知 LEAD",
            "前端按钮 loading 态 + 成功 toast + 跳转到我的微专业",
            "Service 层事务正确（@Transactional + rollbackFor）",
            "并发幂等（防重复创建）"
        ]
    },
    "M1-05": {
        "title": "学生重修重审（reapply）",
        "module": "M1",
        "backend_files": [
            "micro-course-api/src/main/java/com/microcourse/controller/MicroSpecialtyEnrollmentController.java",
            "micro-course-api/src/main/java/com/microcourse/service/impl/MicroSpecialtyEnrollmentServiceImpl.java"
        ],
        "frontend_files": [
            "micro-course-admin/src/views/student/MyMicroSpecialties.vue"
        ],
        "api_endpoint": "POST /api/micro-specialty-enrollments/{id}/reapply",
        "acceptance": [
            "POST /{id}/reapply 端点 isAuthenticated()",
            "REJECTED/DROPPED/FAILED 状态可重提 → PENDING",
            "本人校验：enrollment.userId == currentUserId",
            "前端 MyMicroSpecialties 重修按钮按状态正确显示",
            "成功后通知 LEAD",
            "PENDING/APPROVED/IN_PROGRESS/COMPLETED 状态不可 reapply（按钮置灰或隐藏）"
        ]
    },
    "M1-06": {
        "title": "学生退课",
        "module": "M1",
        "backend_files": [
            "micro-course-api/src/main/java/com/microcourse/controller/MicroSpecialtyEnrollmentController.java",
            "micro-course-api/src/main/java/com/microcourse/service/impl/MicroSpecialtyEnrollmentServiceImpl.java"
        ],
        "frontend_files": [
            "micro-course-admin/src/views/student/MyMicroSpecialties.vue"
        ],
        "api_endpoint": "POST /api/micro-specialty-enrollments/{id}/drop",
        "acceptance": [
            "POST /{id}/drop 端点 isAuthenticated()",
            "IN_PROGRESS/APPROVED 状态可退课",
            "本人校验 + 二次确认弹窗",
            "CANCELLED/COMPLETED/CERTIFIED 状态不可退课",
            "退课后 enrollment.status = DROPPED + droppedAt + dropReason",
            "退课通知 LEAD/ACADEMIC"
        ]
    },
    "M1-07": {
        "title": "学生查看证书",
        "module": "M1",
        "backend_files": [
            "micro-course-api/src/main/java/com/microcourse/controller/CertificateController.java",
            "micro-course-api/src/main/java/com/microcourse/service/CertificateService.java"
        ],
        "frontend_files": [
            "micro-course-admin/src/views/student/MyMicroSpecialties.vue"
        ],
        "api_endpoint": "GET /api/certificates?microSpecialtyId={id}",
        "acceptance": [
            "CERTIFIED 状态显示证书下载按钮",
            "非 CERTIFIED 状态按钮置灰或隐藏",
            "下载 URL 鉴权（仅本人可下载）",
            "PDF 流式下载 + 进度提示"
        ]
    },
    "M1-08": {
        "title": "课程广场微专业专区（二级 Hero）",
        "module": "M1",
        "backend_files": [
            "micro-course-api/src/main/java/com/microcourse/controller/MicroSpecialtyController.java",
            "micro-course-api/src/main/java/com/microcourse/service/impl/MicroSpecialtyServiceImpl.java",
            "micro-course-api/src/main/java/com/microcourse/dto/microSpecialty/MicroSpecialtySquareVO.java"
        ],
        "frontend_files": [
            "micro-course-admin/src/views/student/CourseSquare.vue",
            "micro-course-admin/src/api/microSpecialty.js"
        ],
        "api_endpoint": "GET /api/micro-specialties/square",
        "acceptance": [
            "GET /square 是 permitAll 公开端点",
            "返回 goldFeatured/featured/recruiting 三组预聚合",
            "DRAFT/CANCELLED 不出现在三组",
            "前端专区有 Hero 标题 + 卡片布局 + loading/empty/error 三态",
            "卡片点击跳转到 /student/micro-specialties/{id}"
        ]
    },
    "M1-09": {
        "title": "学生端数据链端到端",
        "module": "M1",
        "backend_files": [
            "micro-course-api/src/main/java/com/microcourse/service/impl/MicroSpecialtyEnrollmentServiceImpl.java",
            "micro-course-api/src/main/java/com/microcourse/service/impl/MicroSpecialtyServiceImpl.java"
        ],
        "frontend_files": [
            "micro-course-admin/src/views/student/MyMicroSpecialties.vue",
            "micro-course-admin/src/views/student/MicroSpecialtyDetail.vue",
            "micro-course-admin/src/views/student/CourseSquare.vue"
        ],
        "api_endpoint": "全链路：申请→审批→学习→完成→证书",
        "acceptance": [
            "学生从 CourseSquare 进详情 → 申请 → 审批通过 → IN_PROGRESS → COMPLETED → CERTIFIED 全链路通",
            "每步状态机转移正确（@Transactional）",
            "每步通知正确发送",
            "前端状态显示一致",
            "证书下载链路完整"
        ]
    },
    # === M2 教师端 30 个（简化） ===
    "M2-01": {
        "title": "教师'我的微专业'列表",
        "module": "M2",
        "backend_files": ["micro-course-api/.../MicroSpecialtyController.java"],
        "frontend_files": ["micro-course-admin/src/views/teacher/MicroSpecialtyList.vue"],
        "api_endpoint": "GET /api/micro-specialties?creatorId=me",
        "acceptance": ["列表+状态过滤+创建入口"]
    },
    # ... 其余 29 个工单按 spec §1.3 详表填写（运行时由 spec 生成）
}


def load_acceptance_from_spec(ticket_id: str) -> list:
    """从 spec 中按 ticket_id 加载 acceptance 列表（如果 TICKETS 中没有）"""
    # 简化处理：TICKETS 中已包含详细工单
    return TICKETS.get(ticket_id, {}).get("acceptance", [])


def render_auditor_task(ticket_id: str) -> dict:
    t = TICKETS.get(ticket_id)
    if not t:
        # 兜底：从 progress.json 读取
        progress = json.loads(PROGRESS_FILE.read_text())
        t = progress["tickets"].get(ticket_id, {})

    return {
        "ticket_id": ticket_id,
        "round": 1,
        "module": t.get("module", "?"),
        "title": t.get("title", ticket_id),
        "agent_role": "auditor",
        "context": {
            "spec_section": f"phase14 spec §1.2-1.5 (工单 {ticket_id})",
            "spec_doc": str(SPEC_DOC.relative_to(PROJECT_ROOT)),
            "audit_spec": str((PROJECT_ROOT / "docs/开发规划/phase14-audit-fix-spec.md").relative_to(PROJECT_ROOT)),
            "related_files": t.get("backend_files", []) + t.get("frontend_files", [])
        },
        "allowed_files": [],
        "forbidden_files": [
            "**/test/**", "docs/**", "package.json", "package-lock.json",
            "pom.xml", "**/*.md", "**/entity/**", "**/mapper/**",
            "**/config/**", "**/migration/**"
        ],
        "deliverables": ["JSON 报告（按 spec §4.1 schema）"],
        "acceptance": {
            "must_pass": t.get("acceptance", []),
            "edge_cases": []
        },
        "steps": [
            f"step 1: 读 phase14 spec 中 {ticket_id} 对应章节",
            f"step 2: 读后端相关文件（如有）",
            f"step 3: 读前端相关文件（如有）",
            f"step 4: 列出 P0/P1/P2/P3 问题"
        ],
        "budget": {
            "max_files_to_read": 8,
            "max_tokens_estimate": 30000,
            "max_runtime_minutes": 8
        }
    }


def render_investigator_task(ticket_id: str) -> dict:
    """复现 auditor 报告中的 P0/P1 问题（输入含 auditor 报告）"""
    t = TICKETS.get(ticket_id, {})
    return {
        "ticket_id": ticket_id,
        "round": 1,
        "module": t.get("module", "?"),
        "title": t.get("title", ticket_id),
        "agent_role": "investigator",
        "context": {
            "previous_auditor_report": "{从 reports/{ticket_id}_auditor_r*.json 读取}",
            "spec_section": f"phase14 spec 工单 {ticket_id}"
        },
        "allowed_files": [],
        "forbidden_files": [
            "**/test/**", "docs/**", "package.json", "pom.xml", "**/*.md",
            "**/entity/**", "**/mapper/**", "**/config/**", "**/migration/**"
        ],
        "deliverables": ["JSON 根因报告（按 spec §4.2 schema）"],
        "steps": [
            "step 1: 读 auditor 报告",
            "step 2: 读相关代码确认问题存在",
            "step 3: 列出根因链 level_1~level_4",
            "step 4: 给 fixer 最小修复 diff_preview"
        ],
        "budget": {
            "max_files_to_read": 4,
            "max_tokens_estimate": 15000,
            "max_runtime_minutes": 5
        }
    }


def render_fixer_task(ticket_id: str) -> dict:
    """修复 auditor+investigator 确认的 P0/P1 问题"""
    t = TICKETS.get(ticket_id, {})
    return {
        "ticket_id": ticket_id,
        "round": 1,
        "module": t.get("module", "?"),
        "title": t.get("title", ticket_id),
        "agent_role": "fixer",
        "context": {
            "previous_auditor_report": "{从 reports/ 读取}",
            "previous_investigator_report": "{从 reports/ 读取}"
        },
        "allowed_files": t.get("backend_files", []) + t.get("frontend_files", []),
        "forbidden_files": [
            "**/test/**", "docs/**", "package.json", "package-lock.json",
            "pom.xml", "**/*.md", "**/entity/**", "**/mapper/**",
            "**/config/**", "**/migration/**"
        ],
        "deliverables": [
            "代码 diff（Edit 工具应用）",
            "JSON 报告（按 spec §4.3 schema）",
            "验证证据（grep / mvn compile / 路由顺序）"
        ],
        "steps": [
            "step 1: 读 auditor + investigator 报告",
            "step 2: 读 allowed_files 内相关代码",
            "step 3: 用 Edit 工具实施修复（仅限 allowed_files）",
            "step 4: grep 验证修改生效",
            "step 5: mvn compile（如改后端）",
            "step 6: 输出 JSON 报告"
        ],
        "budget": {
            "max_files_to_read": 4,
            "max_files_to_modify": 3,
            "max_tokens_estimate": 18000,
            "max_runtime_minutes": 10
        }
    }


def main():
    if len(sys.argv) < 3:
        print("用法: render-task.py <role> <ticket_id>", file=sys.stderr)
        print("role ∈ auditor | investigator | fixer", file=sys.stderr)
        sys.exit(1)

    role = sys.argv[1]
    ticket_id = sys.argv[2]

    if role == "auditor":
        task = render_auditor_task(ticket_id)
    elif role == "investigator":
        task = render_investigator_task(ticket_id)
    elif role == "fixer":
        task = render_fixer_task(ticket_id)
    else:
        print(f"未知 role: {role}", file=sys.stderr)
        sys.exit(1)

    print(json.dumps(task, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
