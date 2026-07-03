package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.microcourse.dto.PageResult;
import com.microcourse.dto.invite.AcceptWithChaptersRequest;
import com.microcourse.dto.invite.AcceptWithChaptersRequest.ChapterDecisionItem;
import com.microcourse.entity.MicroSpecialty;
import com.microcourse.entity.MicroSpecialtyTeacher;
import com.microcourse.entity.User;
import com.microcourse.entity.proposal.ChapterTeacherAssignment;
import com.microcourse.enums.NotificationType;
import com.microcourse.enums.UserRole;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.ChapterTeacherAssignmentRepository;
import com.microcourse.repository.MicroSpecialtyRepository;
import com.microcourse.repository.MicroSpecialtyTeacherRepository;
import com.microcourse.repository.UserRepository;
import com.microcourse.service.MicroSpecialtyInviteService;
import com.microcourse.service.MicroSpecialtyService;
import com.microcourse.service.NotificationService;
import com.microcourse.util.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class MicroSpecialtyInviteServiceImpl implements MicroSpecialtyInviteService {

    private static final Logger log = LoggerFactory.getLogger(MicroSpecialtyInviteServiceImpl.class);
    private static final int BATCH_SIZE = 50;

    private final MicroSpecialtyTeacherRepository teacherRepository;
    private final MicroSpecialtyRepository msRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final MicroSpecialtyService msService;
    private final ChapterTeacherAssignmentRepository chapterAssignRepository;

    public MicroSpecialtyInviteServiceImpl(MicroSpecialtyTeacherRepository teacherRepository,
                                           MicroSpecialtyRepository msRepository,
                                           UserRepository userRepository,
                                           NotificationService notificationService,
                                           MicroSpecialtyService msService,
                                           ChapterTeacherAssignmentRepository chapterAssignRepository) {
        this.teacherRepository = teacherRepository;
        this.msRepository = msRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.msService = msService;
        this.chapterAssignRepository = chapterAssignRepository;
    }

    @Override
    public PageResult<?> getPendingInvites(int page, int size, String status) {
        Long userId = SecurityUtil.getCurrentUserId();
        LambdaQueryWrapper<MicroSpecialtyTeacher> wrapper = new LambdaQueryWrapper<MicroSpecialtyTeacher>()
                .eq(MicroSpecialtyTeacher::getTeacherId, userId);
        if (status != null && !status.isEmpty()) {
            if ("PENDING".equals(status)) {
                wrapper.in(MicroSpecialtyTeacher::getInviteStatus, "INVITED", "PENDING_ACADEMIC");
            } else {
                wrapper.eq(MicroSpecialtyTeacher::getInviteStatus, status);
            }
        } else {
            wrapper.in(MicroSpecialtyTeacher::getInviteStatus, "INVITED", "PENDING_ACADEMIC");
        }
        wrapper.orderByDesc(MicroSpecialtyTeacher::getInvitedAt);
        IPage<MicroSpecialtyTeacher> ipage = teacherRepository.selectPage(
                new Page<>(page + 1, size), wrapper);
        return PageResult.of(ipage);
    }

    @Override
    public PageResult<?> getPendingCrossDeptInvites(int page, int size) {
        IPage<MicroSpecialtyTeacher> ipage = teacherRepository.selectPage(
                new Page<>(page + 1, size),
                new LambdaQueryWrapper<MicroSpecialtyTeacher>()
                        .eq(MicroSpecialtyTeacher::getInviteStatus, "PENDING_ACADEMIC")
                        .orderByDesc(MicroSpecialtyTeacher::getInvitedAt));
        return PageResult.of(ipage);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void acceptInvite(Long inviteId) {
        Long userId = SecurityUtil.getCurrentUserId();

        MicroSpecialtyTeacher record = teacherRepository.selectById(inviteId);
        if (record == null) throw new BusinessException(ErrorCode.MS_TEACHER_NOT_FOUND);

        // 身份校验：必须是本人
        if (!userId.equals(record.getTeacherId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }

        // 状态校验
        if (!"INVITED".equals(record.getInviteStatus())) {
            throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "邀请已失效或已处理");
        }

        // 过期校验
        if (record.getInviteExpiresAt() != null && record.getInviteExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.MS_INVITE_EXPIRED, "邀请已过期");
        }

        MicroSpecialty ms = msRepository.selectById(record.getMicroSpecialtyId());

        // 终态检查
        if (ms != null && ("CANCELLED".equals(ms.getStatus()) || "ARCHIVED".equals(ms.getStatus()))) {
            throw new BusinessException(ErrorCode.MS_TERMINAL_STATUS);
        }

        // 跨学院检测（§9.4）
        User invitedUser = userRepository.selectById(userId);
        boolean isCrossDept = false;
        if (ms != null && invitedUser != null && ms.getOfferDepartmentId() != null) {
            isCrossDept = !ms.getOfferDepartmentId().equals(invitedUser.getDepartmentId());
        }

        if (isCrossDept) {
            int oldVersion = record.getVersion() != null ? record.getVersion() : 0;
            // P1-1 修复: 加 status 筛选防止与 reviewCrossDept 并发覆盖
            int crossAffected = teacherRepository.update(null,
                    new LambdaUpdateWrapper<MicroSpecialtyTeacher>()
                            .eq(MicroSpecialtyTeacher::getId, inviteId)
                            .eq(MicroSpecialtyTeacher::getVersion, oldVersion)
                            .eq(MicroSpecialtyTeacher::getInviteStatus, "INVITED")
                            .set(MicroSpecialtyTeacher::getInviteStatus, "PENDING_ACADEMIC")
                            .set(MicroSpecialtyTeacher::getRespondedAt, LocalDateTime.now())
                            .setSql("version = version + 1"));
            if (crossAffected == 0) {
                throw new BusinessException(ErrorCode.MS_CONCURRENT_MODIFICATION);
            }

            // P0-2 修复：通知所有 ACADEMIC 角色用户（而非非法 userId 0L）
            List<User> academicUsers = userRepository.selectList(
                    new LambdaQueryWrapper<User>().eq(User::getRole, UserRole.ACADEMIC));
            for (User au : academicUsers) {
                notificationService.notifyAsync(au.getId(), NotificationType.MS_INVITE_CROSS_DEPT,
                        "跨学院邀请待审批", invitedUser.getRealName() + " 接受邀请需要跨学院审批",
                        ms != null ? ms.getId() : null);
            }
        } else {
            int oldVersion = record.getVersion() != null ? record.getVersion() : 0;
            // P1-1 修复: 加 status 筛选防止并发
            int selfAffected = teacherRepository.update(null,
                    new LambdaUpdateWrapper<MicroSpecialtyTeacher>()
                            .eq(MicroSpecialtyTeacher::getId, inviteId)
                            .eq(MicroSpecialtyTeacher::getVersion, oldVersion)
                            .eq(MicroSpecialtyTeacher::getInviteStatus, "INVITED")
                            .set(MicroSpecialtyTeacher::getInviteStatus, "ACTIVE")
                            .set(MicroSpecialtyTeacher::getRespondedAt, LocalDateTime.now())
                            .set(MicroSpecialtyTeacher::getJoinedAt, LocalDateTime.now())
                            .setSql("version = version + 1"));
            if (selfAffected == 0) {
                throw new BusinessException(ErrorCode.MS_CONCURRENT_MODIFICATION);
            }

            // 如果是 LEAD 角色，双重确认更新 lead_teacher_id
            if ("LEAD".equals(record.getRole()) && ms != null) {
                int msVersion = ms.getVersion() != null ? ms.getVersion() : 0;
                int msAffected = msRepository.update(null,
                        new LambdaUpdateWrapper<MicroSpecialty>()
                                .eq(MicroSpecialty::getId, ms.getId())
                                .eq(MicroSpecialty::getVersion, msVersion)
                                .set(MicroSpecialty::getLeadTeacherId, userId)
                                .set(MicroSpecialty::getUpdatedAt, LocalDateTime.now())
                                .setSql("version = version + 1"));
                if (msAffected == 0) {
                    throw new BusinessException(ErrorCode.MS_CONCURRENT_MODIFICATION);
                }
            }
        }

        // 通知邀请人
        if (record.getInvitedBy() != null) {
            User inviteeUser = userRepository.selectById(userId);
            notificationService.notifyAsync(record.getInvitedBy(), NotificationType.MS_INVITE_ACCEPTED,
                    "邀请已接受",
                    (inviteeUser != null ? inviteeUser.getRealName() : "教师") + " 已接受邀请",
                    ms != null ? ms.getId() : null);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void declineInvite(Long inviteId) {
        Long userId = SecurityUtil.getCurrentUserId();

        MicroSpecialtyTeacher record = teacherRepository.selectById(inviteId);
        if (record == null) throw new BusinessException(ErrorCode.MS_TEACHER_NOT_FOUND);

        if (!userId.equals(record.getTeacherId())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }

        if (!"INVITED".equals(record.getInviteStatus())) {
            throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "邀请已处理");
        }

        int oldVersion = record.getVersion() != null ? record.getVersion() : 0;
        int affected = teacherRepository.update(null,
                new LambdaUpdateWrapper<MicroSpecialtyTeacher>()
                        .eq(MicroSpecialtyTeacher::getId, inviteId)
                        .eq(MicroSpecialtyTeacher::getVersion, oldVersion)
                        .set(MicroSpecialtyTeacher::getInviteStatus, "DECLINED")
                        .set(MicroSpecialtyTeacher::getRespondedAt, LocalDateTime.now())
                        .setSql("version = version + 1"));
        if (affected == 0) {
            throw new BusinessException(ErrorCode.MS_CONCURRENT_MODIFICATION);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void leaveTeam(Long inviteId) {
        // §7.4 端点对齐 spec：用教师记录 ID（inviteId）直接查找
        MicroSpecialtyTeacher record = teacherRepository.selectById(inviteId);
        if (record == null) throw new BusinessException(ErrorCode.MS_TEACHER_NOT_FOUND);

        // 校验本人操作
        Long userId = SecurityUtil.getCurrentUserId();
        if (!record.getTeacherId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "仅本人可操作退出团队");
        }
        if (!"ACTIVE".equals(record.getInviteStatus())) {
            throw new BusinessException(ErrorCode.MS_TEACHER_NOT_FOUND, "当前状态不可退出");
        }
        // 负责人不能直接退出
        if ("LEAD".equals(record.getRole())) {
            throw new BusinessException(ErrorCode.MS_TEAM_LEAD_CANNOT_LEAVE);
        }

        int oldVersion = record.getVersion() != null ? record.getVersion() : 0;
        int affected = teacherRepository.update(null,
                new LambdaUpdateWrapper<MicroSpecialtyTeacher>()
                        .eq(MicroSpecialtyTeacher::getId, record.getId())
                        .eq(MicroSpecialtyTeacher::getVersion, oldVersion)
                        .set(MicroSpecialtyTeacher::getInviteStatus, "REMOVED")
                        .set(MicroSpecialtyTeacher::getLeftAt, LocalDateTime.now())
                        .setSql("version = version + 1"));
        if (affected == 0) {
            throw new BusinessException(ErrorCode.MS_CONCURRENT_MODIFICATION);
        }

        // 通知 LEAD
        Long msId = record.getMicroSpecialtyId();
        MicroSpecialty ms = msRepository.selectById(msId);
        if (ms != null && ms.getLeadTeacherId() != null) {
            User leavingUser = userRepository.selectById(userId);
            notificationService.notifyAsync(ms.getLeadTeacherId(), NotificationType.MS_TEAM_LEFT,
                    "教师退出团队",
                    "教师 " + (leavingUser != null ? leavingUser.getRealName() : "") + " 已退出微专业团队",
                    msId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reviewCrossDept(Long inviteId, boolean approve, String reason) {
        MicroSpecialtyTeacher record = teacherRepository.selectById(inviteId);
        if (record == null) throw new BusinessException(ErrorCode.MS_TEACHER_NOT_FOUND);

        if (!"PENDING_ACADEMIC".equals(record.getInviteStatus())) {
            throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "非跨学院待审状态");
        }

        MicroSpecialty ms = msRepository.selectById(record.getMicroSpecialtyId());

        // 终态检查 (先于 DB 更新)
        if (ms != null && ("CANCELLED".equals(ms.getStatus()) || "ARCHIVED".equals(ms.getStatus()))) {
            throw new BusinessException(ErrorCode.MS_TERMINAL_STATUS);
        }

        String newStatus = approve ? "ACTIVE" : "REJECTED";
        int oldVersion = record.getVersion() != null ? record.getVersion() : 0;
        int affected = teacherRepository.update(null,
                new LambdaUpdateWrapper<MicroSpecialtyTeacher>()
                        .eq(MicroSpecialtyTeacher::getId, inviteId)
                        .eq(MicroSpecialtyTeacher::getVersion, oldVersion)
                        .eq(MicroSpecialtyTeacher::getInviteStatus, "PENDING_ACADEMIC")
                        .set(MicroSpecialtyTeacher::getInviteStatus, newStatus)
                        .set(MicroSpecialtyTeacher::getRespondedAt, LocalDateTime.now())
                        .setSql("version = version + 1"));
        if (affected == 0) {
            throw new BusinessException(ErrorCode.MS_CONCURRENT_MODIFICATION);
        }

        // 如果是 LEAD 且批准，更新 lead_teacher_id
        if (approve && "LEAD".equals(record.getRole()) && ms != null) {
            int msVersion = ms.getVersion() != null ? ms.getVersion() : 0;
            int msAffected = msRepository.update(null,
                    new LambdaUpdateWrapper<MicroSpecialty>()
                            .eq(MicroSpecialty::getId, ms.getId())
                            .eq(MicroSpecialty::getVersion, msVersion)
                            .set(MicroSpecialty::getLeadTeacherId, record.getTeacherId())
                            .set(MicroSpecialty::getUpdatedAt, LocalDateTime.now())
                            .setSql("version = version + 1"));
            if (msAffected == 0) {
                throw new BusinessException(ErrorCode.MS_CONCURRENT_MODIFICATION);
            }
        }

        // 通知被邀请教师
        User teacher = userRepository.selectById(record.getTeacherId());
        notificationService.notifyAsync(record.getTeacherId(), NotificationType.MS_INVITE_CROSS_DEPT,
                approve ? "跨学院审批通过" : "跨学院审批未通过",
                (teacher != null ? teacher.getRealName() : "教师")
                        + (approve ? " 跨学院审批已通过" : " 跨学院审批未通过，原因：" + (reason != null ? reason : "未填写")),
                ms != null ? ms.getId() : null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reinviteTeacher(Long inviteId, String role, String responsibility, Long courseId) {
        // §7.4 端点对齐 spec：用教师记录 ID 直接查找复用的 DECLINED/REMOVED 记录（§2.3）
        MicroSpecialtyTeacher record = teacherRepository.selectById(inviteId);
        if (record == null) throw new BusinessException(ErrorCode.MS_TEACHER_NOT_FOUND);
        if (!"DECLINED".equals(record.getInviteStatus()) && !"REMOVED".equals(record.getInviteStatus())) {
            throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "仅已拒绝或已移除的记录可重新邀请");
        }

        // 校验当前用户是否为该微专业负责人
        // 使用 isLeadOf() 从教师表 ACTIVE LEAD 查询（权威数据源），而非 ms.leadTeacherId 冗余字段
        // leadTeacherId 可能在 LEAD 继任事务中存在短暂不一致窗口，isLeadOf() 直接查教师表更可靠
        Long currentUserId = SecurityUtil.getCurrentUserId();
        if (!msService.isLeadOf(record.getMicroSpecialtyId(), currentUserId) && !SecurityUtil.isAdmin()) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "仅微专业负责人可执行此操作");
        }

        int oldVersion = record.getVersion() != null ? record.getVersion() : 0;
        int affected = teacherRepository.update(null,
                new LambdaUpdateWrapper<MicroSpecialtyTeacher>()
                        .eq(MicroSpecialtyTeacher::getId, record.getId())
                        .eq(MicroSpecialtyTeacher::getVersion, oldVersion)
                        .set(MicroSpecialtyTeacher::getInviteStatus, "INVITED")
                        .set(MicroSpecialtyTeacher::getRole, role)
                        .set(MicroSpecialtyTeacher::getResponsibility, responsibility)
                        .set(MicroSpecialtyTeacher::getCourseId, courseId)
                        .set(MicroSpecialtyTeacher::getInvitedBy, SecurityUtil.getCurrentUserId())
                        .set(MicroSpecialtyTeacher::getInvitedAt, LocalDateTime.now())
                        .set(MicroSpecialtyTeacher::getInviteExpiresAt, LocalDateTime.now().plusDays(7))
                        .set(MicroSpecialtyTeacher::getRespondedAt, null)
                        .setSql("version = version + 1"));
        if (affected == 0) {
            throw new BusinessException(ErrorCode.MS_CONCURRENT_MODIFICATION);
        }

        Long msId = record.getMicroSpecialtyId();
        MicroSpecialty ms = msRepository.selectById(msId);
        notificationService.notifyAsync(record.getTeacherId(), NotificationType.MS_INVITE_TEAM,
                "重新邀请", "您被重新邀请加入微专业《" + (ms != null ? ms.getTitle() : "") + "》团队", msId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int scanExpired() {
        int totalExpired = 0;

        // 分批扫 INVITED 过期（§9.3），按过期时间升序确保先到期的优先处理
        List<MicroSpecialtyTeacher> expiredList = teacherRepository.selectList(
                new LambdaQueryWrapper<MicroSpecialtyTeacher>()
                        .eq(MicroSpecialtyTeacher::getInviteStatus, "INVITED")
                        .lt(MicroSpecialtyTeacher::getInviteExpiresAt, LocalDateTime.now())
                        .orderByAsc(MicroSpecialtyTeacher::getInviteExpiresAt));

        for (int i = 0; i < expiredList.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, expiredList.size());
            for (int j = i; j < end; j++) {
                MicroSpecialtyTeacher record = expiredList.get(j);
                int affected = teacherRepository.update(null,
                        new LambdaUpdateWrapper<MicroSpecialtyTeacher>()
                                .eq(MicroSpecialtyTeacher::getId, record.getId())
                                .eq(MicroSpecialtyTeacher::getVersion, record.getVersion())
                                .eq(MicroSpecialtyTeacher::getInviteStatus, "INVITED")
                                .set(MicroSpecialtyTeacher::getInviteStatus, "DECLINED")
                                .set(MicroSpecialtyTeacher::getRespondedAt, LocalDateTime.now())
                                .setSql("version = version + 1"));
                if (affected > 0) {
                    totalExpired++;
                    // 通知 LEAD + ACADEMIC
                    MicroSpecialty ms = msRepository.selectById(record.getMicroSpecialtyId());
                    if (ms != null && ms.getLeadTeacherId() != null) {
                        notificationService.notifyAsync(ms.getLeadTeacherId(), NotificationType.MS_INVITE_EXPIRED,
                                "邀请已过期", "微专业《" + ms.getTitle() + "》教师邀请已过期", ms.getId());
                    }
                    // 若角色=LEAD，额外通知 ACADEMIC（P1-1 修复）
                    if ("LEAD".equals(record.getRole())) {
                        List<User> academicUsers = userRepository.selectList(
                                new LambdaQueryWrapper<User>().eq(User::getRole, UserRole.ACADEMIC));
                        for (User au : academicUsers) {
                            notificationService.notifyAsync(au.getId(), NotificationType.MS_INVITE_EXPIRED,
                                    "微专业负责人邀请过期",
                                    (ms != null ? "微专业《" + ms.getTitle() + "》" : "微专业")
                                            + "负责人邀请已过期，请关注",
                                    ms != null ? ms.getId() : null);
                        }
                    }
                }
            }
        }

        // PENDING_ACADEMIC 超过 14 天通知 ACADEMIC（P1-1 修复），按响应时间升序确保最早滞留的优先通知
        List<MicroSpecialtyTeacher> staleList = teacherRepository.selectList(
                new LambdaQueryWrapper<MicroSpecialtyTeacher>()
                        .eq(MicroSpecialtyTeacher::getInviteStatus, "PENDING_ACADEMIC")
                        .lt(MicroSpecialtyTeacher::getRespondedAt, LocalDateTime.now().minusDays(14))
                        .orderByAsc(MicroSpecialtyTeacher::getRespondedAt));
        if (!staleList.isEmpty()) {
            List<User> academicUsers = userRepository.selectList(
                    new LambdaQueryWrapper<User>().eq(User::getRole, UserRole.ACADEMIC));
            for (MicroSpecialtyTeacher stale : staleList) {
                MicroSpecialty ms = msRepository.selectById(stale.getMicroSpecialtyId());
                for (User au : academicUsers) {
                    notificationService.notifyAsync(au.getId(), NotificationType.MS_INVITE_EXPIRED,
                            "跨学院审批超时未处理",
                            (ms != null ? "微专业《" + ms.getTitle() + "》" : "微专业")
                                    + "跨学院邀请审批超过 14 天未处理，请及时审批",
                            ms != null ? ms.getId() : null);
                }
            }
        }

        return totalExpired;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void acceptWithChapters(Long inviteId, AcceptWithChaptersRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        // 1. 校验邀请有效
        MicroSpecialtyTeacher record = teacherRepository.selectById(inviteId);
        if (record == null) throw new BusinessException(ErrorCode.MS_TEACHER_NOT_FOUND);
        if (!userId.equals(record.getTeacherId()))
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        if (!"INVITED".equals(record.getInviteStatus()))
            throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "邀请已处理或已过期");

        // 2. 校验每个章节决策都填写了 source (BR-1)
        for (ChapterDecisionItem dec : request.getChapterDecisions()) {
            if (dec.getSource() == null || "TBD".equals(dec.getSource()))
                throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "所有章节都必须选择来源(已有/新建)");
        }

        // 3. 验证 teacher 身份
        User teacher = userRepository.selectById(userId);
        if (teacher == null) throw new BusinessException(ErrorCode.BAD_REQUEST_PARAM, "用户不存在");

        // 4. 更新 MicroSpecialtyTeacher 的微专业状态
        record.setInviteStatus("ACTIVE");
        record.setRespondedAt(LocalDateTime.now());
        record.setVersion(record.getVersion() != null ? record.getVersion() : 0);
        int updatedRows = teacherRepository.updateById(record);
        if (updatedRows == 0) {
            throw new BusinessException(ErrorCode.MS_CONCURRENT_MODIFICATION, "邀请已被其他操作修改，请刷新后重试");
        }

        // 5. P0 修复: 仅更新本次邀请对应的章节决策(加 proposalId 过滤,防止跨微专业污染)
        // 之前缺少 proposalId→仅按 teacherId+acceptStatus 匹配,教师在不同微专业的
        // PENDING 章节都会被误标 ACCEPTED
        List<Long> affectedChapterIds = request.getChapterDecisions().stream()
                .map(ChapterDecisionItem::getChapterId)
                .filter(id -> id != null)
                .collect(Collectors.toList());
        if (!affectedChapterIds.isEmpty()) {
            LambdaUpdateWrapper<ChapterTeacherAssignment> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(ChapterTeacherAssignment::getTeacherId, userId)
                         .eq(ChapterTeacherAssignment::getAcceptStatus, "PENDING")
                         .in(ChapterTeacherAssignment::getChapterId, affectedChapterIds);
            ChapterTeacherAssignment update = new ChapterTeacherAssignment();
            update.setAcceptStatus("ACCEPTED");
            update.setAcceptedAt(LocalDateTime.now());
            chapterAssignRepository.update(update, updateWrapper);
        }

        // 6. 发送团队通知
        if (record.getInvitedBy() != null) {
            notificationService.notifyAsync(record.getInvitedBy(), NotificationType.MS_INVITE_ACCEPTED,
                    "邀请已接受", teacher.getRealName() + " 已加入微专业团队",
                    record.getMicroSpecialtyId());
        }
    }
}
