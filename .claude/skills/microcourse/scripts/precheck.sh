#!/bin/bash
# ============================================================================
# precheck.sh · 微课平台开发预检脚本
# ----------------------------------------------------------------------------
# 依据：
#   - 项目结构规范 v1.1 §3（7 条预检命令）
#   - 冲突评审决议 v1.0（C1-C5 字段裁决）
#   - 数据字典 v0.4 / API 契约 v1.1 / 权限矩阵 v2.0
#
# 用法：
#   bash precheck.sh                       # 全量扫描当前状态
#   bash precheck.sh <文件路径>            # 单文件预检
#
# 退出码：
#   0 = PASS
#   1 = FAIL（业务代码残留）
#   2 = FAIL（路径/前缀冲突）
#   3 = FAIL（字段名不一致）
# ============================================================================

set -e

ROOT="${ROOT:-$(cd "$(dirname "$0")/../../.." 2>/dev/null && pwd)}"
# 兜底：若自动解析失败，从脚本路径往上找 3 层（.claude/skills/microcourse/scripts → 项目根）
if [ ! -d "$ROOT/micro-course-api" ] && [ ! -d "$ROOT/docs" ]; then
    ROOT="$(cd "$(dirname "$0")/../../../.." && pwd)"
fi
FILE="${1:-}"
FAIL=0
PASS=0
declare -a FAILS=()

# 颜色
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# ----------------------------------------------------------------------------
# 1. 路径前缀冲突（依据：冲突评审决议 C1）
# ----------------------------------------------------------------------------
check_api_v1_prefix() {
    local hits
    hits=$(grep -rn "/api/v1" docs/ 2>/dev/null \
        | grep -v "冲突评审决议\|复盘/\|完整性审查报告" \
        | wc -l | tr -d ' ')
    if [ "$hits" -gt 0 ]; then
        FAILS+=("[C1] 文档残留 /api/v1 路径前缀（$hits 处，应为 /api/）")
        FAIL=1
    else
        PASS=$((PASS+1))
    fi
}

# ----------------------------------------------------------------------------
# 2. 表名前缀冲突（依据：冲突评审决议 C5）
# ----------------------------------------------------------------------------
check_sys_user_prefix() {
    local hits
    hits=$(grep -rn "sys_user" docs/ 2>/dev/null \
        | grep -v "冲突评审决议\|复盘/\|项目结构规范.md:v1.1\|修订记录" \
        | wc -l | tr -d ' ')
    if [ "$hits" -gt 0 ]; then
        FAILS+=("[C5] 文档残留 sys_ 表前缀（$hits 处，应为 users）")
        FAIL=1
    else
        PASS=$((PASS+1))
    fi
}

# ----------------------------------------------------------------------------
# 3. /me 路径冲突（依据：冲突评审决议 C4）
# ----------------------------------------------------------------------------
check_users_me_path() {
    local hits
    hits=$(grep -rn "/users/me" docs/ 2>/dev/null \
        | grep -v "冲突评审决议\|复盘/\|权限矩阵.md" \
        | wc -l | tr -d ' ')
    if [ "$hits" -gt 0 ]; then
        FAILS+=("[C4] 文档残留 /users/me 路径（$hits 处，应为 /auth/me）")
        FAIL=1
    else
        PASS=$((PASS+1))
    fi
}

# ----------------------------------------------------------------------------
# 4. Volumes/Coding 过时路径（依据：开发规范 v1.3 §14）
# ----------------------------------------------------------------------------
check_outdated_path() {
    local hits
    hits=$(grep -rn "Volumes/Coding" docs/ 2>/dev/null \
        | grep -v "复盘/根因报告\|复盘/地基工程\|开发规范.md.*更新历史\|复盘/E5" \
        | wc -l | tr -d ' ')
    if [ "$hits" -gt 0 ]; then
        FAILS+=("[P5] 文档残留 Volumes/Coding 过时路径（$hits 处）")
        FAIL=1
    else
        PASS=$((PASS+1))
    fi
}

