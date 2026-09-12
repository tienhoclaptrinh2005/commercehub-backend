package com.commercehub.backend.storage.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.storage.config.R2StorageProperties;
import com.commercehub.backend.storage.dto.request.CompleteAvatarImageRequest;
import com.commercehub.backend.storage.dto.request.PresignAvatarImageRequest;
import com.commercehub.backend.storage.dto.response.PresignAvatarImageResponse;
import com.commercehub.backend.user.dto.response.ProfileResponse;
import com.commercehub.backend.user.entity.User;
import com.commercehub.backend.user.repository.UserRepository;
import com.commercehub.backend.user.service.ProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
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
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "storage.r2.enabled", havingValue = "true")
public class R2AvatarImageStorageService implements AvatarImageStorageService {

    private static final String AVATAR_CONTENT_TYPE = "image/webp";
    private static final DateTimeFormatter OBJECT_DATE_PATH =
            DateTimeFormatter.ofPattern("uuuu/MM").withZone(ZoneOffset.UTC);

    private final R2StorageProperties properties;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final UserRepository userRepository;
    private final MediaUrlService mediaUrlService;
    private final SellerImageUploadRateLimiter rateLimiter;
    private final ProfileService profileService;

    @Override
    public PresignAvatarImageResponse createUpload(Long userId, PresignAvatarImageRequest request) {
        requireActiveUser(userId);
        String contentType = normalizeAndValidateContentType(request.contentType());
        validateFileSize(request.fileSize());
        // Dùng chung quota tải ảnh theo user để một tài khoản không thể spam
        // presigned URL qua cả màn hình sản phẩm và màn hình hồ sơ.
        rateLimiter.checkAndRecord(userId);

        Instant now = Instant.now();
        String objectKey = "users/%d/avatars/%s/%s.webp".formatted(
                userId,
                OBJECT_DATE_PATH.format(now),
                UUID.randomUUID()
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

        return new PresignAvatarImageResponse(
                objectKey,
                presignedRequest.url().toString(),
                now.plus(signatureDuration),
                properties.getAvatarMaxImageSizeBytes(),
                properties.getAvatarImageWidth(),
                properties.getAvatarImageHeight(),
                properties.getImageCacheControl()
        );
    }

    @Override
    public ProfileResponse completeAndApplyUpload(Long userId, CompleteAvatarImageRequest request) {
        requireActiveUser(userId);
        String objectKey = mediaUrlService.normalizeOwnedAvatarImageReference(request.objectKey(), userId);

        try {
            HeadObjectResponse head = s3Client.headObject(builder -> builder
                    .bucket(properties.getBucket())
                    .key(objectKey));
            validateFileSize(head.contentLength());
            String contentType = normalizeAndValidateContentType(head.contentType());
            validateStoredImage(objectKey, contentType, head.contentLength());
        } catch (AppException exception) {
            deleteQuietly(objectKey, "invalid avatar upload");
            throw exception;
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                throw new AppException(ErrorCode.IMAGE_UPLOAD_NOT_FOUND);
            }
            log.error("R2 rejected avatar completion for key {}: {}", objectKey, exception.getMessage());
            throw new AppException(ErrorCode.IMAGE_STORAGE_UNAVAILABLE);
        } catch (SdkException exception) {
            log.error("Cannot reach R2 while completing avatar {}", objectKey, exception);
            throw new AppException(ErrorCode.IMAGE_STORAGE_UNAVAILABLE);
        }

        try {
            return profileService.replaceAvatar(userId, objectKey);
        } catch (RuntimeException exception) {
            // DB chưa nhận object key thì object mới không còn chủ sở hữu và phải
            // được dọn ngay; ảnh cũ trong DB vẫn nguyên vẹn.
            deleteQuietly(objectKey, "database avatar update failure");
            throw exception;
        }
    }

    @Override
    public void deleteAvatarImage(Long userId, String objectKey) {
        String normalized = mediaUrlService.normalizeOwnedAvatarImageReference(objectKey, userId);
        if (normalized == null) {
            return;
        }
        try {
            s3Client.deleteObject(builder -> builder
                    .bucket(properties.getBucket())
                    .key(normalized));
        } catch (SdkException exception) {
            log.error("Cannot delete replaced R2 avatar {}", normalized, exception);
            throw new AppException(ErrorCode.IMAGE_STORAGE_UNAVAILABLE);
        }
    }

    private User requireActiveUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new AppException(ErrorCode.ACCOUNT_LOCKED);
        }
        return user;
    }

    private String normalizeAndValidateContentType(String contentType) {
        String normalized = contentType == null ? "" : contentType.trim().toLowerCase(Locale.ROOT);
        if (!AVATAR_CONTENT_TYPE.equals(normalized)) {
            throw new AppException(ErrorCode.IMAGE_UPLOAD_INVALID_TYPE);
        }
        return normalized;
    }

    private void validateFileSize(long fileSize) {
        if (fileSize < 1) {
            throw new AppException(ErrorCode.IMAGE_UPLOAD_REFERENCE_INVALID);
        }
        if (fileSize > properties.getAvatarMaxImageSizeBytes()) {
            throw new AppException(ErrorCode.IMAGE_UPLOAD_TOO_LARGE);
        }
    }

    private void validateStoredImage(String objectKey, String contentType, long expectedLength) {
        if (!AVATAR_CONTENT_TYPE.equals(contentType)) {
            throw new AppException(ErrorCode.IMAGE_UPLOAD_INVALID_TYPE);
        }

        ResponseBytes<GetObjectResponse> storedObject = s3Client.getObject(
                GetObjectRequest.builder()
                        .bucket(properties.getBucket())
                        .key(objectKey)
                        .range("bytes=0-" + properties.getAvatarMaxImageSizeBytes())
                        .build(),
                ResponseTransformer.toBytes()
        );
        byte[] bytes = storedObject.asByteArray();
        if (bytes.length > properties.getAvatarMaxImageSizeBytes()) {
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
        if (imageInfo.width() != properties.getAvatarImageWidth()
                || imageInfo.height() != properties.getAvatarImageHeight()) {
            throw new AppException(ErrorCode.AVATAR_IMAGE_INVALID_DIMENSIONS);
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
