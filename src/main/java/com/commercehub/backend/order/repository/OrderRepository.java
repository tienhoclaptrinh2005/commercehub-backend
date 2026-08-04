package com.commercehub.backend.order.repository;

import com.commercehub.backend.order.entity.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = {"shop"})
    Page<Order> findByUserId(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"shop"})
    Page<Order> findByShopId(Long shopId, Pageable pageable);


    //  Hàm tìm các đơn hàng Shop ĐÃ NHẬN nhưng xử lý quá hạn (quá 24h)
    List<Order> findByStatusAndProcessingDeadlineAtBefore(String status, OffsetDateTime deadline);

    // Hàm tìm các đơn hàng Shop BỎ QUÊN không thèm duyệt
    List<Order> findByStatusAndApprovalDeadlineAtBefore(String status, OffsetDateTime deadline);


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :orderId")
    Optional<Order> findByIdWithLock(@Param("orderId") Long orderId);

    /** Tra cứu các đơn đã tạo bởi 1 lần checkout (idempotency). */
    List<Order> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);


}