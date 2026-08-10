package com.commercehub.backend.product.repository;

import com.commercehub.backend.product.entity.AssetDeliveryLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AssetDeliveryLogRepository extends JpaRepository<AssetDeliveryLog, Long> {

    List<AssetDeliveryLog> findByOrderItemId(Long orderItemId);

    @Query("SELECT l FROM AssetDeliveryLog l WHERE l.orderItemId IN " +
            "(SELECT oi.id FROM OrderItem oi WHERE oi.order.id = :orderId) " +
            "ORDER BY l.orderItemId ASC, l.id ASC")
    List<AssetDeliveryLog> findByOrderId(@Param("orderId") Long orderId);
}