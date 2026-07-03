package com.commercehub.backend.product.controller;

import com.commercehub.backend.common.response.ApiResponse;
import com.commercehub.backend.product.dto.request.ProductFilterRequest;
import com.commercehub.backend.product.dto.response.ProductDetailResponse;
import com.commercehub.backend.product.dto.response.ProductResponse;
import com.commercehub.backend.product.service.ProductSearchService;
import com.commercehub.backend.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.commercehub.backend.common.response.PageResponse;
import java.util.List;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {
    private final ProductSearchService productSearchService;
    private final ProductService productService;

    @GetMapping("/slug/{slug}")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> getProductBySlug(@PathVariable String slug) {
        ProductDetailResponse response = productService.getProductBySlug(slug);
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin chi tiết sản phẩm thành công!", response));
    }

    @GetMapping("/shop/{shopId}")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> getProductsByShop(@PathVariable Long shopId) {
        List<ProductResponse> responses = productService.getProductsByShop(shopId);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách sản phẩm thành công!", responses));
    }


    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> getAllProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        var responses = productService.getAllActiveProducts(page, size);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách sản phẩm thành công!", responses));
    }


    @PostMapping("/search")
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> searchProducts(
            @RequestBody ProductFilterRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        var responses = productSearchService.searchProducts(request, page, size);
        return ResponseEntity.ok(ApiResponse.success("Tìm kiếm sản phẩm thành công!", responses));
    }


}