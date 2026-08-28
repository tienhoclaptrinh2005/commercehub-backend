package com.commercehub.backend.order.mapper;

import com.commercehub.backend.order.dto.response.OrderDetailResponse;
import com.commercehub.backend.order.dto.response.OrderItemResponse;
import com.commercehub.backend.order.dto.response.OrderResponse;
import com.commercehub.backend.order.dto.response.OrderStatusLogResponse;
import com.commercehub.backend.order.entity.Order;
import com.commercehub.backend.order.entity.OrderItem;
import com.commercehub.backend.order.entity.OrderStatusLog;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OrderMapper {

    @Mapping(target = "shopId", source = "shop.id")
    @Mapping(target = "shopName", source = "shop.name")
    @Mapping(target = "sellerUsername", source = "shop.owner.username")
    @Mapping(target = "effectiveStatus", source = "status")
    OrderResponse toOrderResponse(Order order);

    @Mapping(target = "shopId", source = "shop.id")
    @Mapping(target = "shopName", source = "shop.name")
    @Mapping(target = "sellerUsername", source = "shop.owner.username")
    @Mapping(target = "effectiveStatus", source = "status")
    OrderDetailResponse toOrderDetailResponse(Order order);

    // OrderItem → OrderItemResponse
    OrderItemResponse toOrderItemResponse(OrderItem item);


    OrderStatusLogResponse toStatusLogResponse(OrderStatusLog log);
}
