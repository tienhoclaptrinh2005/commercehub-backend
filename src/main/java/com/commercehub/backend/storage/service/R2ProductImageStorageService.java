package com.commercehub.backend.storage.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.shop.entity.Shop;
import com.commercehub.backend.shop.repository.ShopRepository;
import com.commercehub.backend.storage.config.R2StorageProperties;
import com.commercehub.backend.storage.dto.request.CompleteProductImageRequest;
import com.commercehub.backend.storage.dto.request.PresignProductImageRequest;
import com.commercehub.backend.storage.dto.response.CompleteProductImageResponse;
import com.commercehub.backend.storage.dto.response.PresignProductImageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "storage.r2.enabled", havingValue = "true")
public class R2ProductImageStorageService implements ProductImageStorageService {

    private static final String PRODUCT_IMAGE_CONTENT_TYPE = "image/webp";
    private static final Map<String, String> EXTENSION_BY_CONTENT_TYPE = Map.of(
            PRODUCT_IMAGE_CONTENT_TYPE, "webp"
    );
    private static final DateTimeFormatter OBJECT_DATE_PATH =
            DateTimeFormatter.ofPattern("uuuu/MM").withZone(ZoneOffset.UTC);

    private final R2StorageProperties properties;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final ShopRepository shopRepository;
    private final MediaUrlService mediaUrlService;
    private final SellerImageUploadRateLimiter rateLimiter;

    @Override
    public PresignProductImageResponse createUpload(Long sellerId, PresignProductImageRequest request) {
        Shop shop = requireActiveShop(sellerId);
        String contentType = normalizeAndValidateContentType(request.contentType());
        validateFileSize(request.fileSize());
        rateLimiter.checkAndRecord(sellerId);

        Instant now = Instant.now();
        String objectKey = "shops/%d/products/%s/%s.%s".formatted(
                shop.getId(),
                OBJECT_DATE_PATH.format(now),
                UUID.randomUUID(),
                EXTENSION_BY_CONTENT_TYPE.get(contentType)
        );

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(objectKey)
                .contentType(contentType)
                .cacheControl(properties.getImageCacheControl())
                .build();
        Duration signatureDuration = Duration.ofSeconds(properties.getPresignDurationSeconds());
        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(
                PutObjectPresignRequest.builder()
                        .signatureDuration(signatureDuration)
                        .putObjectRequest(putObjectRequest)
                        .build()
        );

        return new PresignProductImageResponse(
                objectKey,
                presignedRequest.url().toString(),
                now.plus(signatureDuration),
                properties.getMaxImageSizeBytes(),
                properties.getImageCacheControl()
        );
    }

    @Override
    public CompleteProductImageResponse completeUpload(Long sellerId, CompleteProductImageRequest request) {
        Shop shop = requireActiveShop(sellerId);
        String objectKey = mediaUrlService.normalizeOwnedProductImageReference(
                request.objectKey(),
                shop.getId()
        );

        try {
            HeadObjectResponse head = s3Client.headObject(builder -> builder
                    .bucket(properties.getBucket())
                    .key(objectKey));
            validateFileSize(head.contentLength());
            String contentType = normalizeAndValidateContentType(head.contentType());
            validateStoredImage(objectKey, contentType, head.contentLength());
        } catch (AppException exception) {
            deleteQuietly(objectKey, "invalid upload");
            throw exception;
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                throw new AppException(ErrorCode.IMAGE_UPLOAD_NOT_FOUND);
            }
            log.error("R2 rejected product image completion for key {}: {}", objectKey, exception.getMessage());
            throw new AppException(ErrorCode.IMAGE_STORAGE_UNAVAILABLE);
        } catch (SdkException exception) {
            log.error("Cannot reach R2 while completing product image {}", objectKey, exception);
            throw new AppException(ErrorCode.IMAGE_STORAGE_UNAVAILABLE);
        }

