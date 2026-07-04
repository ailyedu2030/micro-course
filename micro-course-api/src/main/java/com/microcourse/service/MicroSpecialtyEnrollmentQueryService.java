package com.microcourse.service;

import com.microcourse.dto.PageResult;
import com.microcourse.dto.microSpecialty.MicroSpecialtyEnrollmentVO;
import com.microcourse.entity.MicroSpecialty;
import com.microcourse.entity.MicroSpecialtyEnrollment;

import java.util.List;

/**
 * 微专业修读查询 Service 接口。
 * 职责：修读记录查询 + VO 转换。
 */
public interface MicroSpecialtyEnrollmentQueryService {

    /** 我的修读列表 */
    List<MicroSpecialtyEnrollmentVO> getMyEnrollments();

    /** 微专业修读名单（LEAD/ACADEMIC） */
    PageResult<MicroSpecialtyEnrollmentVO> listEnrollments(Long msId, int page, int size, String status);

    /** 修读记录 → VO 转换 */
    MicroSpecialtyEnrollmentVO toVO(MicroSpecialtyEnrollment en, MicroSpecialty ms);
}
