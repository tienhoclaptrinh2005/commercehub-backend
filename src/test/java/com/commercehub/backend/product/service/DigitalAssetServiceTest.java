package com.commercehub.backend.product.service;

import com.commercehub.backend.common.exception.AppException;
import com.commercehub.backend.common.exception.ErrorCode;
import com.commercehub.backend.product.dto.request.UploadDigitalAssetRequest;
import com.commercehub.backend.product.dto.response.DigitalAssetImportResponse;
import com.commercehub.backend.product.entity.Product;
import com.commercehub.backend.product.entity.ProductVariant;
import com.commercehub.backend.product.mapper.ProductMapper;
import com.commercehub.backend.product.repository.AssetDeliveryLogRepository;
import com.commercehub.backend.product.repository.DigitalAssetRepository;
import com.commercehub.backend.product.repository.ProductVariantRepository;
import com.commercehub.backend.shop.entity.Shop;
import com.commercehub.backend.user.entity.Role;
import com.commercehub.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DigitalAssetServiceTest {

    private final DigitalAssetRepository assetRepository = mock(DigitalAssetRepository.class);
    private final ProductVariantRepository variantRepository = mock(ProductVariantRepository.class);
    private final DigitalAssetService service = new DigitalAssetService(
            assetRepository,
            mock(AssetDeliveryLogRepository.class),
            variantRepository,
            mock(ProductMapper.class)
    );

    private ProductVariant variant;

    @BeforeEach
    void setUp() {
        Role role = new Role();
        role.setName("SELLER");
        User owner = User.builder()
                .id(7L)
                .status("ACTIVE")
                .roles(new HashSet<>(Set.of(role)))
                .build();
        Shop shop = Shop.builder().id(3L).status("ACTIVE").owner(owner).build();
        Product product = Product.builder()
                .id(5L)
                .shop(shop)
                .status("ACTIVE")
                .deliveryType("INSTANT")
                .productType("ACCOUNT")
                .build();
        variant = ProductVariant.builder().id(11L).product(product).status("ACTIVE").stockCount(0).build();
        when(variantRepository.findById(11L)).thenReturn(Optional.of(variant));
    }

    @Test
    void importsOnlyUniqueCredentialsAndUpdatesStockByInsertedRows() {
        UploadDigitalAssetRequest request = new UploadDigitalAssetRequest();
        request.setVariantId(11L);
        request.setRawAssets(List.of(
                " Account@example.com|OldSecret ",
                "account@example.com|NewSecret",
                "other|Secret"
        ));
        when(assetRepository.insertAvailableAssetIfAbsent(
                eq(11L), eq("ACCOUNT"), anyString(), anyString(), anyString()))
                .thenReturn(1, 0, 1);

        DigitalAssetImportResponse result = service.uploadAssets(7L, request);

        assertThat(result).isEqualTo(new DigitalAssetImportResponse(3, 2, 1));
        verify(variantRepository).incrementStockCount(11L, 2);
        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        verify(assetRepository, org.mockito.Mockito.times(3)).insertAvailableAssetIfAbsent(
                eq(11L), eq("ACCOUNT"), anyString(), anyString(), hashCaptor.capture());
        assertThat(hashCaptor.getAllValues()).allSatisfy(hash -> assertThat(hash).hasSize(64));
        assertThat(hashCaptor.getAllValues().get(0)).isEqualTo(hashCaptor.getAllValues().get(1));
    }

    @Test
    void rejectsEmbeddedNewlineSoOneRequestItemCannotContainMultipleAccounts() {
        UploadDigitalAssetRequest request = new UploadDigitalAssetRequest();
        request.setVariantId(11L);
        request.setRawAssets(List.of("first\nsecond"));

        assertThatThrownBy(() -> service.uploadAssets(7L, request))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ASSET_LINE_INVALID));
    }

    @Test
    void nonAccountAssetUsesFullLineFingerprintWithoutWritingLongIdentifierColumn() {
        variant.getProduct().setProductType("OTHER");
        UploadDigitalAssetRequest request = new UploadDigitalAssetRequest();
        request.setVariantId(11L);
        request.setRawAssets(List.of("opaque-license-key"));
        when(assetRepository.insertAvailableAssetIfAbsent(
                eq(11L), eq("OTHER"), eq("opaque-license-key"), isNull(), anyString()))
                .thenReturn(1);

        DigitalAssetImportResponse result = service.uploadAssets(7L, request);

        assertThat(result.addedCount()).isEqualTo(1);
        verify(assetRepository).insertAvailableAssetIfAbsent(
                eq(11L), eq("OTHER"), eq("opaque-license-key"), isNull(), anyString());
    }

    @Test
    void bulkDeleteRemovesOnlyAvailableRowsAndAdjustsStock() {
        when(assetRepository.deleteAvailableAssetsByVariantId(11L)).thenReturn(4);

        int deleted = service.deleteAllAvailableAssets(7L, 11L);

        assertThat(deleted).isEqualTo(4);
        verify(variantRepository).incrementStockCount(11L, -4);
    }
}
