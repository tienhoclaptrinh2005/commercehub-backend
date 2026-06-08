package com.commercehub.backend.user.controller;

import com.commercehub.backend.security.CustomUserDetails;
import com.commercehub.backend.user.dto.request.UpdateAvatarRequest;
import com.commercehub.backend.user.dto.request.UpdateProfileRequest;
import com.commercehub.backend.user.dto.response.ProfileResponse;
import com.commercehub.backend.user.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping
    public ResponseEntity<ProfileResponse> getMyProfile(@AuthenticationPrincipal CustomUserDetails currentUser) {
            String email = currentUser.getUsername();
            return ResponseEntity.ok(profileService.getMyProfile(email));

    }

    @PutMapping
    public ResponseEntity<ProfileResponse> updateMyProfile (@RequestBody UpdateProfileRequest request, @AuthenticationPrincipal CustomUserDetails currentUser) {
        String email = currentUser.getUsername();
        return ResponseEntity.ok(profileService.updateMyProfile(email , request));

    }


    @PatchMapping
    public ResponseEntity<ProfileResponse> updateAvatar(@RequestBody UpdateAvatarRequest request, @AuthenticationPrincipal CustomUserDetails currentUser) {
        String email = currentUser.getUsername();
        ProfileResponse updatedProfile = profileService.updateAvatar(email , request);
        return ResponseEntity.ok(updatedProfile);
    }

}
