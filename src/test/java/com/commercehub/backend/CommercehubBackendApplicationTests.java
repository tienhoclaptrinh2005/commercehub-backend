package com.commercehub.backend;

import com.commercehub.backend.product.dto.request.CreateProductRequest;
import com.commercehub.backend.product.dto.request.CreateVariantRequest;
import com.commercehub.backend.auth.dto.response.AuthResponse;
import com.commercehub.backend.dashboard.repository.SellerDashboardRepository;
import com.commercehub.backend.product.mapper.ProductMapper;
import com.commercehub.backend.product.repository.ProductRepository;
import com.commercehub.backend.product.service.ProductReviewService;
import com.commercehub.backend.order.repository.OrderRepository;
import com.commercehub.backend.dispute.entity.DisputeStatus;
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

	@Autowired
	private SellerDashboardRepository sellerDashboardRepository;

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
		Set<DisputeStatus> activeDisputeStatuses = Set.of(
				DisputeStatus.OPEN,
				DisputeStatus.WARRANTY_IN_PROGRESS,
				DisputeStatus.WAITING_BUYER_CONFIRMATION,
				DisputeStatus.ADMIN_REVIEW
		);
		assertDoesNotThrow(() -> orderRepository.findFirstBuyerOrders(
				-1L,
				"",
				null,
				false,
				OffsetDateTime.parse("1970-01-01T00:00:00+07:00"),
				OffsetDateTime.parse("9999-12-31T00:00:00+07:00"),
				activeDisputeStatuses,
				PageRequest.of(0, 10)
		));
		assertDoesNotThrow(() -> orderRepository.findBuyerOrdersBefore(
				-1L,
				"ORD-",
				null,
				true,
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
	void sellerDashboardNativeQueriesExecuteAgainstPostgres() {
		List<Long> shopIds = jdbcTemplate.queryForList(
				"SELECT DISTINCT shop_id FROM orders ORDER BY shop_id LIMIT 1",
				Long.class
		);
		assumeFalse(shopIds.isEmpty());
		Long shopId = shopIds.getFirst();
		OffsetDateTime fromTime = OffsetDateTime.parse("2000-01-01T00:00:00+07:00");
		OffsetDateTime toTime = OffsetDateTime.now().plusDays(1);

		var revenueRows = assertDoesNotThrow(() -> sellerDashboardRepository.findMonthlyRevenue(
				shopId,
				fromTime,
				toTime
		));
		assertFalse(revenueRows.isEmpty());
		assertTrue(revenueRows.getFirst().getDay() >= 1);
		assertTrue(revenueRows.getFirst().getOrderCount() >= 1);
		assertTrue(revenueRows.getFirst().getRevenue().compareTo(BigDecimal.ZERO) >= 0);

		var statusRows = assertDoesNotThrow(() -> sellerDashboardRepository.findMonthlyOrderStatusCounts(
				shopId,
				fromTime,
				toTime
		));
		assertFalse(statusRows.isEmpty());
		assertFalse(statusRows.getFirst().getStatus().isBlank());
		assertTrue(statusRows.getFirst().getCount() >= 1);

		var workload = assertDoesNotThrow(() ->
				sellerDashboardRepository.findCurrentPreOrderWorkload(shopId));
		assertTrue(workload.getNewRequestCount() >= 0);
		assertTrue(workload.getProcessingCount() >= 0);

		Long sellerId = jdbcTemplate.queryForObject(
				"SELECT owner_id FROM shops WHERE id = ?",
				Long.class,
				shopId
		);
		var notifications = assertDoesNotThrow(() ->
				sellerDashboardRepository.findSellerNotificationCounts(
						shopId,
						sellerId,
						OffsetDateTime.now().minusHours(24)
				));
		assertTrue(notifications.getRecentInstantOrderCount() >= 0);
		assertTrue(notifications.getNewPreOrderRequestCount() >= 0);
		assertTrue(notifications.getProcessingPreOrderCount() >= 0);
		assertTrue(notifications.getActiveDisputeCount() >= 0);

		assertDoesNotThrow(() -> {
			sellerDashboardRepository.markSellerNotificationCategoryRead(sellerId, "INSTANT_ORDERS");
			sellerDashboardRepository.markSellerNotificationCategoryRead(sellerId, "PRE_ORDERS");
			sellerDashboardRepository.markSellerNotificationCategoryRead(sellerId, "DISPUTES");
		});
		var notificationsAfterRead = sellerDashboardRepository.findSellerNotificationCounts(
				shopId,
				sellerId,
				OffsetDateTime.now().minusHours(24)
		);
		assertEquals(0L, notificationsAfterRead.getRecentInstantOrderCount());
		assertEquals(0L, notificationsAfterRead.getNewPreOrderRequestCount());
		assertEquals(0L, notificationsAfterRead.getProcessingPreOrderCount());
		assertEquals(0L, notificationsAfterRead.getActiveDisputeCount());

		var recentOrders = assertDoesNotThrow(() -> sellerDashboardRepository.findRecentOrders(shopId));
		assertFalse(recentOrders.isEmpty());
		assertTrue(recentOrders.size() <= 5);
		assertEquals(shopId, jdbcTemplate.queryForObject(
				"SELECT shop_id FROM orders WHERE id = ?",
				Long.class,
				recentOrders.getFirst().getOrderId()
		));
		assertFalse(recentOrders.getFirst().getOrderCode().isBlank());
		assertFalse(recentOrders.getFirst().getProductName().isBlank());
		assertTrue(recentOrders.getFirst().getItemCount() >= 1);
		assertTrue(recentOrders.getFirst().getPlacedAt().isBefore(java.time.Instant.now().plusSeconds(60)));
	}

	@Test
	@Transactional
	void sellerProductManagementQueriesAreScopedAndExecutable() {
		List<Long> sellerIds = jdbcTemplate.queryForList(
				"""
				SELECT DISTINCT shop.owner_id
				FROM shops shop
				JOIN products product ON product.shop_id = shop.id
				WHERE product.status <> 'DELETED'
				ORDER BY shop.owner_id
				LIMIT 1
				""",
				Long.class
		);
		assumeFalse(sellerIds.isEmpty());
		Long sellerId = sellerIds.getFirst();

		var products = assertDoesNotThrow(() -> productRepository.findSellerProducts(
				sellerId,
				"",
				null,
				null,
				null,
				PageRequest.of(0, 10)
		));
		assertFalse(products.isEmpty());
		assertFalse(products.getContent().getFirst().getCategory().getName().isBlank());

		List<Long> productIds = products.getContent().stream().map(product -> product.getId()).toList();
		var inventory = assertDoesNotThrow(() -> productRepository.findActiveVariantStats(productIds));
		inventory.forEach(row -> {
			assertTrue(productIds.contains(row.getProductId()));
			assertTrue(row.getMinPrice().compareTo(BigDecimal.ZERO) > 0);
			assertTrue(row.getStockCount() >= 0);
		});
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
