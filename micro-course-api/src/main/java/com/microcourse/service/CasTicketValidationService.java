package com.microcourse.service;

/**
 * CAS 票据验证服务。
 */
public interface CasTicketValidationService {

    /**
     * 验证 ticket 并返回 CAS 用户名。
     *
     * @param ticket CAS 票据
     * @return CAS 用户名
     */
    String validateTicket(String ticket);
}
