package com.commercehub.backend.user.controller;

import com.commercehub.backend.user.dto.response.UserLevelResponse;
import com.commercehub.backend.user.dto.response.UserResponse;
import com.commercehub.backend.user.service.UserLevelService;
import com.commercehub.backend.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserLevelService userLevelService;

    // Lấy thông tin public của một user bất kỳ thông qua ID
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }


    @GetMapping("/levels")
    public ResponseEntity<List<UserLevelResponse>> getAllLevels() {
        return ResponseEntity.ok(userLevelService.getAllLevelConfigs());
    }


}