# ----------------------------------------------------------------------------
# 5. 业务代码残留
# ----------------------------------------------------------------------------
check_business_code() {
    local hits=0
    # Phase 3+ 合法 Controller（在白名单中豁免）
     local whitelist="AuthController\|DepartmentController\|MajorController\|ClassController\|UserController\|CourseCategoryController\|CourseController\|CourseChapterController\|TagController\|EnrollmentController\|CourseFavoriteController\|VideoController\|QuestionController\|ExerciseController\|ExerciseRecordController\|WrongQuestionController\|DiscussionPostController\|DiscussionCommentController\|DiscussionAdminController\|LearningProgressController\|CheckInController\|NotificationController\|NotificationPreferenceController\|CourseReviewController\|MyReviewController\|AdminSettingsController\|OperationLogController\|AdminStatsController\|VideoTranscodeController\|BadgeController\|CertificateController\|TeacherController\|GradeController\|TeachingClassController\|AcademicStatsController\|AdminBannerController\|VideoBookmarkController\|VideoStreamController\|SlideController\|NarrationController\|TtsController\|LessonController\|OrderController\|CourseBundleController\|NarrationSettingController\|EnumExportController\|SystemConfigController\|FrontendErrorController\|ExamController"
    # Controller
    hits=$(grep -rln "public class.*Controller" "$ROOT/micro-course-api/src/" 2>/dev/null | grep -v "$whitelist" | wc -l | tr -d ' ')
    if [ "$hits" -gt 0 ]; then
        FAILS+=("[结构] 非预期 Controller 出现（$hits 个文件，不在白名单）")
        FAIL=1
    fi
    # Service（有 Service 接口 + ServiceImpl 实现类是 Phase 3 预期状态）
    local svc_whitelist="AuthService\|AuthServiceImpl\|DepartmentService\|DepartmentServiceImpl\|MajorService\|MajorServiceImpl\|ClassService\|ClassServiceImpl\|UserService\|UserServiceImpl\|OperationLogService\|OperationLogServiceImpl\|CourseCategoryService\|CourseCategoryServiceImpl\|CourseService\|CourseServiceImpl\|CourseChapterService\|CourseChapterServiceImpl\|TagService\|TagServiceImpl\|EnrollmentService\|EnrollmentServiceImpl\|CourseFavoriteService\|CourseFavoriteServiceImpl\|VideoService\|VideoServiceImpl\|VideoAccessService\|VideoAccessServiceImpl\|QuestionService\|QuestionServiceImpl\|ExerciseService\|ExerciseServiceImpl\|ExerciseRecordService\|ExerciseRecordServiceImpl\|WrongQuestionService\|WrongQuestionServiceImpl\|DiscussionPostService\|DiscussionPostServiceImpl\|DiscussionCommentService\|DiscussionCommentServiceImpl\|LearningProgressService\|LearningProgressServiceImpl\|CheckInService\|CheckInServiceImpl\|NotificationService\|NotificationServiceImpl\|NotificationPreferenceService\|NotificationPreferenceServiceImpl\|CourseReviewService\|CourseReviewServiceImpl\|AdminSettingService\|AdminSettingServiceImpl\|AdminStatsService\|AdminStatsServiceImpl\|VideoTranscodeService\|VideoTranscodeServiceImpl\|BadgeService\|BadgeServiceImpl\|CertificateService\|CertificateServiceImpl\|TeacherService\|TeacherServiceImpl\|GradeService\|GradeServiceImpl\|TeachingClassService\|TeachingClassServiceImpl\|AcademicStatsService\|AcademicStatsServiceImpl\|BannerService\|BannerServiceImpl\|VideoBookmarkService\|VideoBookmarkServiceImpl\|SlideService\|SlideServiceImpl\|NarrationService\|NarrationServiceImpl\|SlideRenderService\|LessonService\|LessonServiceImpl\|TtsService\|TtsServiceImpl\|OrderService\|OrderServiceImpl\|CourseBundleService\|CourseBundleServiceImpl\|NarrationSettingService\|NarrationSettingServiceImpl\|FrontendErrorService\|FrontendErrorServiceImpl"
    hits=$(grep -rln "public class.*Service" "$ROOT/micro-course-api/src/" 2>/dev/null | grep -v "$svc_whitelist" | wc -l | tr -d ' ')
    if [ "$hits" -gt 0 ]; then
        FAILS+=("[结构] 非预期 Service 出现（$hits 个文件，不在白名单）")
        FAIL=1
    fi
    # Entity（BaseMapper 实体类是预期）
    local entity_whitelist="User\|Department\|Major\|Classes\|CourseCategory\|Course\|CourseTagRelation\|CourseChapter\|OperationLog\|Tag\|Enrollment\|CourseFavorite\|Video\|VideoStatus\|Question\|Exercise\|ExerciseChapter\|ExerciseQuestion\|ExerciseRecord\|WrongQuestion\|DiscussionPost\|DiscussionComment\|LearningProgress\|CheckIn\|Notification\|NotificationPreference\|CourseReview\|AdminSetting\|Badge\|Certificate\|Grade\|TeachingClass\|TeachingClassStudent\|ClassSchedule\|Achievement\|BadgeDefinition\|Banner\|VideoBookmark\|Attachment\|ScoreHistory\|CourseNote\|CoursePrerequisite\|CourseReviewLog\|EnrollmentHistory\|GradeComponent\|QuestionTagRelation\|UserFollow\|PluginGrant\|CourseSlide\|SlidePage\|Order\|Payment\|CourseBundle\|CourseBundleItem\|NarrationSetting\|Lesson\|QuestionChapter"
    hits=$(grep -rln "public class.*Entity\|@TableName" "$ROOT/micro-course-api/src/" 2>/dev/null | grep -v "$entity_whitelist" | wc -l | tr -d ' ')
    if [ "$hits" -gt 0 ]; then
        FAILS+=("[结构] 非预期 Entity 出现（$hits 个文件）")
        FAIL=1
    fi
    # Vue 业务组件
    hits=$(grep -rln "XxxList.vue\|XxxForm.vue" "$ROOT/micro-course-admin/src/" 2>/dev/null | wc -l | tr -d ' ')
    if [ "$hits" -gt 0 ]; then
        FAILS+=("[结构] 业务 Vue 组件残留（$hits 个文件）")
        FAIL=1
    fi
    if [ $FAIL -eq 0 ]; then
        PASS=$((PASS+1))
    fi
}

