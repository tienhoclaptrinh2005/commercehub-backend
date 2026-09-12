package com.commercehub.backend.storage.service;

import com.commercehub.backend.storage.dto.request.CompleteAvatarImageRequest;
import com.commercehub.backend.storage.dto.request.PresignAvatarImageRequest;
import com.commercehub.backend.storage.dto.response.PresignAvatarImageResponse;
import com.commercehub.backend.user.dto.response.ProfileResponse;

public interface AvatarImageStorageService {

    PresignAvatarImageResponse createUpload(Long userId, PresignAvatarImageRequest request);

    ProfileResponse completeAndApplyUpload(Long userId, CompleteAvatarImageRequest request);

    void deleteAvatarImage(Long userId, String objectKey);
}
