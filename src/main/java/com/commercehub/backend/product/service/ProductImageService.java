package com.commercehub.backend.product.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.product.dto.response.ProductImageResponse;
import com.commercehub.backend.product.entity.Product;
import com.commercehub.backend.product.entity.ProductImage;
import com.commercehub.backend.product.repository.ProductImageRepository;
import com.commercehub.backend.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductImageService {

    private final ProductImageRepository imageRepository;
    private final ProductRepository productRepository;

    @Transactional
    public List<ProductImageResponse> addImages(Long sellerId, Long productId, List<String> imageUrls) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        if (!product.getShop().getOwner().getId().equals(sellerId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        int currentMaxOrder = imageRepository.findByProductIdOrderBySortOrderAsc(productId)
                .stream()
                .mapToInt(ProductImage::getSortOrder)
                .max()
                .orElse(-1);

        final int[] orderCounter = {currentMaxOrder + 1};
        List<ProductImage> newImages = imageUrls.stream().map(url -> {
            return ProductImage.builder()
                    .product(product)
                    .imageUrl(url)
                    .sortOrder(orderCounter[0]++)
                    .build();
        }).collect(Collectors.toList());

        List<ProductImage> savedImages = imageRepository.saveAll(newImages);

        return savedImages.stream()
                .map(img -> new ProductImageResponse(img.getId(), img.getImageUrl(), img.getSortOrder()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProductImageResponse> getImagesByProduct(Long productId) {
        return imageRepository.findByProductIdOrderBySortOrderAsc(productId)
                .stream()
                .map(img -> new ProductImageResponse(img.getId(), img.getImageUrl(), img.getSortOrder()))
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteImage(Long sellerId, Long imageId) {
        ProductImage image = imageRepository.findById(imageId)
                .orElseThrow(() -> new AppException(ErrorCode.RECORD_NOT_FOUND));

        if (!image.getProduct().getShop().getOwner().getId().equals(sellerId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        imageRepository.delete(image);
    }
}