package com.commercehub.backend.storage.service;

import com.commercehub.backend.storage.dto.request.CompleteProductImageRequest;
import com.commercehub.backend.storage.dto.request.PresignProductImageRequest;
import com.commercehub.backend.storage.dto.response.CompleteProductImageResponse;
import com.commercehub.backend.storage.dto.response.PresignProductImageResponse;

public interface ProductImageStorageService {

    PresignProductImageResponse createUpload(Long sellerId, PresignProductImageRequest request);

    CompleteProductImageResponse completeUpload(Long sellerId, CompleteProductImageRequest request);

    void deleteProductImage(Long shopId, String objectKey);
}