        return new CompleteProductImageResponse(objectKey, mediaUrlService.toPublicUrl(objectKey));
    }

    @Override
    public void deleteProductImage(Long shopId, String objectKey) {
        String normalized = mediaUrlService.normalizeOwnedProductImageReference(objectKey, shopId);
        if (normalized == null) {
            return;
        }
        try {
            s3Client.deleteObject(builder -> builder
                    .bucket(properties.getBucket())
                    .key(normalized));
        } catch (SdkException exception) {
            log.error("Cannot delete replaced R2 product image {}", normalized, exception);
            throw new AppException(ErrorCode.IMAGE_STORAGE_UNAVAILABLE);
        }
    }

    private Shop requireActiveShop(Long sellerId) {
        Shop shop = shopRepository.findByOwnerId(sellerId)
                .orElseThrow(() -> new AppException(ErrorCode.SHOP_NOT_FOUND));
        if (!"ACTIVE".equals(shop.getStatus())
                || !"ACTIVE".equals(shop.getOwner().getStatus())
                || !shop.getOwner().hasRole("SELLER")) {
            throw new AppException(ErrorCode.SHOP_UNAUTHORIZED);
        }
        return shop;
    }

    private String normalizeAndValidateContentType(String contentType) {
        String normalized = contentType == null ? "" : contentType.trim().toLowerCase(Locale.ROOT);
        if (!EXTENSION_BY_CONTENT_TYPE.containsKey(normalized)) {
            throw new AppException(ErrorCode.IMAGE_UPLOAD_INVALID_TYPE);
        }
        return normalized;
    }

    private void validateFileSize(long fileSize) {
        if (fileSize < 1) {
            throw new AppException(ErrorCode.IMAGE_UPLOAD_REFERENCE_INVALID);
        }
        if (fileSize > properties.getMaxImageSizeBytes()) {
            throw new AppException(ErrorCode.IMAGE_UPLOAD_TOO_LARGE);
        }
    }

    private void validateStoredImage(String objectKey, String contentType, long expectedLength) {
        if (!PRODUCT_IMAGE_CONTENT_TYPE.equals(contentType)) {
            throw new AppException(ErrorCode.IMAGE_UPLOAD_INVALID_TYPE);
        }

        ResponseBytes<GetObjectResponse> storedObject = s3Client.getObject(
                GetObjectRequest.builder()
                        .bucket(properties.getBucket())
                        .key(objectKey)
                        // Keep the verification read bounded even if the object is
                        // replaced between HEAD and GET while the signed URL is alive.
                        .range("bytes=0-" + properties.getMaxImageSizeBytes())
                        .build(),
                ResponseTransformer.toBytes()
        );
        byte[] bytes = storedObject.asByteArray();
        if (bytes.length > properties.getMaxImageSizeBytes()) {
            throw new AppException(ErrorCode.IMAGE_UPLOAD_TOO_LARGE);
        }
        if (bytes.length != expectedLength) {
            throw new AppException(ErrorCode.IMAGE_UPLOAD_REFERENCE_INVALID);
        }
        WebpImageInspector.ImageInfo imageInfo;
        try {
            imageInfo = WebpImageInspector.inspect(bytes);
        } catch (IllegalArgumentException exception) {
            throw new AppException(ErrorCode.IMAGE_UPLOAD_INVALID_TYPE);
        }
        if (imageInfo.width() != properties.getProductImageWidth()
                || imageInfo.height() != properties.getProductImageHeight()) {
            throw new AppException(ErrorCode.IMAGE_UPLOAD_INVALID_DIMENSIONS);
        }
        if (imageInfo.containsMetadata() || imageInfo.animated()) {
            throw new AppException(ErrorCode.IMAGE_UPLOAD_METADATA_NOT_ALLOWED);
        }
    }

    private void deleteQuietly(String objectKey, String reason) {
        try {
            s3Client.deleteObject(builder -> builder
                    .bucket(properties.getBucket())
                    .key(objectKey));
        } catch (RuntimeException cleanupError) {
            log.warn("Cannot clean R2 object {} after {}", objectKey, reason, cleanupError);
        }
    }
}
