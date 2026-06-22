package com.microcourse.service;
public interface MicroSpecialtyFeaturedService {
    void apply(Long msId, String reason);
    void approve(Long msId); void reject(Long msId);
    void setGold(Long msId); void unsetGold(Long msId);
}
