package com.commercehub.backend.storage.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.storage.dto.request.CompleteAvatarImageRequest;
import com.commercehub.backend.storage.dto.request.PresignAvatarImageRequest;
import com.commercehub.backend.storage.dto.response.PresignAvatarImageResponse;
import com.commercehub.backend.user.dto.response.ProfileResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "storage.r2.enabled", havingValue = "false", matchIfMissing = true)
public class DisabledAvatarImageStorageService implements AvatarImageStorageService {

    @Override
    public PresignAvatarImageResponse createUpload(Long userId, PresignAvatarImageRequest request) {
        throw new AppException(ErrorCode.IMAGE_STORAGE_UNAVAILABLE);
    }

    @Override
    public ProfileResponse completeAndApplyUpload(Long userId, CompleteAvatarImageRequest request) {
        throw new AppException(ErrorCode.IMAGE_STORAGE_UNAVAILABLE);
    }

    @Override
    public void deleteAvatarImage(Long userId, String objectKey) {
        // Không có object từ xa để dọn khi R2 đang tắt.
    }
}
