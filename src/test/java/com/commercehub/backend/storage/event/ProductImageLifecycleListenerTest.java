package com.commercehub.backend.storage.event;

import com.commercehub.backend.storage.service.ProductImageStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ProductImageLifecycleListenerTest {

    private final ProductImageStorageService storageService = mock(ProductImageStorageService.class);
    private final ProductImageLifecycleListener listener = new ProductImageLifecycleListener(storageService);

    @Test
    void deletesOldObjectAfterAReplacementCommits() {
        ProductImageReplacedEvent event =
                new ProductImageReplacedEvent(3L, "shops/3/products/2026/09/old.webp");

        listener.deleteReplacedImage(event);

        verify(storageService).deleteProductImage(3L, event.oldObjectKey());
    }

    @Test
    void cleanupFailureDoesNotTurnACommittedProductUpdateIntoAnApiFailure() {
        ProductImageReplacedEvent event =
                new ProductImageReplacedEvent(3L, "shops/3/products/2026/09/old.webp");
        doThrow(new IllegalStateException("R2 unavailable"))
                .when(storageService).deleteProductImage(3L, event.oldObjectKey());

        assertThatCode(() -> listener.deleteReplacedImage(event)).doesNotThrowAnyException();
    }

    @Test
    void listenerIsBoundToTheAfterCommitPhase() throws NoSuchMethodException {
        TransactionalEventListener annotation = ProductImageLifecycleListener.class
                .getMethod("deleteReplacedImage", ProductImageReplacedEvent.class)
                .getAnnotation(TransactionalEventListener.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }
}
