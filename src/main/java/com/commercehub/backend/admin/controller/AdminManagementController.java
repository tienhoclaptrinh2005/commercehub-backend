package com.commercehub.backend.admin.controller;

import com.commercehub.backend.admin.dto.AdminRequests;
import com.commercehub.backend.admin.dto.AdminResponses;
import com.commercehub.backend.admin.service.AdminCommandService;
import com.commercehub.backend.admin.service.AdminQueryService;
import com.commercehub.backend.admin.service.AdminAuditService;
import com.commercehub.backend.category.dto.request.CreateCategoryRequest;
import com.commercehub.backend.category.dto.request.UpdateCategoryRequest;
import com.commercehub.backend.category.dto.response.CategoryResponse;
import com.commercehub.backend.category.service.CategoryService;
import com.commercehub.backend.common.response.ApiResponse;
import com.commercehub.backend.common.response.PageResponse;
import com.commercehub.backend.common.util.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminManagementController {
    private final AdminQueryService queries;
    private final AdminCommandService commands;
    private final CategoryService categoryService;
    private final AdminAuditService auditService;

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<AdminResponses.Dashboard>> dashboard() {
        return ResponseEntity.ok(ApiResponse.success(queries.dashboard()));
    }

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<PageResponse<AdminResponses.UserRow>>> users(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(queries.users(keyword, status, role, page, size)));
    }

    @PatchMapping("/users/{userId}/status")
    public ResponseEntity<ApiResponse<Void>> changeUserStatus(@PathVariable Long userId,
            @Valid @RequestBody AdminRequests.StatusChange request, HttpServletRequest http) {
        commands.changeUserStatus(SecurityUtils.getCurrentUserId(), userId, request, clientIp(http), http.getHeader("User-Agent"));
        return ResponseEntity.ok(ApiResponse.success("Đã cập nhật trạng thái tài khoản.", null));
    }

    @GetMapping("/shops")
    public ResponseEntity<ApiResponse<PageResponse<AdminResponses.ShopRow>>> shops(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(queries.shops(keyword, status, page, size)));
    }

    @PatchMapping("/shops/{shopId}/status")
    public ResponseEntity<ApiResponse<Void>> changeShopStatus(@PathVariable Long shopId,
            @Valid @RequestBody AdminRequests.ShopStatusChange request, HttpServletRequest http) {
        commands.changeShopStatus(SecurityUtils.getCurrentUserId(), shopId, request, clientIp(http), http.getHeader("User-Agent"));
        return ResponseEntity.ok(ApiResponse.success("Đã cập nhật trạng thái gian hàng.", null));
    }

    @GetMapping("/products")
    public ResponseEntity<ApiResponse<PageResponse<AdminResponses.ProductRow>>> products(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long shopId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(queries.products(keyword, status, shopId, categoryId, page, size)));
    }

    @PatchMapping("/products/{productId}/status")
    public ResponseEntity<ApiResponse<Void>> changeProductStatus(@PathVariable Long productId,
            @Valid @RequestBody AdminRequests.StatusChange request, HttpServletRequest http) {
        commands.changeProductStatus(SecurityUtils.getCurrentUserId(), productId, request, clientIp(http), http.getHeader("User-Agent"));
        return ResponseEntity.ok(ApiResponse.success("Đã cập nhật trạng thái sản phẩm.", null));
    }

    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<java.util.List<AdminResponses.CategoryRow>>> categories() {
        return ResponseEntity.ok(ApiResponse.success(queries.categories()));
    }

    @PostMapping("/categories")
    @Transactional
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(
            @Valid @RequestBody CreateCategoryRequest request, HttpServletRequest http) {
        CategoryResponse created = categoryService.createCategory(request);
        auditService.record(SecurityUtils.getCurrentUserId(), "CATEGORY_CREATED", "CATEGORY", created.getId(),
                null, java.util.Map.of("name", created.getName(), "active", created.getIsActive()), null,
                clientIp(http), http.getHeader("User-Agent"));
        return ResponseEntity.ok(ApiResponse.success("Đã tạo danh mục.", created));
    }

    @PutMapping("/categories/{categoryId}")
    @Transactional
    public ResponseEntity<ApiResponse<CategoryResponse>> updateCategory(@PathVariable Long categoryId,
            @Valid @RequestBody UpdateCategoryRequest request, HttpServletRequest http) {
        CategoryResponse updated = categoryService.updateCategory(categoryId, request);
        auditService.record(SecurityUtils.getCurrentUserId(), "CATEGORY_UPDATED", "CATEGORY", categoryId,
                null, java.util.Map.of("name", updated.getName(), "active", updated.getIsActive()), null,
                clientIp(http), http.getHeader("User-Agent"));
        return ResponseEntity.ok(ApiResponse.success("Đã cập nhật danh mục.", updated));
    }

    @DeleteMapping("/categories/{categoryId}")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> deactivateCategory(@PathVariable Long categoryId, HttpServletRequest http) {
        categoryService.deleteCategory(categoryId);
        auditService.record(SecurityUtils.getCurrentUserId(), "CATEGORY_DEACTIVATED", "CATEGORY", categoryId,
                null, java.util.Map.of("active", false), "Ẩn danh mục", clientIp(http), http.getHeader("User-Agent"));
        return ResponseEntity.ok(ApiResponse.success("Đã ẩn danh mục.", null));
    }

    @GetMapping("/deposits")
    public ResponseEntity<ApiResponse<PageResponse<AdminResponses.DepositRow>>> deposits(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String provider,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(queries.deposits(keyword, status, provider, page, size)));
    }

    @GetMapping("/withdrawals")
    public ResponseEntity<ApiResponse<PageResponse<AdminResponses.WithdrawalRow>>> withdrawals(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(queries.withdrawals(keyword, status, page, size)));
    }

    @PutMapping("/withdrawals/{withdrawalId}/decision")
    public ResponseEntity<ApiResponse<Void>> decideWithdrawal(@PathVariable Long withdrawalId,
            @Valid @RequestBody AdminRequests.WithdrawalDecision request, HttpServletRequest http) {
        commands.processWithdrawal(SecurityUtils.getCurrentUserId(), withdrawalId, request, clientIp(http), http.getHeader("User-Agent"));
        return ResponseEntity.ok(ApiResponse.success("Đã xử lý yêu cầu rút tiền.", null));
    }

    @GetMapping("/wallet-transactions")
    public ResponseEntity<ApiResponse<PageResponse<AdminResponses.WalletTransactionRow>>> transactions(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(queries.transactions(keyword, type, page, size)));
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<ApiResponse<PageResponse<AdminResponses.AuditLogRow>>> auditLogs(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(queries.auditLogs(keyword, action, targetType, page, size)));
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
    }
}