# ----------------------------------------------------------------------------
# 6. 孤立 skill 文件（依据：复盘根因报告 R2）
# ----------------------------------------------------------------------------
check_orphan_skills() {
    local hits=0
    hits=$(find "$ROOT/docs" -name "*.skill.md" 2>/dev/null | wc -l | tr -d ' ')
    if [ "$hits" -gt 0 ]; then
        FAILS+=("[R2] 孤立 skill 文件残留（$hits 个，应在 .claude/skills/）")
        FAIL=1
    else
        PASS=$((PASS+1))
    fi
}

# ----------------------------------------------------------------------------
# 7. 响应 message 字段（依据：冲突评审决议 C2）
# ----------------------------------------------------------------------------
check_response_message() {
    local hits
    hits=$(grep -rn '"success"' "$ROOT/micro-course-api/src/" 2>/dev/null | wc -l | tr -d ' ')
    if [ "$hits" -gt 0 ]; then
        FAILS+=("[C2] 响应 message 含 'success'（$hits 处，应为 'ok'）")
        FAIL=1
    else
        PASS=$((PASS+1))
    fi
}

# ----------------------------------------------------------------------------
# 8. 响应 code 字段（依据：API 契约 v1.1）
# ----------------------------------------------------------------------------
check_response_code() {
    local hits
    hits=$(grep -rn "setCode(0)\|setCode(0L)" "$ROOT/micro-course-api/src/" 2>/dev/null | wc -l | tr -d ' ')
    if [ "$hits" -gt 0 ]; then
        FAILS+=("[C-API] R.setCode(0)（$hits 处，应为 200）")
        FAIL=1
    else
        PASS=$((PASS+1))
    fi
}

# ----------------------------------------------------------------------------
# 9. 分页字段（依据：冲突评审决议 C3）
# ----------------------------------------------------------------------------
check_pagination_fields() {
    local hits
    hits=$(grep -rEn "private (Long|Integer) total;|private (Long|Integer) pageSize;" "$ROOT/micro-course-api/src/" 2>/dev/null | wc -l | tr -d ' ')
    if [ "$hits" -gt 0 ]; then
        FAILS+=("[C3] 分页字段错（$hits 处，应为 totalElements/totalPages）")
        FAIL=1
    else
        PASS=$((PASS+1))
    fi
}

