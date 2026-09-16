package com.commercehub.backend.voucher.repository;

import com.commercehub.backend.voucher.entity.VoucherUsage;
import com.commercehub.backend.voucher.entity.VoucherUsageStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VoucherUsageRepository extends JpaRepository<VoucherUsage, Long> {
    boolean existsByVoucherIdAndUserIdAndStatus(Long voucherId, Long userId, VoucherUsageStatus status);
    Optional<VoucherUsage> findByOrderIdAndStatus(Long orderId, VoucherUsageStatus status);
}
