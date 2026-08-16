package com.commercehub.backend;

import com.commercehub.backend.product.dto.request.CreateProductRequest;
import com.commercehub.backend.product.dto.request.CreateVariantRequest;
import com.commercehub.backend.auth.dto.response.AuthResponse;
import com.commercehub.backend.product.mapper.ProductMapper;
import com.commercehub.backend.product.repository.ProductRepository;
import com.commercehub.backend.shop.dto.request.CreateShopRequest;
import com.commercehub.backend.shop.mapper.ShopMapper;
import com.commercehub.backend.shop.repository.ShopRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

@SpringBootTest
class CommercehubBackendApplicationTests {

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private ProductMapper productMapper;

	@Autowired
	private ShopRepository shopRepository;

	@Autowired
	private ShopMapper shopMapper;

	@Autowired
	private RoleHierarchy roleHierarchy;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void contextLoads() {
	}

	@Test
	void newShopStartsPendingUntilAdminApproval() {
		CreateShopRequest request = new CreateShopRequest();
		request.setName("Shop đang chờ duyệt");

		assertEquals("PENDING", shopMapper.toEntity(request).getStatus());
	}

	@Test
	void higherRolesInheritCommercePermissions() {
		var adminAuthorities = roleHierarchy.getReachableGrantedAuthorities(
				List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
		);
		assertTrue(adminAuthorities.contains(new SimpleGrantedAuthority("ROLE_SELLER")));
		assertTrue(adminAuthorities.contains(new SimpleGrantedAuthority("ROLE_BUYER")));

		var superAdminAuthorities = roleHierarchy.getReachableGrantedAuthorities(
				List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))
		);
		assertTrue(superAdminAuthorities.contains(new SimpleGrantedAuthority("ROLE_ADMIN")));
		assertTrue(superAdminAuthorities.contains(new SimpleGrantedAuthority("ROLE_SELLER")));
		assertTrue(superAdminAuthorities.contains(new SimpleGrantedAuthority("ROLE_BUYER")));
	}

	@Test
	void refreshTokenIsNeverSerializedToJson() throws Exception {
		AuthResponse response = AuthResponse.builder()
				.accessToken("access-token")
				.refreshToken("server-only-refresh-token")
				.build();

		String json = objectMapper.writeValueAsString(response);
		assertFalse(json.contains("server-only-refresh-token"));
		assertFalse(json.contains("refreshToken"));
	}

	@Test
	void productDetailQueryDoesNotFetchMultipleBagCollections() {
		assertDoesNotThrow(() -> productRepository.findPublicBySlug("__missing-product__"));
	}

	@Test
	void productMapperLeavesVariantCreationToProductService() {
		CreateVariantRequest variantRequest = new CreateVariantRequest();
		variantRequest.setName("Gói 1 tháng");
		variantRequest.setPrice(BigDecimal.valueOf(99_000));

		CreateProductRequest request = new CreateProductRequest();
		request.setName("Sản phẩm kiểm thử");
		request.setShortDescription("Mô tả ngắn");
		request.setDescription("Mô tả chi tiết");
		request.setThumbnailUrl("https://example.com/product.jpg");
		request.setVariants(List.of(variantRequest));

		var product = productMapper.toEntity(request);

		assertTrue(product.getVariants().isEmpty());
		assertEquals(request.getThumbnailUrl(), product.getThumbnailUrl());
	}

	@Test
	@Transactional
	void banningAndUnbanningShopDynamicallyHidesAndRestoresProducts() {
		var products = productRepository.findActiveProductsFromActiveShops(PageRequest.of(0, 1));
		assumeFalse(products.isEmpty());

		var product = products.getContent().getFirst();
		var shop = product.getShop();

		shop.setStatus("BANNED");
		shopRepository.saveAndFlush(shop);
		assertTrue(productRepository.findPublicBySlug(product.getSlug()).isEmpty());

		shop.setStatus("ACTIVE");
		shopRepository.saveAndFlush(shop);
		assertFalse(productRepository.findPublicBySlug(product.getSlug()).isEmpty());
	}

}
