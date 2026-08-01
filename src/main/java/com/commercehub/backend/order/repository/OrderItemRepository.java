package com.commercehub.backend.order.repository;

import com.commercehub.backend.order.entity.Order;
import com.commercehub.backend.order.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findByOrderId(Long orderId);
    List<OrderItem> findByOrder(Order order);
}