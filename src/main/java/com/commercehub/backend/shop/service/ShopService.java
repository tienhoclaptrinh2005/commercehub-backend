package com.commercehub.backend.shop.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.common.cache.CacheNames;
import com.commercehub.backend.common.response.PageResponse;
import com.commercehub.backend.common.util.SlugUtils;
import com.commercehub.backend.order.service.OrderStatisticsService;
import com.commercehub.backend.product.repository.ProductRepository;
import com.commercehub.backend.shop.dto.request.CreateShopRequest;
import com.commercehub.backend.shop.dto.request.UpdateShopRequest;
import com.commercehub.backend.shop.dto.response.ShopResponse;
import com.commercehub.backend.shop.dto.response.ShopApplicationResponse;
import com.commercehub.backend.shop.entity.Shop;
import com.commercehub.backend.shop.mapper.ShopMapper;
import com.commercehub.backend.shop.repository.ShopRepository;
import com.commercehub.backend.storage.service.MediaUrlService;
import com.commercehub.backend.user.entity.Role;
import com.commercehub.backend.user.entity.User;
import com.commercehub.backend.user.repository.RoleRepository;
import com.commercehub.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.time.OffsetDateTime;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShopService {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-z0-9_.]{3,100}$");

    private final ShopRepository shopRepository;
    private final ShopMapper shopMapper;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final OrderStatisticsService orderStatisticsService;
    private final ProductRepository productRepository;
    private final MediaUrlService mediaUrlService;

    @Transactional(readOnly = true)
    @Cacheable(
            cacheNames = CacheNames.PUBLIC_SHOPS,
            key = "T(com.commercehub.backend.common.cache.PublicCacheKeys).publicShops(#page, #size, #keyword, #categoryId, #sort)"
    )
    public PageResponse<ShopResponse> getAllActiveShops(
            int page,
            int size,
            String keyword,
            Long categoryId,
            String sort
    ) {
        int validPage = Math.max(0, page);
        int validSize = (size <= 0 || size > 100) ? 8 : size;
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        if (normalizedKeyword.length() > 100) {
            normalizedKeyword = normalizedKeyword.substring(0, 100);
        }

        Pageable pageable = PageRequest.of(validPage, validSize, publicShopSort(sort));
        Page<Shop> shopPage = shopRepository.findAllPublicActive(
                normalizedKeyword,
                categoryId,
                pageable
        );

        List<Long> shopIds = shopPage.getContent().stream()
                .map(Shop::getId)
                .toList();
        Map<Long, ProductRepository.ShopProductStats> statsByShopId = shopIds.isEmpty()
                ? Map.of()
                : productRepository.findPublicShopProductStats(shopIds).stream()
                        .collect(Collectors.toMap(
                                ProductRepository.ShopProductStats::getShopId,
                                Function.identity()
                        ));

        Page<ShopResponse> mappedPage = shopPage.map(shop -> {
            ShopResponse response = shopMapper.toResponse(shop);
            resolveMediaUrls(response);
            ProductRepository.ShopProductStats stats = statsByShopId.get(shop.getId());
            response.setActiveProductCount(stats == null ? 0L : stats.getActiveProductCount());
            response.setSoldProductCount(stats == null ? 0L : stats.getSoldProductCount());
            return response;
        });
        return PageResponse.of(mappedPage);
    }

    @Transactional(readOnly = true)
    public ShopResponse getShopBySlug(String slug) {
        Shop shop = shopRepository.findBySlug(slug)
                .orElseThrow(() -> new AppException(ErrorCode.SHOP_NOT_FOUND));
        if (!"ACTIVE".equals(shop.getStatus())
                || !"ACTIVE".equals(shop.getOwner().getStatus())
                || !shop.getOwner().hasRole("SELLER")) {
            throw new AppException(ErrorCode.SHOP_NOT_FOUND);
        }

        long ownerCompletedPurchaseCount = orderStatisticsService
                .countCompletedPurchases(shop.getOwner().getId());
        long successfulSaleCount = orderStatisticsService.countSuccessfulSales(shop.getId());

        ShopResponse response = shopMapper.toResponse(
                shop,
                ownerCompletedPurchaseCount,
                successfulSaleCount
        );
        resolveMediaUrls(response);
        return response;
    }

    @Transactional
    public ShopApplicationResponse createShop(CreateShopRequest request, Long ownerId) {
        // Khóa user để hai request đăng ký đồng thời không thể cùng vượt qua
        // điều kiện "một user - một shop" hoặc cùng tiêu thụ lượt đổi username.
        User owner = userRepository.findByIdForUsernameUpdate(ownerId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (owner.getRoles().size() != 1 || !owner.hasRole("BUYER")) {
            throw new AppException(ErrorCode.SHOP_CREATION_ROLE_NOT_ALLOWED);
        }

        if (shopRepository.findByOwnerId(ownerId).isPresent()) {
            throw new AppException(ErrorCode.USER_ALREADY_HAS_SHOP);
        }

        String normalizedName = request.getName().trim();
        if (shopRepository.existsByNameIgnoreCase(normalizedName)) {
            throw new AppException(ErrorCode.SHOP_ALREADY_EXISTS);
        }

        lockRegistrationUsername(owner, request.getUsername());

        Shop shop = shopMapper.toEntity(request);
        shop.setOwner(owner);
        shop.setName(normalizedName);
        shop.setContactInfo(request.getContactInfo().trim());
        shop.setApplicationReason(trimToNull(request.getApplicationReason()));

        String generatedSlug = SlugUtils.toSlug(normalizedName);
        while (shopRepository.existsBySlug(generatedSlug)) {
            generatedSlug = SlugUtils.toSlug(normalizedName) + "-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        }
        shop.setSlug(generatedSlug);

        // Shop mới chỉ là hồ sơ chờ duyệt. BUYER chỉ được nâng thành SELLER
        // khi admin chuyển shop sang ACTIVE trong changeShopStatus().
        try {
            return shopMapper.toApplicationResponse(shopRepository.saveAndFlush(shop));
        } catch (DataIntegrityViolationException exception) {
            throw new AppException(ErrorCode.SHOP_ALREADY_EXISTS);
        }
    }

    @Transactional(readOnly = true)
    public ShopApplicationResponse getMyShopApplication(Long ownerId) {
        Shop shop = shopRepository.findByOwnerId(ownerId)
                .orElseThrow(() -> new AppException(ErrorCode.SHOP_NOT_FOUND));
        return shopMapper.toApplicationResponse(shop);
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.PUBLIC_SHOPS, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PUBLIC_PRODUCT_PAGES, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.BEST_SELLING_PRODUCTS, allEntries = true)
    })
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

        if (request.getName() != null && !request.getName().equals(shop.getName())) {
            throw new AppException(ErrorCode.SELLER_IDENTITY_LOCKED);
        }

        shopMapper.updateEntityFromRequest(request, shop);

        ShopResponse response = shopMapper.toResponse(shopRepository.save(shop));
        resolveMediaUrls(response);
        return response;
    }

    @Transactional(readOnly = true)
    public Shop getShopByOwnerId(Long ownerId) {
        return shopRepository.findByOwnerId(ownerId)
                .orElseThrow(() -> new AppException(ErrorCode.SHOP_NOT_FOUND));
    }

    // LUỒNG DÀNH CHO ADMIN (TRANG QUẢN TRỊ)

    @Transactional(readOnly = true)
    public Page<ShopApplicationResponse> getAllShopsForAdmin(int page, int size) {
        int validPage = Math.max(0, page);
        int validSize = (size <= 0 || size > 100) ? 20 : size;
        Pageable pageable = PageRequest.of(validPage, validSize, Sort.by("createdAt").descending());
        Page<Shop> shopPage = shopRepository.findAll(pageable);
        return shopPage.map(shopMapper::toApplicationResponse);
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.PUBLIC_SHOPS, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PUBLIC_PRODUCT_PAGES, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.BEST_SELLING_PRODUCTS, allEntries = true)
    })
    @Transactional
    public void changeShopStatus(Long shopId, String newStatus) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new AppException(ErrorCode.SHOP_NOT_FOUND));

        List<String> allowedStatuses = List.of(
                "PENDING", "ACTIVE", "REJECTED", "BANNED"
        );

        if (newStatus == null || !allowedStatuses.contains(newStatus.trim().toUpperCase(Locale.ROOT))) {
            throw new AppException(ErrorCode.INVALID_STATUS);
        }

        String normalizedStatus = newStatus.trim().toUpperCase(Locale.ROOT);

        if (!isAllowedTransition(shop.getStatus(), normalizedStatus)) {
            throw new AppException(ErrorCode.SHOP_STATUS_TRANSITION_INVALID);
        }

        shop.setStatus(normalizedStatus);
        try {
            // Flush phiên bản shop trước khi thay đổi role. Request duyệt thua
            // sẽ dừng tại đây và không tạo tác dụng phụ lên tài khoản owner.
            shopRepository.saveAndFlush(shop);
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw new AppException(ErrorCode.SHOP_REVIEW_CONFLICT);
        }

        if ("ACTIVE".equals(normalizedStatus)) {
            promoteOwnerForApprovedShop(shop.getOwner(), shop.getName());
        }
    }

    /**
     * Mỗi user chỉ giữ một role. Khi shop được duyệt, BUYER trở thành SELLER.
     * Shop của SELLER có thể được kích hoạt lại; ADMIN/SUPER_ADMIN bị từ chối.
     */
    private void resolveMediaUrls(ShopResponse response) {
        response.setShopAvatarUrl(mediaUrlService.toPublicUrl(response.getShopAvatarUrl()));
        response.setShopCoverUrl(mediaUrlService.toPublicUrl(response.getShopCoverUrl()));
    }

    private void promoteOwnerForApprovedShop(User owner, String approvedShopName) {
        if (owner.getRoles().size() != 1) {
            throw new AppException(ErrorCode.SYSTEM_CONFIG_ERROR);
        }

        Role currentRole = owner.getRoles().stream()
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.SYSTEM_CONFIG_ERROR));

        if ("ADMIN".equals(currentRole.getName())
                || "SUPER_ADMIN".equals(currentRole.getName())) {
            throw new AppException(ErrorCode.SHOP_CREATION_ROLE_NOT_ALLOWED);
        }

        if ("SELLER".equals(currentRole.getName())) {
            owner.setFullName(approvedShopName);
            userRepository.save(owner);
            return;
        }

        if ("BUYER".equals(currentRole.getName())) {
            Role sellerRole = roleRepository.findByName("SELLER")
                    .orElseThrow(() -> {
                        log.error("CRITICAL ERROR: Không tìm thấy quyền 'SELLER' trong bảng Roles!");
                        return new AppException(ErrorCode.SYSTEM_CONFIG_ERROR);
                    });
            owner.getRoles().clear();
            owner.getRoles().add(sellerRole);
            owner.setFullName(approvedShopName);
            userRepository.save(owner);
            return;
        }

        throw new AppException(ErrorCode.SHOP_CREATION_ROLE_NOT_ALLOWED);
    }

    private void lockRegistrationUsername(User owner, String requestedUsername) {
        String normalizedUsername = requestedUsername == null
                ? ""
                : requestedUsername.trim().toLowerCase(Locale.ROOT);

        if (!USERNAME_PATTERN.matcher(normalizedUsername).matches()) {
            throw new AppException(ErrorCode.INVALID_USERNAME_FORMAT);
        }

        boolean unchanged = normalizedUsername.equals(owner.getUsername());
        if (owner.getUsernameChangedAt() != null) {
            if (!unchanged) {
                throw new AppException(ErrorCode.USERNAME_CHANGE_LIMIT_REACHED);
            }
            return;
        }

        if (!unchanged && userRepository.existsByUsername(normalizedUsername)) {
            throw new AppException(ErrorCode.USERNAME_ALREADY_EXISTS);
        }

        owner.setUsername(normalizedUsername);
        // Gửi hồ sơ seller sẽ chốt username hiện tại làm định danh cuối cùng,
        // kể cả khi buyer giữ nguyên username được sinh lúc đăng ký.
        owner.setUsernameChangedAt(OffsetDateTime.now());
        try {
            userRepository.saveAndFlush(owner);
        } catch (DataIntegrityViolationException exception) {
            throw new AppException(ErrorCode.USERNAME_ALREADY_EXISTS);
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private boolean isAllowedTransition(String currentStatus, String nextStatus) {
        if (currentStatus == null || currentStatus.equals(nextStatus)) {
            return false;
        }
        return switch (currentStatus) {
            case "PENDING" -> List.of("ACTIVE", "REJECTED").contains(nextStatus);
            case "ACTIVE" -> "BANNED".equals(nextStatus);
            case "BANNED" -> "ACTIVE".equals(nextStatus);
            // REJECTED là trạng thái kết thúc và không được gửi lại.
            case "REJECTED" -> false;
            default -> false;
        };
    }

    private Sort publicShopSort(String requestedSort) {
        String normalizedSort = requestedSort == null
                ? "trusted"
                : requestedSort.trim().toLowerCase(Locale.ROOT);

        return switch (normalizedSort) {
            case "newest" -> Sort.by(
                    Sort.Order.desc("createdAt"),
                    Sort.Order.desc("id")
            );
            case "name" -> Sort.by(
                    Sort.Order.asc("name"),
                    Sort.Order.asc("id")
            );
            default -> Sort.by(
                    Sort.Order.desc("ratingAvg"),
                    Sort.Order.desc("ratingCount"),
                    Sort.Order.asc("disputeRate"),
                    Sort.Order.asc("totalDisputes"),
                    Sort.Order.desc("createdAt"),
                    Sort.Order.desc("id")
            );
        };
    }
}
