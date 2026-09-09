package com.commercehub.backend.storage.event;

import com.commercehub.backend.storage.service.ProductImageStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductImageLifecycleListener {

    private final ProductImageStorageService imageStorageService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void deleteReplacedImage(ProductImageReplacedEvent event) {
        try {
            imageStorageService.deleteProductImage(event.shopId(), event.oldObjectKey());
        } catch (RuntimeException exception) {
            // The product update has already committed. Keep the new image and let
            // operational cleanup retry the old object instead of failing the API.
            log.warn("Product image replacement committed but old R2 object {} could not be deleted",
                    event.oldObjectKey(), exception);
        }
    }
}
