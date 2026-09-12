package com.commercehub.backend.storage.event;

import com.commercehub.backend.storage.service.AvatarImageStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class AvatarImageLifecycleListener {

    private final AvatarImageStorageService imageStorageService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void deleteReplacedImage(AvatarImageReplacedEvent event) {
        try {
            imageStorageService.deleteAvatarImage(event.userId(), event.oldObjectKey());
        } catch (RuntimeException exception) {
            log.warn(
                    "Avatar replacement committed but old R2 object {} could not be deleted",
                    event.oldObjectKey(),
                    exception
            );
        }
    }
}
