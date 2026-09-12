package com.commercehub.backend.user.service;

import com.commercehub.backend.order.service.OrderStatisticsService;
import com.commercehub.backend.shop.repository.ShopRepository;
import com.commercehub.backend.storage.event.AvatarImageReplacedEvent;
import com.commercehub.backend.storage.service.MediaUrlService;
import com.commercehub.backend.user.dto.response.ProfileResponse;
import com.commercehub.backend.user.entity.User;
import com.commercehub.backend.user.mapper.UserMapper;
import com.commercehub.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProfileServiceAvatarTest {

    private UserRepository userRepository;
    private UserMapper userMapper;
    private ShopRepository shopRepository;
    private MediaUrlService mediaUrlService;
    private ApplicationEventPublisher eventPublisher;
    private ProfileService profileService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        userMapper = mock(UserMapper.class);
        shopRepository = mock(ShopRepository.class);
        mediaUrlService = mock(MediaUrlService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        profileService = new ProfileService(
                userRepository,
                userMapper,
                mock(OrderStatisticsService.class),
                shopRepository,
                mediaUrlService,
                eventPublisher
        );
    }

    @Test
    void storesObjectKeyAndBuildsCurrentPublicUrlWhenReplacingAvatar() {
        String oldKey = "users/7/avatars/2026/08/old.webp";
        String newKey = "users/7/avatars/2026/09/new.webp";
        User user = User.builder().id(7L).status("ACTIVE").avatarUrl(oldKey).build();
        ProfileResponse mapped = ProfileResponse.builder().build();

        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(mediaUrlService.normalizeOwnedAvatarImageReference(newKey, 7L)).thenReturn(newKey);
        when(mediaUrlService.isOwnedAvatarImageReference(oldKey, 7L)).thenReturn(true);
        when(mediaUrlService.toPublicUrl(newKey)).thenReturn("https://images.new.test/" + newKey);
        when(userMapper.toProfileResponse(user, 0L, 0L)).thenReturn(mapped);
        when(shopRepository.findByOwnerId(7L)).thenReturn(Optional.empty());

        ProfileResponse response = profileService.replaceAvatar(7L, newKey);

        assertThat(user.getAvatarUrl()).isEqualTo(newKey);
        assertThat(response.getAvatarUrl()).isEqualTo("https://images.new.test/" + newKey);
        verify(userRepository).save(user);

        ArgumentCaptor<AvatarImageReplacedEvent> eventCaptor =
                ArgumentCaptor.forClass(AvatarImageReplacedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().oldObjectKey()).isEqualTo(oldKey);
    }
}
