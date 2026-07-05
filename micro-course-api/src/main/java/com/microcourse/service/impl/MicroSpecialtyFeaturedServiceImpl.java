package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.microcourse.entity.MicroSpecialty;
import com.microcourse.enums.MicroSpecialtyFeaturedStatus;
import com.microcourse.enums.MicroSpecialtyStatus;
import com.microcourse.enums.NotificationType;
import com.microcourse.exception.BusinessException;
import com.microcourse.exception.ErrorCode;
import com.microcourse.repository.MicroSpecialtyRepository;
import com.microcourse.service.MicroSpecialtyFeaturedService;
import com.microcourse.service.MicroSpecialtyService;
import com.microcourse.service.NotificationService;
import com.microcourse.util.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class MicroSpecialtyFeaturedServiceImpl implements MicroSpecialtyFeaturedService {

    private static final Logger log = LoggerFactory.getLogger(MicroSpecialtyFeaturedServiceImpl.class);

    private final MicroSpecialtyRepository msRepository;
    private final NotificationService notificationService;
    private final MicroSpecialtyService msService;

    public MicroSpecialtyFeaturedServiceImpl(MicroSpecialtyRepository msRepository,
                                              NotificationService notificationService,
                                              MicroSpecialtyService msService) {
        this.msRepository = msRepository;
        this.notificationService = notificationService;
        this.msService = msService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyFeatured(Long msId, String reason) {
        // 检查当前用户是否为微专业负责人
        if (!msService.isLeadOf(msId, SecurityUtil.getCurrentUserId()) && !SecurityUtil.isAdmin()) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "仅微专业负责人可执行此操作");
        }

        MicroSpecialty ms = msRepository.selectById(msId);
        if (ms == null) throw new BusinessException(ErrorCode.MS_NOT_FOUND);

        // 终态检查
        MicroSpecialtyStatus msStatus = MicroSpecialtyStatus.fromString(ms.getStatus());
        if (msStatus == MicroSpecialtyStatus.CANCELLED || msStatus == MicroSpecialtyStatus.ARCHIVED) {
            throw new BusinessException(ErrorCode.MS_TERMINAL_STATUS);
        }

        if (msStatus != MicroSpecialtyStatus.RECRUITING) {
            throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "仅招生中状态可申请置顶");
        }

        MicroSpecialtyFeaturedStatus currentFeatured = MicroSpecialtyFeaturedStatus.fromString(ms.getFeaturedStatus());
        if (currentFeatured == null || !currentFeatured.canTransitionTo(MicroSpecialtyFeaturedStatus.PENDING)) {
            throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "当前置顶状态不可申请");
        }

        int oldVersion = ms.getVersion();
        int affected = msRepository.update(null,
                new LambdaUpdateWrapper<MicroSpecialty>()
                        .eq(MicroSpecialty::getId, msId)
                        .eq(MicroSpecialty::getVersion, oldVersion)
                        .set(MicroSpecialty::getFeaturedStatus, "PENDING")
                        .set(MicroSpecialty::getFeaturedApplyAt, LocalDateTime.now())
                        .set(MicroSpecialty::getFeaturedApplyReason, reason)
                        .set(MicroSpecialty::getUpdatedAt, LocalDateTime.now())
                        .setSql("version = version + 1"));
        if (affected == 0) {
            throw new BusinessException(ErrorCode.MS_CONCURRENT_MODIFICATION);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveFeatured(Long msId) {
        MicroSpecialty ms = msRepository.selectById(msId);
        if (ms == null) throw new BusinessException(ErrorCode.MS_NOT_FOUND);

        // 终态检查
        MicroSpecialtyStatus msStatus = MicroSpecialtyStatus.fromString(ms.getStatus());
        if (msStatus == MicroSpecialtyStatus.CANCELLED || msStatus == MicroSpecialtyStatus.ARCHIVED) {
            throw new BusinessException(ErrorCode.MS_TERMINAL_STATUS);
        }
        // P2-2 修复: 确认微专业仍处于招生中(可能审批时已被关闭)
        if (msStatus != MicroSpecialtyStatus.RECRUITING) {
            throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "微专业当前非招生状态，不可审批置顶");
        }

        MicroSpecialtyFeaturedStatus currentFeatured = MicroSpecialtyFeaturedStatus.fromString(ms.getFeaturedStatus());
        if (currentFeatured == null || !currentFeatured.canTransitionTo(MicroSpecialtyFeaturedStatus.APPROVED)) {
            throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "仅待审核状态可审批置顶");
        }

        int oldVersion = ms.getVersion();
        int affected = msRepository.update(null,
                new LambdaUpdateWrapper<MicroSpecialty>()
                        .eq(MicroSpecialty::getId, msId)
                        .eq(MicroSpecialty::getVersion, oldVersion)
                        .eq(MicroSpecialty::getFeaturedStatus, "PENDING")
                        .set(MicroSpecialty::getFeaturedStatus, "APPROVED")
                        .set(MicroSpecialty::getIsFeatured, true)
                        .set(MicroSpecialty::getFeaturedApprovedBy, SecurityUtil.getCurrentUserId())
                        .set(MicroSpecialty::getFeaturedApprovedAt, LocalDateTime.now())
                        .set(MicroSpecialty::getUpdatedAt, LocalDateTime.now())
                        .setSql("version = version + 1"));
        if (affected == 0) {
            throw new BusinessException(ErrorCode.MS_CONCURRENT_MODIFICATION);
        }

        if (ms.getLeadTeacherId() != null) {
            notificationService.notifyAsync(ms.getLeadTeacherId(), NotificationType.MS_FEATURED_APPROVED,
                    "置顶审批通过", "微专业《" + ms.getTitle() + "》置顶申请已通过", msId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectFeatured(Long msId, String reason) {
        MicroSpecialty ms = msRepository.selectById(msId);
        if (ms == null) throw new BusinessException(ErrorCode.MS_NOT_FOUND);

        // 终态检查
        MicroSpecialtyStatus msStatus = MicroSpecialtyStatus.fromString(ms.getStatus());
        if (msStatus == MicroSpecialtyStatus.CANCELLED || msStatus == MicroSpecialtyStatus.ARCHIVED) {
            throw new BusinessException(ErrorCode.MS_TERMINAL_STATUS);
        }
        // P2-3 修复: 与 approveFeatured/setGoldFeatured 一致,加 RECRUITING 检查
        if (msStatus != MicroSpecialtyStatus.RECRUITING) {
            throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "微专业当前非招生状态");
        }

        MicroSpecialtyFeaturedStatus currentFeatured = MicroSpecialtyFeaturedStatus.fromString(ms.getFeaturedStatus());
        if (currentFeatured == null || !currentFeatured.canTransitionTo(MicroSpecialtyFeaturedStatus.REJECTED)) {
            throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "仅待审核状态可驳回置顶");
        }

        int oldVersion = ms.getVersion();
        int affected = msRepository.update(null,
                new LambdaUpdateWrapper<MicroSpecialty>()
                        .eq(MicroSpecialty::getId, msId)
                        .eq(MicroSpecialty::getVersion, oldVersion)
                        .eq(MicroSpecialty::getFeaturedStatus, "PENDING")
                        .set(MicroSpecialty::getFeaturedStatus, "REJECTED")
                        .set(MicroSpecialty::getUpdatedAt, LocalDateTime.now())
                        .setSql("version = version + 1"));
        if (affected == 0) {
            throw new BusinessException(ErrorCode.MS_CONCURRENT_MODIFICATION);
        }

        if (ms.getLeadTeacherId() != null) {
            notificationService.notifyAsync(ms.getLeadTeacherId(), NotificationType.MS_FEATURED_REJECTED,
                    "置顶审批驳回", "微专业《" + ms.getTitle() + "》置顶申请被驳回，原因：" + (reason != null ? reason : "未填写"), msId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unsetFeatured(Long msId) {
        MicroSpecialty ms = msRepository.selectById(msId);
        if (ms == null) throw new BusinessException(ErrorCode.MS_NOT_FOUND);

        // 终态检查
        MicroSpecialtyStatus msStatus = MicroSpecialtyStatus.fromString(ms.getStatus());
        if (msStatus == MicroSpecialtyStatus.CANCELLED || msStatus == MicroSpecialtyStatus.ARCHIVED) {
            throw new BusinessException(ErrorCode.MS_TERMINAL_STATUS);
        }

        MicroSpecialtyFeaturedStatus currentFeatured = MicroSpecialtyFeaturedStatus.fromString(ms.getFeaturedStatus());
        if (currentFeatured == null || !currentFeatured.canTransitionTo(MicroSpecialtyFeaturedStatus.NONE)) {
            throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "仅已通过状态可取消置顶");
        }

        int oldVersion = ms.getVersion();
        int affected = msRepository.update(null,
                new LambdaUpdateWrapper<MicroSpecialty>()
                        .eq(MicroSpecialty::getId, msId)
                        .eq(MicroSpecialty::getVersion, oldVersion)
                        .eq(MicroSpecialty::getFeaturedStatus, "APPROVED")
                        .set(MicroSpecialty::getFeaturedStatus, "NONE")
                        .set(MicroSpecialty::getIsFeatured, false)
                        .set(MicroSpecialty::getUpdatedAt, LocalDateTime.now())
                        .setSql("version = version + 1"));
        if (affected == 0) {
            throw new BusinessException(ErrorCode.MS_CONCURRENT_MODIFICATION);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setGoldFeatured(Long msId) {
        MicroSpecialty ms = msRepository.selectById(msId);
        if (ms == null) throw new BusinessException(ErrorCode.MS_NOT_FOUND);

        // 终态检查
        MicroSpecialtyStatus msStatus = MicroSpecialtyStatus.fromString(ms.getStatus());
        if (msStatus == MicroSpecialtyStatus.CANCELLED || msStatus == MicroSpecialtyStatus.ARCHIVED) {
            throw new BusinessException(ErrorCode.MS_TERMINAL_STATUS);
        }
        // P2-2 修复: 金标仅可在招生中设置
        if (msStatus != MicroSpecialtyStatus.RECRUITING) {
            throw new BusinessException(ErrorCode.MS_STATUS_INVALID, "微专业当前非招生状态，不可设金标");
        }

        // 全校金标数量 < 2（§9 铁律）
        long currentGold = msRepository.selectCount(
                new LambdaQueryWrapper<MicroSpecialty>()
                        .eq(MicroSpecialty::getIsGoldFeatured, true));
        if (currentGold >= 2) throw new BusinessException(ErrorCode.MS_GOLD_LIMIT);

        int oldVersion = ms.getVersion();
        int affected = msRepository.update(null,
                new LambdaUpdateWrapper<MicroSpecialty>()
                        .eq(MicroSpecialty::getId, msId)
                        .eq(MicroSpecialty::getVersion, oldVersion)
                        .set(MicroSpecialty::getIsGoldFeatured, true)
                        .set(MicroSpecialty::getGoldFeaturedBy, SecurityUtil.getCurrentUserId())
                        .set(MicroSpecialty::getGoldFeaturedAt, LocalDateTime.now())
                        .set(MicroSpecialty::getUpdatedAt, LocalDateTime.now())
                        .setSql("version = version + 1"));
        if (affected == 0) {
            throw new BusinessException(ErrorCode.MS_CONCURRENT_MODIFICATION);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unsetGoldFeatured(Long msId) {
        MicroSpecialty ms = msRepository.selectById(msId);
        if (ms == null) throw new BusinessException(ErrorCode.MS_NOT_FOUND);

        // 终态检查
        MicroSpecialtyStatus msStatus = MicroSpecialtyStatus.fromString(ms.getStatus());
        if (msStatus == MicroSpecialtyStatus.CANCELLED || msStatus == MicroSpecialtyStatus.ARCHIVED) {
            throw new BusinessException(ErrorCode.MS_TERMINAL_STATUS);
        }

        int oldVersion = ms.getVersion();
        int affected = msRepository.update(null,
                new LambdaUpdateWrapper<MicroSpecialty>()
                        .eq(MicroSpecialty::getId, msId)
                        .eq(MicroSpecialty::getVersion, oldVersion)
                        .set(MicroSpecialty::getIsGoldFeatured, false)
                        .set(MicroSpecialty::getGoldFeaturedBy, null)
                        .set(MicroSpecialty::getGoldFeaturedAt, null)
                        .set(MicroSpecialty::getUpdatedAt, LocalDateTime.now())
                        .setSql("version = version + 1"));
        if (affected == 0) {
            throw new BusinessException(ErrorCode.MS_CONCURRENT_MODIFICATION);
        }
    }
}
