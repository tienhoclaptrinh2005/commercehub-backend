package com.commercehub.backend.wallet.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.common.response.SliceResponse;
import com.commercehub.backend.order.entity.Order;
import com.commercehub.backend.order.entity.OrderItem;
import com.commercehub.backend.order.repository.OrderItemRepository;
import com.commercehub.backend.order.repository.OrderRepository;
import com.commercehub.backend.wallet.dto.response.WalletTransactionResponse;
import com.commercehub.backend.wallet.entity.Deposit;
import com.commercehub.backend.wallet.entity.Wallet;
import com.commercehub.backend.wallet.entity.WalletTransaction;
import com.commercehub.backend.wallet.mapper.WalletMapper;
import com.commercehub.backend.wallet.repository.DepositRepository;
import com.commercehub.backend.wallet.repository.WalletRepository;
import com.commercehub.backend.wallet.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WalletTransactionService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final Map<String, List<String>> CATEGORY_TYPES = Map.of(
            "DEPOSIT", List.of("DEPOSIT"),
            "PAYMENT", List.of("ORDER_PAYMENT"),
            "WITHDRAWAL", List.of("WITHDRAW_PENDING", "WITHDRAW_DONE", "WITHDRAW_CANCEL"),
            "REFUND", List.of("ORDER_REFUND", "DISPUTE_REFUND", "REFUND"),
            "SALE", List.of("SALE_HOLD", "HOLD_RELEASE", "HOLD_RELEASE_NET", "CANCEL_HOLD"),
            "FEE", List.of("PLATFORM_FEE"),
            "ADJUSTMENT", List.of("ADMIN_ADJUST")
    );
    private static final List<String> SELLER_TRANSACTION_TYPES = List.of(
            "SALE_HOLD",
            "HOLD_RELEASE",
            "HOLD_RELEASE_NET",
            "CANCEL_HOLD",
            "PLATFORM_FEE",
            "WITHDRAW_PENDING",
            "WITHDRAW_DONE",
            "WITHDRAW_CANCEL",
            "ADMIN_ADJUST"
    );
    private static final Map<String, List<String>> SELLER_CATEGORY_TYPES = Map.of(
            "SALE", List.of("SALE_HOLD", "HOLD_RELEASE", "HOLD_RELEASE_NET", "CANCEL_HOLD"),
            "WITHDRAWAL", List.of("WITHDRAW_PENDING", "WITHDRAW_DONE", "WITHDRAW_CANCEL"),
            "FEE", List.of("PLATFORM_FEE"),
            "ADJUSTMENT", List.of("ADMIN_ADJUST")
    );
    private static final Set<String> DIRECT_ORDER_TRANSACTION_TYPES = Set.of(
            "ORDER_PAYMENT", "ORDER_REFUND", "SALE_HOLD"
    );

    private final WalletTransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final WalletMapper walletMapper;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final DepositRepository depositRepository;

    @Transactional(readOnly = true)
    public SliceResponse<WalletTransactionResponse> getMyTransactions(
            Long userId,
            int page,
            int size,
            String category
    ) {
        return getTransactions(userId, page, size, category, CATEGORY_TYPES, null);
    }

    @Transactional(readOnly = true)
    public SliceResponse<WalletTransactionResponse> getSellerTransactions(
            Long userId,
            int page,
            int size,
            String category
    ) {
        return getTransactions(
                userId,
                page,
                size,
                category,
                SELLER_CATEGORY_TYPES,
                SELLER_TRANSACTION_TYPES
        );
    }

    private SliceResponse<WalletTransactionResponse> getTransactions(
            Long userId,
            int page,
            int size,
            String category,
            Map<String, List<String>> categoryTypes,
            List<String> allTransactionTypes
    ) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new AppException(ErrorCode.WALLET_NOT_FOUND));

        if (page < 1 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        String normalizedCategory = category == null ? "ALL" : category.trim().toUpperCase();
        if (!"ALL".equals(normalizedCategory) && !categoryTypes.containsKey(normalizedCategory)) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        List<String> transactionTypes = "ALL".equals(normalizedCategory)
                ? allTransactionTypes
                : categoryTypes.get(normalizedCategory);

        Pageable pageable = PageRequest.of(page - 1, size);
        Slice<WalletTransaction> transactionPage = findTransactions(
                wallet.getId(), transactionTypes, pageable);

        TransactionReferences references = loadReferences(transactionPage.getContent());
        Slice<WalletTransactionResponse> responsePage = transactionPage.map(transaction -> {
            WalletTransactionResponse response = walletMapper.toTransactionResponse(transaction);
            response.setReferenceId(transaction.getReferenceId());
            response.setReferenceType(transaction.getReferenceType());

            Order order = DIRECT_ORDER_TRANSACTION_TYPES.contains(transaction.getTransactionType())
                    ? references.orders().get(transaction.getReferenceId())
                    : null;
            if (order != null) {
                response.setReferenceCode(order.getOrderCode());
            } else if ("WITHDRAWAL".equals(transaction.getReferenceType())
                    && transaction.getReferenceId() != null) {
                response.setReferenceCode("WD-%06d".formatted(transaction.getReferenceId()));
            }
            response.setDescription(buildDescription(transaction, order, references));
            return response;
        });
        SliceResponse<WalletTransactionResponse> response = SliceResponse.of(responsePage);
        response.setPageNumbers(buildPageNumbers(
                wallet.getId(), transactionTypes, page, size, transactionPage));
        return response;
    }

    private Slice<WalletTransaction> findTransactions(
            Long walletId,
            List<String> transactionTypes,
            Pageable pageable
    ) {
        return transactionTypes == null
                ? transactionRepository.findByWalletIdOrderByCreatedAtDesc(walletId, pageable)
                : transactionRepository.findByWalletIdAndTransactionTypeInOrderByCreatedAtDesc(
                        walletId, transactionTypes, pageable);
    }

    /**
     * Trả tối đa ba số trang trong cùng một cụm mà không cần COUNT(*).
     * Ở đầu mỗi cụm chỉ đọc trước một Slice giới hạn để biết trang thứ ba có tồn tại.
     */
    private List<Integer> buildPageNumbers(
            Long walletId,
            List<String> transactionTypes,
            int page,
            int size,
            Slice<WalletTransaction> currentSlice
    ) {
        int groupStart = ((page - 1) / 3) * 3 + 1;
        int positionInGroup = page - groupStart;
        int pagesInGroup;

        if (positionInGroup == 0) {
            if (!currentSlice.hasNext()) {
                pagesInGroup = 1;
            } else {
                Slice<WalletTransaction> secondPage = findTransactions(
                        walletId,
                        transactionTypes,
                        PageRequest.of(page, size)
                );
                pagesInGroup = secondPage.hasNext() ? 3 : 2;
            }
        } else if (positionInGroup == 1) {
            pagesInGroup = currentSlice.hasNext() ? 3 : 2;
        } else {
            pagesInGroup = 3;
        }

        List<Integer> pageNumbers = new ArrayList<>(pagesInGroup);
        for (int offset = 0; offset < pagesInGroup; offset++) {
            pageNumbers.add(groupStart + offset);
        }
        return pageNumbers;
    }

    private TransactionReferences loadReferences(List<WalletTransaction> transactions) {
        List<Long> orderIds = transactions.stream()
                .filter(transaction -> DIRECT_ORDER_TRANSACTION_TYPES.contains(transaction.getTransactionType()))
                .map(WalletTransaction::getReferenceId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, Order> orders = orderIds.isEmpty()
                ? Map.of()
                : orderRepository.findAllById(orderIds).stream()
                        .collect(Collectors.toMap(Order::getId, Function.identity()));
        Map<Long, List<OrderItem>> orderItems = orderIds.isEmpty()
                ? Map.of()
                : orderItemRepository.findByOrderIdIn(orderIds).stream()
                        .collect(Collectors.groupingBy(
                                item -> item.getOrder().getId(),
                                LinkedHashMap::new,
                                Collectors.toList()
                        ));

        List<Long> depositIds = transactions.stream()
                .filter(transaction -> "DEPOSIT".equals(transaction.getTransactionType()))
                .map(WalletTransaction::getReferenceId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, Deposit> deposits = depositIds.isEmpty()
                ? Map.of()
                : depositRepository.findAllById(depositIds).stream()
                        .collect(Collectors.toMap(Deposit::getId, Function.identity()));

        return new TransactionReferences(orders, orderItems, deposits);
    }

    private String buildDescription(
            WalletTransaction transaction,
            Order order,
            TransactionReferences references
    ) {
        if (transaction.getDescription() != null && !transaction.getDescription().isBlank()) {
            return transaction.getDescription();
        }

        if (order != null) {
            String products = summarizeProducts(references.orderItems().getOrDefault(order.getId(), List.of()));
            String orderLabel = order.getOrderCode() == null ? "đơn #" + order.getId() : order.getOrderCode();
            return switch (transaction.getTransactionType()) {
                case "ORDER_PAYMENT" -> "Thanh toán " + orderLabel + products;
                case "ORDER_REFUND" -> "Hoàn tiền " + orderLabel + products;
                case "SALE_HOLD" -> "Doanh thu tạm giữ từ " + orderLabel + products;
                default -> transaction.getTransactionType();
            };
        }

        if ("DEPOSIT".equals(transaction.getTransactionType())) {
            Deposit deposit = references.deposits().get(transaction.getReferenceId());
            String provider = deposit == null || deposit.getProvider() == null
                    ? "cổng thanh toán"
                    : deposit.getProvider();
            return "Nạp tiền vào ví qua " + provider;
        }

        return switch (transaction.getTransactionType()) {
            case "DISPUTE_REFUND" -> "Hoàn tiền khiếu nại";
            case "HOLD_RELEASE" -> "Giải phóng tiền đang tạm giữ";
            case "HOLD_RELEASE_NET" -> "Doanh thu đã quyết toán";
            case "CANCEL_HOLD" -> "Hủy khoản tiền đang tạm giữ";
            case "WITHDRAW_PENDING" -> "Gửi yêu cầu rút tiền";
            case "WITHDRAW_DONE" -> "Yêu cầu rút tiền đã hoàn tất";
            case "WITHDRAW_CANCEL" -> "Hoàn lại yêu cầu rút tiền";
            case "REFUND" -> "Khoản tiền hoàn lại";
            case "PLATFORM_FEE" -> "Phí dịch vụ sàn";
            case "ADMIN_ADJUST" -> "Điều chỉnh số dư bởi quản trị viên";
            default -> transaction.getTransactionType().replace('_', ' ');
        };
    }

    private String summarizeProducts(List<OrderItem> items) {
        if (items.isEmpty()) {
            return "";
        }
        List<String> labels = new ArrayList<>();
        for (int index = 0; index < Math.min(items.size(), 2); index++) {
            OrderItem item = items.get(index);
            String variant = item.getVariantName() == null || item.getVariantName().isBlank()
                    ? ""
                    : " - " + item.getVariantName();
            labels.add(item.getProductName() + variant + " ×" + item.getQuantity());
        }
        String remaining = items.size() > 2 ? " và " + (items.size() - 2) + " sản phẩm khác" : "";
        return ": " + String.join(", ", labels) + remaining;
    }

    private record TransactionReferences(
            Map<Long, Order> orders,
            Map<Long, List<OrderItem>> orderItems,
            Map<Long, Deposit> deposits
    ) {
    }
}
