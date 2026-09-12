package com.commercehub.backend.storage.event;

public record AvatarImageReplacedEvent(Long userId, String oldObjectKey) {
}
