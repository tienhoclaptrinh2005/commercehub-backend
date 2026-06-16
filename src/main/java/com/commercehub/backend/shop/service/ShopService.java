package com.commercehub.backend.shop.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.common.util.SlugUtils;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShopService {

    private final ShopRepository shopRepository;
    private final ShopMapper shopMapper;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    @Transactional(readOnly = true)
    public Page<ShopResponse> getAllActiveShops(int page, int size) {
        int validPage = Math.max(0, page);
        int validSize = (size <= 0 || size > 100) ? 20 : size;
        Pageable pageable = PageRequest.of(validPage, validSize, Sort.by("createdAt").descending());
        Page<Shop> shopPage = shopRepository.findByStatus("ACTIVE", pageable);
        return shopPage.map(shopMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public ShopResponse getShopBySlug(String slug) {
        Shop shop = shopRepository.findBySlug(slug)
                .orElseThrow(() -> new AppException(ErrorCode.SHOP_NOT_FOUND));
        if (!"ACTIVE".equals(shop.getStatus())) {
            throw new AppException(ErrorCode.SHOP_NOT_FOUND);
        }
        return shopMapper.toResponse(shop);
    }

    @Transactional
    public ShopResponse createShop(CreateShopRequest request, Long ownerId){
        if (shopRepository.existsByName(request.getName())) {
            throw new AppException(ErrorCode.SHOP_ALREADY_EXISTS);
        }
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        int allowedCount = owner.getUserLevel().getAllowedShopCount();
        if (allowedCount <= 0) {
            throw new AppException(ErrorCode.SHOP_CREATION_NOT_ALLOWED);
        }
        long currentShopCount = shopRepository.countByOwnerId(ownerId);

        if (currentShopCount >= allowedCount) {
            throw new AppException(ErrorCode.SHOP_LIMIT_REACHED);
        }

        Shop shop = shopMapper.toEntity(request);
        shop.setOwner(owner);

        String generatedSlug = SlugUtils.toSlug(request.getName());
        if (shopRepository.existsBySlug(generatedSlug)) {
            generatedSlug = generatedSlug + "-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        }
        shop.setSlug(generatedSlug);

        Shop savedShop = shopRepository.save(shop);

        Role sellerRole = roleRepository.findByName("SELLER")
                .orElseThrow(() -> {
                    log.error("CRITICAL ERROR: Không tìm thấy quyền 'SELLER' trong bảng Roles!");
                    return new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
                });
        if (!owner.getRoles().contains(sellerRole)) {
            owner.getRoles().add(sellerRole);
            userRepository.save(owner);
        }
        return shopMapper.toResponse(savedShop);

    }

    @Transactional
    public ShopResponse updateShop(Long id, UpdateShopRequest request, Long currentUserId) {
        Shop shop = shopRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.SHOP_NOT_FOUND));

        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        boolean isAdmin = currentUser.getRoles().stream()
                .anyMatch(role -> "ADMIN".equals(role.getName()));

        if (!shop.getOwner().getId().equals(currentUserId) && !isAdmin) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

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

        shopMapper.updateEntityFromRequest(request, shop);
        return shopMapper.toResponse(shopRepository.save(shop));
    }


    //LUỒNG DÀNH CHO ADMIN (TRANG QUẢN TRỊ)


    @Transactional(readOnly = true)
    public Page<ShopResponse> getAllShopsForAdmin(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Shop> shopPage = shopRepository.findAll(pageable);
        return shopPage.map(shopMapper::toResponse);
    }

    @Transactional
    public void changeShopStatus(Long shopId, String newStatus) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new AppException(ErrorCode.SHOP_NOT_FOUND));

        List<String> allowedStatuses = Arrays.asList("ACTIVE", "INACTIVE", "BANNED");

        if (newStatus == null || !allowedStatuses.contains(newStatus.trim().toUpperCase())) {

            throw new AppException(ErrorCode.INVALID_STATUS);
        }

        shop.setStatus(newStatus.trim().toUpperCase());
        shopRepository.save(shop);
    }
}