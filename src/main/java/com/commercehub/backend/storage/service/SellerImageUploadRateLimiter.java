package com.commercehub.backend.storage.service;

public interface SellerImageUploadRateLimiter {

    void checkAndRecord(Long sellerId);
}
