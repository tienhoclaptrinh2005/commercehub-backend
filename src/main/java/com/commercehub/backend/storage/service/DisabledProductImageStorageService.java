package com.commercehub.backend.storage.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.storage.dto.request.CompleteProductImageRequest;
import com.commercehub.backend.storage.dto.request.PresignProductImageRequest;
import com.commercehub.backend.storage.dto.response.CompleteProductImageResponse;
import com.commercehub.backend.storage.dto.response.PresignProductImageResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "storage.r2.enabled", havingValue = "false", matchIfMissing = true)
public class DisabledProductImageStorageService implements ProductImageStorageService {

    @Override
    public PresignProductImageResponse createUpload(Long sellerId, PresignProductImageRequest request) {
        throw new AppException(ErrorCode.IMAGE_STORAGE_UNAVAILABLE);
    }

    @Override
    public CompleteProductImageResponse completeUpload(Long sellerId, CompleteProductImageRequest request) {
        throw new AppException(ErrorCode.IMAGE_STORAGE_UNAVAILABLE);
    }
}
