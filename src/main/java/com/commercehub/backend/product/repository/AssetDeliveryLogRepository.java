package com.commercehub.backend.product.repository;

import com.commercehub.backend.product.entity.AssetDeliveryLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AssetDeliveryLogRepository extends JpaRepository<AssetDeliveryLog, Long> {

    List<AssetDeliveryLog> findByOrderItemId(Long orderItemId);
}