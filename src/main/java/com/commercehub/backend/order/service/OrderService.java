package com.commercehub.backend.order.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.order.dto.response.OrderDetailResponse;
import com.commercehub.backend.order.dto.response.CheckoutOrderResponse;
import com.commercehub.backend.order.dto.response.OrderItemResponse;
import com.commercehub.backend.order.dto.response.OrderResponse;
import com.commercehub.backend.order.dto.response.PreOrderItemResponse;
import com.commercehub.backend.order.entity.Order;
import com.commercehub.backend.order.entity.OrderItem;
import com.commercehub.backend.order.mapper.OrderMapper;
import com.commercehub.backend.order.repository.OrderItemRepository;
import com.commercehub.backend.order.repository.OrderRepository;
import com.commercehub.backend.order.repository.OrderStatusLogRepository;
import com.commercehub.backend.order.repository.PreOrderItemRepository;
import com.commercehub.backend.dispute.entity.OrderDispute;
import com.commercehub.backend.dispute.repository.OrderDisputeRepository;
import com.commercehub.backend.wallet.entity.HoldRelease;
import com.commercehub.backend.wallet.repository.HoldReleaseRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final int MAX_ORDER_PAGE_SIZE = 50;
    private static final ZoneOffset BUSINESS_TIMEZONE_OFFSET = ZoneOffset.ofHours(7);
    private static final OffsetDateTime MIN_FILTER_TIME =
            LocalDate.of(1970, 1, 1).atStartOfDay().atOffset(BUSINESS_TIMEZONE_OFFSET);
    private static final OffsetDateTime MAX_FILTER_TIME =
            LocalDate.of(9999, 12, 31).atStartOfDay().atOffset(BUSINESS_TIMEZONE_OFFSET);
    private static final Set<String> ACTIVE_DISPUTE_STATUSES = Set.of(
            OrderDispute.STATUS_OPEN,
            OrderDispute.STATUS_WARRANTY_IN_PROGRESS,
            OrderDispute.STATUS_WAITING_BUYER_CONFIRMATION,
            OrderDispute.STATUS_PROCESSING
    );
    private static final Set<String> BUYER_ORDER_FILTER_STATUSES = Set.of(
            "WAITING_APPROVAL",
            "PROCESSING",
            "DELIVERED",
            "DISPUTED",
            "REFUNDED",
            "REJECTED",
            "CANCELLED",
            "CANCELLED_BY_SELLER",
            "CANCELLED_BY_SYSTEM",
            "PENDING",
            "APPROVED"
    );
    private static final Set<String> SELLER_ORDER_FILTER_STATUSES = Set.of(
            "WAITING_APPROVAL",
            "PROCESSING",
            "DELIVERED",
            "DISPUTED",
            "REJECTED",
            "CANCELLED",
            "CANCELLED_BY_SELLER",
            "CANCELLED_BY_SYSTEM"
    );
    private static final Set<String> ORDER_DELIVERY_TYPES = Set.of("INSTANT", "PRE_ORDER");

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PreOrderItemRepository preOrderItemRepository;
    private final OrderStatusLogRepository orderStatusLogRepository;
    private final HoldReleaseRepository holdReleaseRepository;
    private final OrderDisputeRepository orderDisputeRepository;
    private final OrderStatusService orderStatusService;
    private final OrderMapper orderMapper;

    @Transactional(readOnly = true)
    public Slice<OrderResponse> getBuyerOrders(
            Long buyerId,
            String orderCode,
            String status,
            LocalDate fromDate,
            LocalDate toDate,
            OffsetDateTime beforePlacedAt,
            Long beforeId,
            int size
    ) {
        String normalizedOrderCode = orderCode == null ? "" : orderCode.trim();
        String normalizedStatus = status == null ? "" : status.trim().toUpperCase();
        if (!normalizedStatus.isEmpty() && !BUYER_ORDER_FILTER_STATUSES.contains(normalizedStatus)) {
            throw new AppException(ErrorCode.INVALID_STATUS);
        }
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        if ((beforePlacedAt == null) != (beforeId == null)
                || size < 1
                || size > MAX_ORDER_PAGE_SIZE) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }

        OffsetDateTime fromDateTime = fromDate == null
                ? MIN_FILTER_TIME
                : fromDate.atStartOfDay().atOffset(BUSINESS_TIMEZONE_OFFSET);
        OffsetDateTime toDateTimeExclusive = toDate == null
                ? MAX_FILTER_TIME
                : toDate.plusDays(1).atStartOfDay().atOffset(BUSINESS_TIMEZONE_OFFSET);

        PageRequest pageRequest = PageRequest.of(0, size);
        Slice<Order> orders = beforePlacedAt == null
                ? orderRepository.findFirstBuyerOrders(
                        buyerId,
                        normalizedOrderCode,
                        normalizedStatus,
                        fromDateTime,
                        toDateTimeExclusive,
                        ACTIVE_DISPUTE_STATUSES,
                        pageRequest
                )
                : orderRepository.findBuyerOrdersBefore(
                        buyerId,
                        normalizedOrderCode,
                        normalizedStatus,
                        fromDateTime,
                        toDateTimeExclusive,
                        beforePlacedAt,
                        beforeId,
                        ACTIVE_DISPUTE_STATUSES,
                        pageRequest
                );
        return mapBuyerOrdersWithEffectiveStatus(orders);
    }

    @Transactional(readOnly = true)
    public List<CheckoutOrderResponse> getBuyerCheckoutOrders(Long buyerId, List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return List.of();
        }

        Map<Long, Order> ownedOrders = orderRepository
                .findCheckoutOrdersForBuyer(buyerId, orderIds)
                .stream()
                .collect(Collectors.toMap(
                        Order::getId,
                        Function.identity(),
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));

        if (ownedOrders.size() != orderIds.stream().distinct().count()) {
            throw new AppException(ErrorCode.RECORD_NOT_FOUND);
        }

        return orderIds.stream()
                .map(ownedOrders::get)
                .map(order -> new CheckoutOrderResponse(order.getOrderCode()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Order getBuyerOrderOrThrow(Long buyerId, String orderCode) {
        String normalizedOrderCode = normalizeOrderCode(orderCode);
        return orderRepository.findByOrderCodeAndUserId(normalizedOrderCode, buyerId)
                .orElseThrow(() -> new AppException(ErrorCode.RECORD_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public OrderDetailResponse getBuyerOrderDetail(Long buyerId, String orderCode) {
        return buildOrderDetail(getBuyerOrderOrThrow(buyerId, orderCode));
    }

    @Transactional(readOnly = true)
    public Slice<OrderResponse> getSellerOrders(
            Long shopId,
            String search,
            String deliveryType,
            String status,
            LocalDate fromDate,
            LocalDate toDate,
            OffsetDateTime beforePlacedAt,
            Long beforeId,
            int size
    ) {
        String normalizedSearch = search == null ? "" : search.trim();
        String normalizedDeliveryType = deliveryType == null ? "" : deliveryType.trim().toUpperCase();
        String normalizedStatus = status == null ? "" : status.trim().toUpperCase();
        if (!normalizedDeliveryType.isEmpty() && !ORDER_DELIVERY_TYPES.contains(normalizedDeliveryType)) {
            throw new AppException(ErrorCode.INVALID_DELIVERY_TYPE);
        }
        if (!normalizedStatus.isEmpty() && !SELLER_ORDER_FILTER_STATUSES.contains(normalizedStatus)) {
            throw new AppException(ErrorCode.INVALID_STATUS);
        }
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        if ((beforePlacedAt == null) != (beforeId == null)
                || size < 1
                || size > MAX_ORDER_PAGE_SIZE) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }

        OffsetDateTime fromDateTime = fromDate == null
                ? MIN_FILTER_TIME
                : fromDate.atStartOfDay().atOffset(BUSINESS_TIMEZONE_OFFSET);
        OffsetDateTime toDateTimeExclusive = toDate == null
                ? MAX_FILTER_TIME
                : toDate.plusDays(1).atStartOfDay().atOffset(BUSINESS_TIMEZONE_OFFSET);
        PageRequest pageRequest = PageRequest.of(0, size);
        Slice<Order> orders = beforePlacedAt == null
                ? orderRepository.findFirstSellerOrders(
                        shopId, normalizedSearch, normalizedDeliveryType, normalizedStatus,
                        fromDateTime, toDateTimeExclusive, ACTIVE_DISPUTE_STATUSES, pageRequest
                )
                : orderRepository.findSellerOrdersBefore(
                        shopId, normalizedSearch, normalizedDeliveryType, normalizedStatus,
                        fromDateTime, toDateTimeExclusive, beforePlacedAt, beforeId,
                        ACTIVE_DISPUTE_STATUSES, pageRequest
                );
        return mapOrdersWithEffectiveStatus(orders);
    }

    @Transactional(readOnly = true)
    public OrderDetailResponse getSellerOrderDetail(Long shopId, Long orderId) {
        return buildOrderDetail(getSellerOrderOrThrow(shopId, orderId));
    }

    @Transactional(readOnly = true)
    public Order getSellerOrderOrThrow(Long shopId, Long orderId) {
        return orderRepository.findByIdAndShopId(orderId, shopId)
                .orElseThrow(() -> new AppException(ErrorCode.RECORD_NOT_FOUND));
    }



    private OrderDetailResponse buildOrderDetail(Order order) {
        List<OrderItem> orderItems = orderItemRepository.findByOrderId(order.getId());
        List<Long> orderItemIds = orderItems.stream().map(OrderItem::getId).toList();
        List<Long> preOrderItemIds = orderItems.stream()
                .filter(item -> "PRE_ORDER".equals(item.getDeliveryType()))
                .map(OrderItem::getId)
                .toList();
        Map<Long, com.commercehub.backend.order.entity.PreOrderItem> preOrderItems =
                preOrderItemIds.isEmpty()
                        ? Map.of()
                        : preOrderItemRepository.findByOrderItemIdIn(preOrderItemIds).stream()
                                .collect(Collectors.toMap(
                                        preItem -> preItem.getOrderItem().getId(),
                                        Function.identity()
                                ));
        Map<Long, HoldRelease> holdReleases = orderItemIds.isEmpty()
                ? Map.of()
                : holdReleaseRepository.findByOrderItemIdIn(orderItemIds).stream()
                        .collect(Collectors.toMap(HoldRelease::getOrderItemId, Function.identity()));
        Map<Long, OrderDispute> disputes = orderItemIds.isEmpty()
                ? Map.of()
                : orderDisputeRepository.findByOrderItemIdIn(orderItemIds).stream()
                        .collect(Collectors.toMap(OrderDispute::getOrderItemId, Function.identity()));
        OffsetDateTime now = OffsetDateTime.now();

        var items = orderItems.stream()
                .map(item -> {
                    OrderItemResponse itemResponse = orderMapper.toOrderItemResponse(item);
                    HoldRelease holdRelease = holdReleases.get(item.getId());
                    OrderDispute dispute = disputes.get(item.getId());
                    itemResponse.setComplaintDeadlineAt(
                            holdRelease != null ? holdRelease.getScheduledReleaseAt() : null
                    );
                    itemResponse.setDisputeId(dispute != null ? dispute.getId() : null);
                    itemResponse.setComplaintAllowed(
                            dispute == null
                                    && holdRelease != null
                                    && "HOLDING".equals(holdRelease.getStatus())
                                    && holdRelease.getScheduledReleaseAt() != null
                                    && holdRelease.getScheduledReleaseAt().isAfter(now)
                    );
                    // Item PRE_ORDER: gắn trạng thái xử lý + nội dung shop đã giao.
                    // deliveryContent chỉ trả trong chi tiết đơn (buyer sở hữu / shop bán),
                    // không bao giờ xuất hiện trong API danh sách.
                    if ("PRE_ORDER".equals(item.getDeliveryType())) {
                        var preItem = preOrderItems.get(item.getId());
                        if (preItem != null) {
                            itemResponse.setPreOrder(PreOrderItemResponse.builder()
                                    .status(preItem.getStatus())
                                    .buyerInputs(preItem.getBuyerInputs())
                                    .deliveryContentType(preItem.getDeliveryContentType() != null
                                            ? preItem.getDeliveryContentType().name() : null)
                                    .deliveryContent(preItem.getDeliveryContent())
                                    .acceptedAt(preItem.getAcceptedAt())
                                    .deliveredAt(preItem.getDeliveredAt())
                                    .completedAt(preItem.getCompletedAt())
                                    .build());
                        }
                    }
                    return itemResponse;
                })
                .collect(Collectors.toList());

        OrderDetailResponse response = orderMapper.toOrderDetailResponse(order);
        if (disputes.values().stream().anyMatch(this::isActiveDispute)) {
            response.setEffectiveStatus("DISPUTED");
        }
        response.setItems(items);
        response.setStatusLogs(
                orderStatusLogRepository.findByOrderIdOrderByCreatedAtDesc(order.getId()).stream()
                        .map(orderMapper::toStatusLogResponse)
                        .toList()
        );

        return response;
    }

    private boolean isActiveDispute(OrderDispute dispute) {
        return dispute != null && ACTIVE_DISPUTE_STATUSES.contains(dispute.getStatus());
    }

    private String normalizeOrderCode(String orderCode) {
        if (orderCode == null || orderCode.isBlank() || orderCode.length() > 50) {
            throw new AppException(ErrorCode.RECORD_NOT_FOUND);
        }
        return orderCode.trim();
    }

    private Slice<OrderResponse> mapOrdersWithEffectiveStatus(Slice<Order> orders) {
        List<Long> orderIds = orders.getContent().stream().map(Order::getId).toList();
        Set<Long> disputedOrderIds = orderIds.isEmpty()
                ? Set.of()
                : orderDisputeRepository.findOrderIdsWithStatuses(orderIds, ACTIVE_DISPUTE_STATUSES);
        List<OrderItem> orderItems = orderIds.isEmpty()
                ? List.of()
                : orderItemRepository.findByOrderIdIn(orderIds);
        Map<Long, List<String>> productNamesByOrder = groupUniqueItemNames(
                orderItems,
                OrderItem::getProductName,
                "Sản phẩm"
        );
        Map<Long, List<String>> variantNamesByOrder = groupUniqueItemNames(
                orderItems,
                OrderItem::getVariantName,
                "Mặc định"
        );

        return orders.map(order -> {
            OrderResponse response = orderMapper.toOrderResponse(order);
            response.setProductNames(productNamesByOrder.getOrDefault(order.getId(), List.of()));
            response.setVariantNames(variantNamesByOrder.getOrDefault(order.getId(), List.of()));
            if (disputedOrderIds.contains(order.getId())) {
                response.setEffectiveStatus("DISPUTED");
            }
            return response;
        });
    }

    private Map<Long, List<String>> groupUniqueItemNames(
            List<OrderItem> orderItems,
            Function<OrderItem, String> nameExtractor,
            String fallback
    ) {
        return orderItems.stream()
                .collect(Collectors.groupingBy(
                        item -> item.getOrder().getId(),
                        LinkedHashMap::new,
                        Collectors.mapping(
                                item -> {
                                    String name = nameExtractor.apply(item);
                                    return name == null || name.isBlank() ? fallback : name;
                                },
                                Collectors.collectingAndThen(
                                        Collectors.toCollection(java.util.LinkedHashSet::new),
                                        List::copyOf
                                )
                        )
                ));
    }

    /**
     * Lịch sử mua hàng dùng Slice để không phát sinh câu COUNT(*) trên toàn bộ
     * tập kết quả. Spring Data chỉ lấy thêm một bản ghi để xác định còn trang sau.
     */
    private Slice<OrderResponse> mapBuyerOrdersWithEffectiveStatus(Slice<Order> orders) {
        return mapOrdersWithEffectiveStatus(orders);
    }
}
