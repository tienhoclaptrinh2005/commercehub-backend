package com.commercehub.backend.storage.event;

public record ProductImageReplacedEvent(Long shopId, String oldObjectKey) {
}
