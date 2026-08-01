package com.commercehub.backend.order.repository;

import com.commercehub.backend.order.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    // ĐÃ FIX Bug #42: Eager fetch shop để tránh N+1 query khi mapping OrderResponse
    @EntityGraph(attributePaths = {"shop"})
    Page<Order> findByUserId(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"shop"})
    Page<Order> findByShopId(Long shopId, Pageable pageable);


    //  Hàm tìm các đơn hàng Shop ĐÃ NHẬN nhưng xử lý quá hạn (quá 24h)
    List<Order> findByStatusAndProcessingDeadlineAtBefore(String status, OffsetDateTime deadline);

    // Hàm tìm các đơn hàng Shop BỎ QUÊN không thèm duyệt
    List<Order> findByStatusAndApprovalDeadlineAtBefore(String status, OffsetDateTime deadline);
}