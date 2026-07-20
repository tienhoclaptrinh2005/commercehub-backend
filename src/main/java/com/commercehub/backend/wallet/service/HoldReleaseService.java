package com.commercehub.backend.wallet.service;

import com.commercehub.backend.wallet.entity.HoldRelease;
import com.commercehub.backend.wallet.repository.HoldReleaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class HoldReleaseService {

    private final HoldReleaseRepository holdReleaseRepository;
    private final HoldReleaseProcessor holdReleaseProcessor;

    public void processDueReleases(OffsetDateTime now) {
        List<HoldRelease> dueReleases = holdReleaseRepository.findDueReleases(now);

        for (HoldRelease hr : dueReleases) {
            try {

                holdReleaseProcessor.processSingle(hr);
            } catch (Exception e) {

                log.error("Lỗi khi xử lý giải phóng tiền HoldRelease ID {}: {}", hr.getId(), e.getMessage());
            }
        }
    }

    /**
     * Nhả tiền sớm cho Seller khi Buyer xác nhận đã nhận hàng.
     * Tìm HoldRelease theo orderItemId, nếu đang HOLDING thì release ngay.
     */
    public void earlyReleaseByOrderItemId(Long orderItemId) {
        HoldRelease hr = holdReleaseRepository.findByOrderItemId(orderItemId);
        if (hr == null) {
            log.warn("Không tìm thấy HoldRelease cho orderItemId={}", orderItemId);
            return;
        }
        if (!"HOLDING".equals(hr.getStatus())) {
            log.info("HoldRelease ID {} đã được xử lý (status={}), bỏ qua.", hr.getId(), hr.getStatus());
            return;
        }
        try {
            holdReleaseProcessor.processSingle(hr);
            log.info("Nhả tiền sớm thành công cho HoldRelease ID {} (orderItemId={})", hr.getId(), orderItemId);
        } catch (Exception e) {
            log.error("Lỗi khi nhả tiền sớm HoldRelease ID {}: {}", hr.getId(), e.getMessage());
        }
    }
}