package com.microcourse.service;
import com.microcourse.dto.PricingForAdopterVO;

import com.microcourse.dto.CoursePricingInfoVO;
import com.microcourse.dto.CoursePricingRequest;

import java.util.Map;

public interface CoursePricingService {

    void updatePricing(Long courseId, CoursePricingRequest request);

    void submitPricingForReview(Long courseId);

    void reviewPricing(Long courseId, boolean approved, String reason);

    PricingForAdopterVO getPricingForAdopter(Long courseId);

    CoursePricingInfoVO getMyPricing(Long courseId);
}
