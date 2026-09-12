package com.commercehub.backend.dashboard.controller;

import com.commercehub.backend.common.response.ApiResponse;
import com.commercehub.backend.dashboard.dto.response.SellerDashboardResponse;
import com.commercehub.backend.dashboard.dto.response.SellerNotificationResponse;
import com.commercehub.backend.dashboard.service.SellerDashboardService;
import com.commercehub.backend.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/seller/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SELLER')")
public class SellerDashboardController {

    private final SellerDashboardService dashboardService;

    @GetMapping
    public ResponseEntity<ApiResponse<SellerDashboardResponse>> getDashboard(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @RequestParam(required = false) String month
    ) {
        SellerDashboardResponse response = dashboardService.getDashboard(currentUser.getId(), month);
        return ResponseEntity.ok(ApiResponse.success("Lấy tổng quan bán hàng thành công", response));
    }

    @GetMapping("/notifications")
    public ResponseEntity<ApiResponse<SellerNotificationResponse>> getNotifications(
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        SellerNotificationResponse response = dashboardService.getNotifications(currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success("Lấy thông báo bán hàng thành công", response));
    }

    @PostMapping("/notifications/{category}/read")
    public ResponseEntity<ApiResponse<SellerNotificationResponse>> markNotificationsRead(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable String category
    ) {
        SellerNotificationResponse response = dashboardService.markNotificationsRead(
                currentUser.getId(),
                category
        );
        return ResponseEntity.ok(ApiResponse.success("Đã đánh dấu thông báo là đã xem", response));
    }
}
