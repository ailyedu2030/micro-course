package com.microcourse.service;
public interface MicroSpecialtyInviteService {
    Object getPendingInvites();
    void accept(Long inviteId); void decline(Long inviteId);
    void reviewCrossDept(Long inviteId, String action);
    void scanExpired();
}
