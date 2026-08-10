package com.commercehub.backend.order.service;

import com.commercehub.backend.order.repository.OrderRepository;
import com.commercehub.backend.shop.repository.ShopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderStatisticsService {

    private final OrderRepository orderRepository;
    private final ShopRepository shopRepository;

    @Transactional(readOnly = true)
    public long countCompletedPurchases(Long userId) {
        return orderRepository.countCompletedPurchasesByUserId(userId);
    }

    @Transactional(readOnly = true)
    public long countSuccessfulSales(Long shopId) {
        return orderRepository.countSuccessfulSalesByShopId(shopId);
    }

    @Transactional(readOnly = true)
    public long countSuccessfulSalesByOwner(Long ownerId) {
        return shopRepository.findByOwnerId(ownerId)
                .map(shop -> countSuccessfulSales(shop.getId()))
                .orElse(0L);
    }
}
