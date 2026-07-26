#!/usr/bin/env python3
"""
Hermes 课程推送脚本 — 支持 htmlLessons[] 字段.

用法:
  # 从 JSON 文件推送 (含可选 htmlLessons)
  python push_course.py --api-key <KEY> --base-url <URL> course.json

  # 手动指定 htmlLessons
  python push_course.py --api-key <KEY> --base-url <URL> course.json \\
      --html-lesson courseId=1 chapterId=1 title="互动课时" \\
          htmlContent=@lesson.html lessonScript="讲述稿全文"

示例文件 course.json:
  {
    "hermesCourseId": "course-001",
    "title": "HTML 互动课程演示",
    "categoryId": 1,
    "chapters": [
      {
        "title": "第一章",
        "sortOrder": 1,
        "lessons": [
          {
            "title": "1.1 概述",
            "type": "VIDEO",
            "durationMinutes": 10,
            "sortOrder": 1
          }
        ]
      }
    ],
    "htmlLessons": [
      {
        "courseId": 0,
        "chapterId": 0,
        "title": "HTML 互动课时",
        "htmlContent": "<!DOCTYPE html><html><body><h1>Hello</h1></body></html>",
        "lessonScript": "欢迎来到互动课堂"
      }
    ]
  }

字段说明:
  htmlLessons[].courseId:   课程 ID (推送后自动填充, 可填 0)
  htmlLessons[].chapterId:  章节 ID (推送后自动填充, 可填 0)
  htmlLessons[].title:      课时标题
  htmlLessons[].htmlContent: HTML 内容 (支持内联或 @file 引用)
  htmlLessons[].lessonScript: 讲述稿内容 (可选)

依赖: Python 3.8+, requests (可选, 否则用 urllib)
"""

import argparse
import json
import os
import sys
import tempfile
import time

try:
    import requests
    HAS_REQUESTS = True
except ImportError:
    HAS_REQUESTS = False
    import urllib.request
    import urllib.error


# ── 常量 ──────────────────────────────────────────────
DEFAULT_BASE_URL = "https://microcourse.ailyedu.cn/api/hermes/webhook"
UPLOAD_BASE_URL = "https://microcourse.ailyedu.cn/api/hermes/webhook"
DEFAULT_TIMEOUT = 30  # 秒


# ── 辅助函数 ──────────────────────────────────────────

def load_json_file(path: str) -> dict:
    """加载 JSON 文件, 支持 // 和 # 注释."""
    import re
    with open(path, "r", encoding="utf-8") as f:
        text = f.read()
    # 去掉单行注释 (不破坏字符串内的内容)
    text = re.sub(r'(?m)^\s*(//|#).*$', '', text)
    return json.loads(text)


def resolve_content(value: str) -> str:
    """如果 value 以 @ 开头, 从文件读取; 否则原样返回."""
    if value.startswith("@"):
        filepath = value[1:]
        with open(filepath, "r", encoding="utf-8") as f:
            return f.read()
    return value


# ── HTTP 调用 (适配 requests / urllib) ────────────────

def http_request(method: str, url: str, headers: dict,
                 json_body: dict = None,
                 data: dict = None,
                 files: dict = None,
                 timeout: int = DEFAULT_TIMEOUT) -> tuple:
    """
    发送 HTTP 请求, 返回 (status_code, response_dict).
    优先用 requests, 否则用 urllib.
    """
    if HAS_REQUESTS:
        return _requests_request(method, url, headers, json_body, data, files, timeout)
    return _urllib_request(method, url, headers, json_body, data, files, timeout)


def _requests_request(method, url, headers, json_body, data, files, timeout):
    sess = requests.Session()
    sess.headers.update(headers)
    try:
        if method == "POST":
            if files:
                resp = sess.post(url, data=data, files=files, timeout=timeout)
            else:
                resp = sess.post(url, json=json_body, timeout=timeout)
        elif method == "GET":
            resp = sess.get(url, timeout=timeout)
        elif method == "DELETE":
            resp = sess.delete(url, timeout=timeout)
        else:
            raise ValueError(f"Unsupported method: {method}")
        try:
            body = resp.json()
        except Exception:
            body = {"raw": resp.text}
        return resp.status_code, body
    except requests.exceptions.RequestException as e:
        return 0, {"error": str(e)}