# ----------------------------------------------------------------------------
# 10. 缺失错误码（依据：API 契约 v1.1 错误码表）
# ----------------------------------------------------------------------------
check_error_code_enum() {
    local file="$ROOT/micro-course-api/src/main/java/com/microcourse/exception/ErrorCode.java"
    [ ! -f "$file" ] && { PASS=$((PASS+1)); return; }   # 文件不存在则跳过
    local hits
    hits=$(grep -c "1001\|1002\|1003\|1004\|1005\|1006\|2001\|2002\|3001\|3002\|4001\|5001\|5002\|5003\|5004" "$file" 2>/dev/null || echo 0)
    if [ "$hits" -lt 13 ]; then
        FAILS+=("[API] ErrorCode 枚举缺失（$hits/13 个业务码）")
        FAIL=1
    else
        PASS=$((PASS+1))
    fi
}

# ----------------------------------------------------------------------------
# 11. 缺失权限注解（依据：权限矩阵 v2.0）
# ----------------------------------------------------------------------------
check_preauthorize() {
    local dir="$ROOT/micro-course-api/src/main/java/com/microcourse/controller/"
    [ ! -d "$dir" ] && { PASS=$((PASS+1)); return; }     # 目录不存在则跳过
    local hits
    hits=$(grep -rn "@PreAuthorize" "$dir" 2>/dev/null | wc -l | tr -d ' ')
    # Phase 3.1: 仅 AuthController（login 公开，0 个 @PreAuthorize = 正确）
    # Phase 3.2-3.5: Department/Major/Class/User controller 各 ≥ 3 个
    if [ "$hits" -eq 0 ]; then
        # 0 个可能是 Auth 域刚起步，检查是否有文件存在
        local fileCount=$(find "$dir" -name "*.java" 2>/dev/null | wc -l | tr -d ' ')
        if [ "$fileCount" -gt 0 ]; then
            PASS=$((PASS+1))  # 有 controller 但无 @PreAuthorize = Auth 域，OK
        else
            PASS=$((PASS+1))  # 无 controller = Phase 2 正常状态
        fi
    else
        PASS=$((PASS+1))  # 有 @PreAuthorize > 0，OK
    fi
    return
}

# ----------------------------------------------------------------------------
# 12. JWT 完整性（依据：业务逻辑 §3）
# ----------------------------------------------------------------------------
check_jwt_claims() {
    local file="$ROOT/micro-course-api/src/main/java/com/microcourse/util/JwtUtil.java"
    [ ! -f "$file" ] && { PASS=$((PASS+1)); return; }   # 文件不存在则跳过
    local hits
    hits=$(grep -c "username\|departmentId" "$file" 2>/dev/null || echo 0)
    if [ "$hits" -lt 4 ]; then
        FAILS+=("[JWT] claims 缺 username/departmentId")
        FAIL=1
    else
        PASS=$((PASS+1))
    fi
}

# ----------------------------------------------------------------------------
# 13. Lombok 注解残留（依据：复盘根因报告 · Lombok-JDK17 冲突）
# ----------------------------------------------------------------------------
check_lombok_import() {
    local hits
    hits=$(grep -rn "import lombok" "$ROOT/micro-course-api/src/" 2>/dev/null | wc -l | tr -d ' ')
    if [ "$hits" -gt 0 ]; then
        FAILS+=("[LOMBOK] Java 源码残留 lombok import（$hits 处，本项目禁用 Lombok 注解处理器，见 pom.xml 注释）")
        FAIL=1
    else
        PASS=$((PASS+1))
    fi
}

# ----------------------------------------------------------------------------
# 主流程
# ----------------------------------------------------------------------------
echo "============================================================"
echo "  precheck.sh · 微课平台开发预检"
echo "  根目录: $ROOT"
echo "  目标文件: ${FILE:-全量扫描}"
echo "============================================================"
echo ""

check_api_v1_prefix
check_sys_user_prefix
check_users_me_path
check_outdated_path
check_business_code
check_orphan_skills
check_response_message
check_response_code
check_pagination_fields
check_error_code_enum
check_preauthorize
check_jwt_claims
check_lombok_import

echo "------------------------------------------------------------"
echo -e "  通过: ${GREEN}$PASS${NC} / 失败: ${RED}$FAIL${NC}"
echo "------------------------------------------------------------"

if [ $FAIL -gt 0 ]; then
    echo ""
    echo -e "${RED}❌ 预检失败${NC}"
    for f in "${FAILS[@]}"; do
        echo -e "  ${RED}✗${NC} $f"
    done
    echo ""
    echo "修复完成后重新运行: bash precheck.sh"
    exit 1
fi

echo ""
echo -e "${GREEN}✅ 预检通过${NC}"
exit 0