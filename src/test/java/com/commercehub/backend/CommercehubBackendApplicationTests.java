package com.commercehub.backend;

import com.commercehub.backend.product.dto.request.CreateProductRequest;
import com.commercehub.backend.product.dto.request.CreateVariantRequest;
import com.commercehub.backend.auth.dto.response.AuthResponse;
import com.commercehub.backend.product.mapper.ProductMapper;
import com.commercehub.backend.product.repository.ProductRepository;
import com.commercehub.backend.product.service.ProductReviewService;
import com.commercehub.backend.order.repository.OrderRepository;
import com.commercehub.backend.shop.dto.request.CreateShopRequest;
import com.commercehub.backend.shop.mapper.ShopMapper;
import com.commercehub.backend.shop.repository.ShopRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@SpringBootTest
class CommercehubBackendApplicationTests {

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private OrderRepository orderRepository;

	@Autowired
	private ProductMapper productMapper;

	@Autowired
	private ProductReviewService productReviewService;

	@Autowired
	private ShopRepository shopRepository;

	@Autowired
	private ShopMapper shopMapper;

	@Autowired
	private RoleHierarchy roleHierarchy;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

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
    void administratorsCanBuyButDoNotInheritSellerPermissions() {
        var adminAuthorities = roleHierarchy.getReachableGrantedAuthorities(
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        assertFalse(adminAuthorities.contains(new SimpleGrantedAuthority("ROLE_SELLER")));
        assertTrue(adminAuthorities.contains(new SimpleGrantedAuthority("ROLE_BUYER")));

        var superAdminAuthorities = roleHierarchy.getReachableGrantedAuthorities(
                List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))
        );
        assertTrue(superAdminAuthorities.contains(new SimpleGrantedAuthority("ROLE_ADMIN")));
        assertFalse(superAdminAuthorities.contains(new SimpleGrantedAuthority("ROLE_SELLER")));
        assertTrue(superAdminAuthorities.contains(new SimpleGrantedAuthority("ROLE_BUYER")));

        var sellerAuthorities = roleHierarchy.getReachableGrantedAuthorities(
                List.of(new SimpleGrantedAuthority("ROLE_SELLER"))
        );
        assertTrue(sellerAuthorities.contains(new SimpleGrantedAuthority("ROLE_BUYER")));
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
	@Transactional
	void buyerOrderFiltersAcceptEmptyOptionalCriteria() {
		Set<String> activeDisputeStatuses = Set.of(
				"OPEN",
				"WARRANTY_IN_PROGRESS",
				"WAITING_BUYER_CONFIRMATION",
				"PROCESSING"
		);
		assertDoesNotThrow(() -> orderRepository.findFirstBuyerOrders(
				-1L,
				"",
				"",
				OffsetDateTime.parse("1970-01-01T00:00:00+07:00"),
				OffsetDateTime.parse("9999-12-31T00:00:00+07:00"),
				activeDisputeStatuses,
				PageRequest.of(0, 10)
		));
		assertDoesNotThrow(() -> orderRepository.findBuyerOrdersBefore(
				-1L,
				"ORD-",
				"DISPUTED",
				OffsetDateTime.now().minusYears(1),
				OffsetDateTime.now().plusDays(1),
				OffsetDateTime.now(),
				Long.MAX_VALUE,
				activeDisputeStatuses,
				PageRequest.of(0, 10)
		));
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
	void unratedProductDefaultsToFiveStars() {
		var products = productRepository.findActiveProductsFromActiveShops(PageRequest.of(0, 100));
		assumeFalse(products.isEmpty());

		var unratedProduct = products.getContent().stream()
				.filter(product -> productReviewService
						.getRatingSummary(product.getId())
						.reviewCount() == 0)
				.findFirst();
		assumeTrue(unratedProduct.isPresent());

		var rating = productReviewService.getRatingSummary(unratedProduct.get().getId());
		assertEquals(0L, rating.reviewCount());
		assertEquals(new BigDecimal("5.00"), rating.averageRating());
	}

	@Test
	@Transactional
	void shopRatingCountersAreUpdatedAtomically() {
		var shops = shopRepository.findAll(PageRequest.of(0, 1));
		assumeFalse(shops.isEmpty());
		Long shopId = shops.getContent().getFirst().getId();
		Map<String, Object> original = loadShopRating(shopId);
		long originalCount = ((Number) original.get("rating_count")).longValue();
		long originalSum = ((Number) original.get("rating_sum")).longValue();

		assertEquals(1, shopRepository.addVisibleRating(shopId, 4));
		Map<String, Object> updated = loadShopRating(shopId);
		assertEquals(originalCount + 1, ((Number) updated.get("rating_count")).longValue());
		assertEquals(originalSum + 4, ((Number) updated.get("rating_sum")).longValue());

		assertEquals(1, shopRepository.removeVisibleRating(shopId, 4));
		Map<String, Object> restored = loadShopRating(shopId);
		assertEquals(originalCount, ((Number) restored.get("rating_count")).longValue());
		assertEquals(originalSum, ((Number) restored.get("rating_sum")).longValue());
		assertEquals(
				((BigDecimal) original.get("rating_avg")).stripTrailingZeros(),
				((BigDecimal) restored.get("rating_avg")).stripTrailingZeros()
		);
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

	private Map<String, Object> loadShopRating(Long shopId) {
		return jdbcTemplate.queryForMap(
				"SELECT rating_avg, rating_count, rating_sum FROM shops WHERE id = ?",
				shopId
		);
	}

}