def _urllib_request(method, url, headers, json_body, data, files, timeout):
    import urllib.request
    if files:
        raise RuntimeError("文件上传需要 requests 库: pip install requests")
    body_bytes = None
    if json_body is not None:
        body_bytes = json.dumps(json_body, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(url, data=body_bytes, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            body = json.loads(resp.read().decode("utf-8"))
            return resp.status, body
    except urllib.error.HTTPError as e:
        try:
            body = json.loads(e.read().decode("utf-8"))
        except Exception:
            body = {"error": str(e)}
        return e.code, body
    except Exception as e:
        return 0, {"error": str(e)}


# ── 核心业务逻辑 ──────────────────────────────────────

def push_course(api_key: str, base_url: str, course_data: dict,
                dry_run: bool = False) -> dict:
    """
    推送课程 (含 htmlLessons) 到 Hermes webhook.

    步骤:
      1. POST /courses → 创建/更新课程
      2. 若有 htmlLessons, 逐个上传 HTML 文件
      3. 若有 lessonScript, 推送讲述稿

    返回: {
      "courseSync": { "courseId": ..., "action": "created|updated" },
      "htmlLessons": [ { "title": ..., "sectionId": ..., "slideId": ..., "status": ... } ],
      "scripts": [ { "title": ..., "status": ... } ]
    }
    """
    result = {
        "courseSync": None,
        "htmlLessons": [],
        "scripts": [],
    }

    headers = {
        "X-API-Key": api_key,
        "Content-Type": "application/json",
    }

    # ── 提取 htmlLessons (推送前从 course_data 分离) ──
    html_lessons = course_data.pop("htmlLessons", None) or []

    # ── 1. 推送课程 ──
    url = f"{base_url}/courses"
    print(f"[Hermes] 推送课程: {course_data.get('title', 'N/A')} ...")

    if dry_run:
        print(f"[Hermes][DRY-RUN] POST {url}")
        print(f"[Hermes][DRY-RUN] Body: {json.dumps(course_data, ensure_ascii=False)[:200]}...")
        course_sync = {"courseId": 0, "action": "dry-run"}
    else:
        status, body = http_request("POST", url, headers, json_body=course_data)
        if status >= 200 and status < 300:
            data = body.get("data", body)
            course_sync = {
                "courseId": data.get("courseId"),
                "action": data.get("action", "unknown"),
            }
            print(f"[Hermes] ✅ 课程推送成功: courseId={course_sync['courseId']}, action={course_sync['action']}")
        else:
            err_msg = body.get("message", body.get("error", str(body)))
            raise RuntimeError(f"课程推送失败 (HTTP {status}): {err_msg}")
    result["courseSync"] = course_sync
    course_id = course_sync["courseId"]

    # ── 2. 如需推送课程 → 再查询 course 详情获取章节结构 ──
    # 获取章节映射: chapterId → [{ sectionId, title }]
    chapter_lessons = _fetch_chapter_lesson_map(api_key, base_url,
                                                 course_data.get("hermesCourseId", ""),
                                                 dry_run=dry_run)

    # ── 3. 处理 htmlLessons ──
    for idx, lesson in enumerate(html_lessons):
        title = lesson.get("title", f"HTML-课时-{idx+1}")
        html_content = resolve_content(lesson.get("htmlContent", ""))
        lesson_script = lesson.get("lessonScript", "")

        print(f"[Hermes] 处理 HTML 课时 [{idx+1}/{len(html_lessons)}]: {title} ...")

        # 3a. 查找匹配的章节 + 课时
        chapter_title = lesson.get("chapterTitle", "")
        lesson_title = title
        chapter_id = lesson.get("chapterId")
        section_id = lesson.get("sectionId")

        if (section_id is None or section_id == 0) and chapter_id is not None:
            # 尝试从 chapter_lessons 中匹配
            for ch_id, sections in chapter_lessons.items():
                if (chapter_id == 0 or ch_id == chapter_id):
                    for sec in sections:
                        if sec.get("title") == lesson_title:
                            section_id = sec.get("sectionId")
                            chapter_id = ch_id
                            break
                    if section_id:
                        break

        if section_id is None or section_id == 0:
            print(f"[Hermes] ⚠️  跳过 HTML 课时 '{title}': 未找到匹配的 sectionId, "
                  f"请确认课程已推送且章节标题匹配")
            result["htmlLessons"].append({
                "title": title,
                "sectionId": None,
                "status": "skipped",
                "reason": "no matching section found",
            })
            continue

        if not html_content:
            print(f"[Hermes] ⚠️  跳过 HTML 课时 '{title}': htmlContent 为空")
            result["htmlLessons"].append({
                "title": title,
                "sectionId": section_id,
                "status": "skipped",
                "reason": "empty htmlContent",
            })
            continue

        # 3b. 将 htmlContent 写入临时文件
        html_file = _write_temp_html(title, html_content)

        try:
            if dry_run:
                print(f"[Hermes][DRY-RUN] POST {base_url}/courses/"
                      f"{course_data.get('hermesCourseId', '')}/lessons/{section_id}/slide")
                slide_result = {"slideId": 0, "status": 0, "message": "dry-run"}
            else:
                # 上传 HTML 文件
                upload_headers = {"X-API-Key": api_key}
                upload_url = f"{base_url}/courses/{course_data.get('hermesCourseId', '')}/lessons/{section_id}/slide"
                if HAS_REQUESTS:
                    with open(html_file, "rb") as f:
                        status, body = http_request(
                            "POST", upload_url, upload_headers,
                            data={},
                            files={"file": (os.path.basename(html_file), f, "text/html")},
                        )
                else:
                    status, body = http_request(
                        "POST", upload_url, upload_headers,
                        data={},
                        files={},
                    )

                if status >= 200 and status < 300:
                    slide_data = body.get("data", body)
                    slide_result = {
                        "slideId": slide_data.get("slideId"),
                        "status": slide_data.get("status"),
                        "message": slide_data.get("message", "上传成功"),
                    }
                    print(f"[Hermes]   ✅ HTML 上传成功: slideId={slide_result['slideId']}, "
                          f"message={slide_result.get('message')}")
                else:
                    err_msg = body.get("message", body.get("error", str(body)))
                    raise RuntimeError(f"HTML 上传失败 (HTTP {status}): {err_msg}")
        finally:
            # 清理临时文件
            try:
                os.unlink(html_file)
            except OSError:
                pass

        result["htmlLessons"].append({
            "title": title,
            "sectionId": section_id,
            "chapterId": chapter_id,
            **slide_result,
        })

        # 3c. 推送讲述稿 (可选)
        if lesson_script:
            if dry_run:
                print(f"[Hermes][DRY-RUN] POST {base_url}/courses/"
                      f"{course_data.get('hermesCourseId', '')}/scripts")
                script_status = {"updated": 0, "totalPages": 0}
            else:
                script_url = f"{base_url}/courses/{course_data.get('hermesCourseId', '')}/scripts"
                script_payload = {
                    "scriptContent": lesson_script,
                    "sectionId": section_id,
                }
                s_status, s_body = http_request("POST", script_url, headers,
                                                 json_body=script_payload)
                if s_status >= 200 and s_status < 300:
                    script_status = s_body.get("data", s_body)
                    print(f"[Hermes]   ✅ 讲述稿推送成功: {script_status}")
                else:
                    print(f"[Hermes]   ⚠️  讲述稿推送失败 (HTTP {s_status}): "
                          f"{s_body.get('message', '')}")

            result["scripts"].append({
                "title": title,
                "sectionId": section_id,
                **script_status,
            })

    return result


def _fetch_chapter_lesson_map(api_key: str, base_url: str,
                               hermes_course_id: str,
                               dry_run: bool = False) -> dict:
    """
    查询课程详情, 返回 { chapterId: [{ sectionId, title }] } 映射.
    """
    if dry_run or not hermes_course_id:
        return {}
    headers = {"X-API-Key": api_key}
    url = f"{base_url}/courses/{hermes_course_id}"
    status, body = http_request("GET", url, headers)
    if status < 200 or status >= 300:
        print(f"[Hermes] ⚠️  查询课程详情失败 (HTTP {status}), 跳过章节匹配")
        return {}

    data = body.get("data", {})
    chapters = data.get("chapters", [])
    result = {}
    for ch in chapters:
        ch_id = ch.get("id")
        lessons = ch.get("lessons", [])
        result[ch_id] = [
            {"sectionId": l.get("id"), "title": l.get("title")}
            for l in lessons
        ]
    print(f"[Hermes]   查询到 {len(result)} 个章节, "
          f"{sum(len(v) for v in result.values())} 个课时")
    return result


def _write_temp_html(title: str, html_content: str) -> str:
    """将 HTML 内容写入临时文件, 返回文件路径."""
    safe_name = "".join(c if c.isalnum() or c in ('-', '_') else '_' for c in title)
    fd, path = tempfile.mkstemp(suffix=".html", prefix=f"hermes_{safe_name}_")
    with os.fdopen(fd, "w", encoding="utf-8") as f:
        f.write(html_content)
    return path


# ── CLI 入口 ──────────────────────────────────────────

def parse_args():
    parser = argparse.ArgumentParser(
        description="Hermes 课程推送脚本 (支持 htmlLessons)",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
示例:
  # 从 JSON 推送
  python push_course.py --api-key "xxx" course.json

  # 从 JSON 推送 + 覆盖 htmlContent 为文件
  python push_course.py --api-key "xxx" course.json \\\\
      --html-file lesson.html

  # 仅验证, 不发送
  python push_course.py --api-key "xxx" --dry-run course.json
        """,
    )
    parser.add_argument("course_json", help="课程 JSON 文件路径")
    parser.add_argument("--api-key", required=True, help="Hermes API Key")
    parser.add_argument("--base-url", default=DEFAULT_BASE_URL,
                        help=f"Hermes Webhook Base URL (默认: {DEFAULT_BASE_URL})")
    parser.add_argument("--dry-run", action="store_true",
                        help="仅验证, 不发送实际请求")
    # 替代方案: 用 --html-lesson 替代 JSON 中的 htmlLessons
    parser.add_argument("--html-lesson", action="append", nargs="+",
                        help="HTML 课时参数 (key=value 对), 例如: "
                             "title=\"互动课时\" htmlContent=@lesson.html")
    return parser.parse_args()


def parse_html_lesson_args(args_list):
    """解析 --html-lesson key=value 参数为字典列表."""
    lessons = []
    for group in args_list:
        lesson = {}
        for kv in group:
            if "=" not in kv:
                continue
            key, _, value = kv.partition("=")
            lesson[key] = value
        if "title" in lesson:
            if "htmlContent" in lesson:
                lesson["htmlContent"] = resolve_content(lesson["htmlContent"])
            lessons.append(lesson)
    return lessons


def main():
    args = parse_args()

    # 加载课程 JSON
    course_data = load_json_file(args.course_json)

    # 如果提供了 --html-lesson 参数, 覆盖 JSON 中的 htmlLessons
    if args.html_lesson:
        course_data["htmlLessons"] = parse_html_lesson_args(args.html_lesson)

    # 确保 hermesCourseId 存在
    if not course_data.get("hermesCourseId"):
        print("[Hermes] ❌ 课程 JSON 必须包含 hermesCourseId 字段")
        sys.exit(1)

    print(f"[Hermes] ════════════════════════════════════════")
    print(f"[Hermes]   API Base: {args.base_url}")
    print(f"[Hermes]   课程:     {course_data.get('title', 'N/A')}")
    html_count = len(course_data.get("htmlLessons", []) or [])
    print(f"[Hermes]   HTML 课时: {html_count}")
    if args.dry_run:
        print(f"[Hermes]   模式:     DRY-RUN (仅验证)")
    print(f"[Hermes] ════════════════════════════════════════")

    try:
        result = push_course(args.api_key, args.base_url, course_data,
                             dry_run=args.dry_run)
    except RuntimeError as e:
        print(f"\n[Hermes] ❌ 失败: {e}")
        sys.exit(1)

    # 输出结果
    print(f"\n[Hermes] ──────────── 推送结果 ────────────")
    print(json.dumps(result, ensure_ascii=False, indent=2))
    print(f"[Hermes] ─────────────────────────────────────")

    # 统计
    total = len(result["htmlLessons"])
    succeeded = sum(1 for h in result["htmlLessons"]
                    if h.get("status") not in ("skipped",) and h.get("slideId"))
    if total > 0:
        print(f"[Hermes] HTML 课时: {succeeded}/{total} 成功")
    else:
        print(f"[Hermes] 无 HTML 课时需要推送")

    if args.dry_run:
        print(f"[Hermes] (DRY-RUN 模式, 未发送实际请求)")
    else:
        print(f"[Hermes] ✅ 完成")


if __name__ == "__main__":
    main()
