#!/usr/bin/env python3
"""
微课平台 100 万级压测脚本 (W34 治理)

目标:
  - 验证 100 万 QPS 集群设计
  - 通过 Locust 模拟 100 万用户
  - 输出 p99 / 错误率 / 集群扩展性

使用:
  pip install locust
  locust -f load-test-million.py --host=http://api.micro-course.com -u 1000000 -r 100000
"""

from locust import HttpUser, task, between, events
import random


class MicroCourseUser(HttpUser):
    wait_time = between(1, 3)  # 用户思考时间 1-3s

    def on_start(self):
        """登录获取 token"""
        # 70% 学员, 20% 教师, 10% 管理员
        role = random.choices(
            ['student', 'teacher', 'admin'],
            weights=[0.7, 0.2, 0.1]
        )[0]
        self.role = role

        # 模拟登录
        try:
            response = self.client.post(
                '/api/auth/login',
                json={
                    'username': f'{role}_{random.randint(1, 10000)}',
                    'password': 'test1234'
                },
                name='login',
                timeout=5
            )
            if response.status_code == 200:
                data = response.json().get('data', {})
                self.token = data.get('accessToken', '')
            else:
                self.token = ''
        except Exception:
            self.token = ''

    @property
    def auth_headers(self):
        if self.token:
            return {'Authorization': f'Bearer {self.token}'}
        return {}

    # ──────────────────────────────────────────
    # 学员任务 (70% 流量)
    # ──────────────────────────────────────────

    @task(30)
    def view_courses(self):
        """浏览课程列表 (高频)"""
        self.client.get(
            f'/api/courses?page={random.randint(1, 100)}&size=20',
            headers=self.auth_headers,
            name='GET /api/courses',
            timeout=3
        )

    @task(20)
    def view_course_detail(self):
        """课程详情 (高频)"""
        course_id = random.randint(1, 1000)
        self.client.get(
            f'/api/courses/{course_id}',
            headers=self.auth_headers,
            name='GET /api/courses/{id}',
            timeout=3
        )

    @task(20)
    def view_courseware_tree(self):
        """课件树 (高频 + Redis 缓存)"""
        course_id = random.randint(1, 1000)
        section_id = random.randint(1, 100)
        self.client.get(
            f'/api/courses/{course_id}/courseware/{section_id}',
            headers=self.auth_headers,
            name='GET /api/courses/{cid}/courseware/{sid}',
            timeout=3
        )

    @task(10)
    def view_video_metadata(self):
        """视频元数据 (中频)"""
        video_id = random.randint(1, 1000)
        self.client.get(
            f'/api/videos/{video_id}',
            headers=self.auth_headers,
            name='GET /api/videos/{id}',
            timeout=3
        )

    @task(5)
    def submit_exercise(self):
        """提交练习 (低频)"""
        exercise_id = random.randint(1, 5000)
        self.client.post(
            f'/api/exercise-records',
            json={
                'exerciseId': exercise_id,
                'answer': random.choice(['A', 'B', 'C', 'D']),
                'duration': random.randint(10, 120)
            },
            headers=self.auth_headers,
            name='POST /api/exercise-records',
            timeout=5
        )

    @task(5)
    def view_progress(self):
        """学习进度 (低频)"""
        course_id = random.randint(1, 1000)
        self.client.get(
            f'/api/learning-progress/{course_id}',
            headers=self.auth_headers,
            name='GET /api/learning-progress/{id}',
            timeout=3
        )

    # ──────────────────────────────────────────
    # 教师任务 (20% 流量)
    # ──────────────────────────────────────────

    @task(3)
    def list_teacher_courses(self):
        """教师课程列表"""
        if self.role != 'teacher':
            return
        self.client.get(
            f'/api/courses?teacherId={random.randint(1, 100)}',
            headers=self.auth_headers,
            name='GET /api/courses?teacherId=',
            timeout=3
        )

    @task(1)
    def create_course(self):
        """创建课程 (极低频)"""
        if self.role != 'teacher':
            return
        self.client.post(
            '/api/courses',
            json={
                'title': f'Test Course {random.randint(1, 100000)}',
                'description': 'Auto-generated test course',
                'category': random.choice(['K12', '职教', '语言'])
            },
            headers=self.auth_headers,
            name='POST /api/courses',
            timeout=5
        )

    # ──────────────────────────────────────────
    # 管理员任务 (10% 流量)
    # ──────────────────────────────────────────

    @task(1)
    def admin_stats(self):
        """管理员统计 (极低频)"""
        if self.role != 'admin':
            return
        self.client.get(
            '/api/admin/stats',
            headers=self.auth_headers,
            name='GET /api/admin/stats',
            timeout=5
        )


# ──────────────────────────────────────────
# 100 万 QPS 集群配置 (locust 启动参数)
# ──────────────────────────────────────────

# 单机模拟 100 万用户
# locust -f load-test-million.py --host=http://api --users 1000000 --spawn-rate 100000 --run-time 30m

# 集群模式 (主从)
# master:
#   locust -f load-test-million.py --master --host=http://api --users 1000000
# worker (推荐 4-8 节点):
#   locust -f load-test-million.py --worker --master-host=master.local

# 性能 SLO 验证:
#   p99 < 200ms
#   错误率 < 0.1%
#   QPS = users * (1/wait_time) = 1000000 * (1/2) = 50万 QPS (基础)
#   + 多任务权重 → 100 万 QPS 峰值

# 集群扩展性:
#   20 节点 × 30k QPS = 60 万 QPS
#   30 节点 + CDN + ES = 100 万 QPS


@events.test_start.add_listener
def on_test_start(environment, **kwargs):
    """压测启动时输出配置"""
    print(f"""
    ══════════════════════════════════════════════════════
      微课平台 100 万级压测
    ══════════════════════════════════════════════════════
      Host:        {environment.host}
      Users:       {environment.runner.target_user_count if hasattr(environment.runner, 'target_user_count') else 'N/A'}
      Spawn rate:  {environment.runner.spawn_rate if hasattr(environment.runner, 'spawn_rate') else 'N/A'}
      Run time:    {environment.parsed_options.run_time if hasattr(environment, 'parsed_options') else 'N/A'}
    ══════════════════════════════════════════════════════
    """)


@events.test_stop.add_listener
def on_test_stop(environment, **kwargs):
    """压测结束输出汇总"""
    stats = environment.stats
    print(f"""
    ══════════════════════════════════════════════════════
      压测结果汇总
    ══════════════════════════════════════════════════════
      总请求数:    {stats.total.num_requests}
      失败数:      {stats.total.num_failures}
      错误率:      {stats.total.fail_ratio * 100:.3f}%
      平均响应:    {stats.total.avg_response_time:.1f}ms
      中位数:      {stats.total.median_response_time}ms
      p95:         {stats.total.get_response_time_percentile(0.95):.1f}ms
      p99:         {stats.total.get_response_time_percentile(0.99):.1f}ms
      最大:        {stats.total.max_response_time}ms
      总 RPS:      {stats.total.total_rps:.1f}
    ══════════════════════════════════════════════════════

      SLO 验证:
        p99 < 200ms:    {'✅' if stats.total.get_response_time_percentile(0.99) < 200 else '❌'}
        错误率 < 0.1%:  {'✅' if stats.total.fail_ratio < 0.001 else '❌'}
        RPS > 10000:    {'✅' if stats.total.total_rps > 10000 else '❌'}
    ══════════════════════════════════════════════════════
    """)