package com.commercehub.backend.order.repository;

import com.commercehub.backend.order.entity.PreOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PreOrderItemRepository extends JpaRepository<PreOrderItem, Long> {
    Optional<PreOrderItem> findByOrderItemId(Long orderItemId);

    List<PreOrderItem> findByOrderItemIdIn(Collection<Long> orderItemIds);
}
