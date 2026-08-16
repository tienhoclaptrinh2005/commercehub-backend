package com.commercehub.backend.shop.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.common.util.SlugUtils;
import com.commercehub.backend.order.service.OrderStatisticsService;
import com.commercehub.backend.shop.dto.request.CreateShopRequest;
import com.commercehub.backend.shop.dto.request.UpdateShopRequest;
import com.commercehub.backend.shop.dto.response.ShopResponse;
import com.commercehub.backend.shop.entity.Shop;
import com.commercehub.backend.shop.mapper.ShopMapper;
import com.commercehub.backend.shop.repository.ShopRepository;
import com.commercehub.backend.user.entity.Role;
import com.commercehub.backend.user.entity.User;
import com.commercehub.backend.user.repository.RoleRepository;
import com.commercehub.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShopService {

    private final ShopRepository shopRepository;
    private final ShopMapper shopMapper;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final OrderStatisticsService orderStatisticsService;

    @Transactional(readOnly = true)
    public Page<ShopResponse> getAllActiveShops(int page, int size) {
        int validPage = Math.max(0, page);
        int validSize = (size <= 0 || size > 100) ? 20 : size;
        Pageable pageable = PageRequest.of(validPage, validSize, Sort.by("createdAt").descending());
        Page<Shop> shopPage = shopRepository.findAllPublicActive(pageable);
        return shopPage.map(shopMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public ShopResponse getShopBySlug(String slug) {
        Shop shop = shopRepository.findBySlug(slug)
                .orElseThrow(() -> new AppException(ErrorCode.SHOP_NOT_FOUND));
        if (!"ACTIVE".equals(shop.getStatus())
                || !"ACTIVE".equals(shop.getOwner().getStatus())) {
            throw new AppException(ErrorCode.SHOP_NOT_FOUND);
        }

        long ownerCompletedPurchaseCount = orderStatisticsService
                .countCompletedPurchases(shop.getOwner().getId());
        long successfulSaleCount = orderStatisticsService.countSuccessfulSales(shop.getId());

        return shopMapper.toResponse(shop, ownerCompletedPurchaseCount, successfulSaleCount);
    }

    @Transactional
    public ShopResponse createShop(CreateShopRequest request, Long ownerId) {
        if (shopRepository.existsByName(request.getName())) {
            throw new AppException(ErrorCode.SHOP_ALREADY_EXISTS);
        }
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (shopRepository.findByOwnerId(ownerId).isPresent()) {
            throw new AppException(ErrorCode.USER_ALREADY_HAS_SHOP);
        }

        Shop shop = shopMapper.toEntity(request);
        shop.setOwner(owner);

        String generatedSlug = SlugUtils.toSlug(request.getName());
        while (shopRepository.existsBySlug(generatedSlug)) {
            generatedSlug = SlugUtils.toSlug(request.getName()) + "-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        }
        shop.setSlug(generatedSlug);

        // Shop mới chỉ là hồ sơ chờ duyệt. BUYER chỉ được nâng thành SELLER
        // khi admin chuyển shop sang ACTIVE trong changeShopStatus().
        return shopMapper.toResponse(shopRepository.save(shop));
    }

    @CacheEvict(value = "shopByOwner", allEntries = true)
    @Transactional
    public ShopResponse updateShop(Long id, UpdateShopRequest request, Long currentUserId) {
        Shop shop = shopRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.SHOP_NOT_FOUND));

        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        boolean isAdmin = currentUser.getRoles().stream()
                .anyMatch(role -> List.of("ADMIN", "SUPER_ADMIN").contains(role.getName()));

        if (!shop.getOwner().getId().equals(currentUserId) && !isAdmin) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        if (!isAdmin && (!"ACTIVE".equals(shop.getStatus())
                || !"ACTIVE".equals(shop.getOwner().getStatus()))) {
            throw new AppException(ErrorCode.SHOP_UNAUTHORIZED);
        }

        shopMapper.updateEntityFromRequest(request, shop);

        if (request.getName() != null && !request.getName().equals(shop.getName())) {
            if (shopRepository.existsByName(request.getName())) {
                throw new AppException(ErrorCode.SHOP_ALREADY_EXISTS);
            }
            String newSlug = SlugUtils.toSlug(request.getName());
            if (shopRepository.existsBySlug(newSlug)) {
                throw new AppException(ErrorCode.SHOP_ALREADY_EXISTS);
            }
            shop.setName(request.getName());
            shop.setSlug(newSlug);
        }

        return shopMapper.toResponse(shopRepository.save(shop));
    }

    @Cacheable(value = "shopByOwner", key = "#ownerId")
    @Transactional(readOnly = true)
    public Shop getShopByOwnerId(Long ownerId) {
        return shopRepository.findByOwnerId(ownerId)
                .orElseThrow(() -> new AppException(ErrorCode.SHOP_NOT_FOUND));
    }

    // LUỒNG DÀNH CHO ADMIN (TRANG QUẢN TRỊ)

    @Transactional(readOnly = true)
    public Page<ShopResponse> getAllShopsForAdmin(int page, int size) {
        int validPage = Math.max(0, page);
        int validSize = (size <= 0 || size > 100) ? 20 : size;
        Pageable pageable = PageRequest.of(validPage, validSize, Sort.by("createdAt").descending());
        Page<Shop> shopPage = shopRepository.findAll(pageable);
        return shopPage.map(shopMapper::toResponse);
    }

    @CacheEvict(value = "shopByOwner", allEntries = true)
    @Transactional
    public void changeShopStatus(Long shopId, String newStatus) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new AppException(ErrorCode.SHOP_NOT_FOUND));

        List<String> allowedStatuses = List.of(
                "PENDING", "ACTIVE", "REJECTED", "INACTIVE", "SUSPENDED", "BANNED", "CLOSED"
        );

        if (newStatus == null || !allowedStatuses.contains(newStatus.trim().toUpperCase(Locale.ROOT))) {
            throw new AppException(ErrorCode.INVALID_STATUS);
        }

        String normalizedStatus = newStatus.trim().toUpperCase(Locale.ROOT);

        if ("ACTIVE".equals(normalizedStatus)) {
            promoteOwnerForApprovedShop(shop.getOwner());
        }

        shop.setStatus(normalizedStatus);
        shopRepository.save(shop);
    }

    /**
     * Mỗi user chỉ giữ một role. Khi shop được duyệt, BUYER trở thành SELLER.
     * ADMIN và SUPER_ADMIN giữ nguyên role vì role hierarchy đã bao gồm quyền SELLER/BUYER.
     */
    private void promoteOwnerForApprovedShop(User owner) {
        Role highestRole = owner.getRoles().stream()
                .max(java.util.Comparator.comparingInt(role -> rolePriority(role.getName())))
                .orElseThrow(() -> new AppException(ErrorCode.SYSTEM_CONFIG_ERROR));

        if ("BUYER".equals(highestRole.getName())) {
            highestRole = roleRepository.findByName("SELLER")
                    .orElseThrow(() -> {
                        log.error("CRITICAL ERROR: Không tìm thấy quyền 'SELLER' trong bảng Roles!");
                        return new AppException(ErrorCode.SYSTEM_CONFIG_ERROR);
                    });
        }

        owner.getRoles().clear();
        owner.getRoles().add(highestRole);
        userRepository.save(owner);
    }

    private int rolePriority(String roleName) {
        return switch (roleName) {
            case "SUPER_ADMIN" -> 4;
            case "ADMIN" -> 3;
            case "SELLER" -> 2;
            case "BUYER" -> 1;
            default -> 0;
        };
    }
}
