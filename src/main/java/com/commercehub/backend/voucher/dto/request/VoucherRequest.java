package com.commercehub.backend.voucher.dto.request;

import com.commercehub.backend.voucher.entity.VoucherDiscountType;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Set;

@Data
public class VoucherRequest {
    @NotBlank(message = "Mã giảm giá không được để trống")
    @Pattern(regexp = "[A-Za-z0-9_-]{3,50}", message = "Mã giảm giá phải gồm 3-50 chữ, số, gạch ngang hoặc gạch dưới")
    private String code;

    @Size(max = 255, message = "Mô tả tối đa 255 ký tự")
    private String description;

    @NotNull(message = "Vui lòng chọn loại giảm giá")
    private VoucherDiscountType discountType;

    @NotNull(message = "Giá trị giảm không được để trống")
    @DecimalMin(value = "1", message = "Giá trị giảm phải lớn hơn 0")
    private BigDecimal discountValue;

    @DecimalMin(value = "1", message = "Mức giảm tối đa phải lớn hơn 0")
    private BigDecimal maxDiscountAmount;

    @DecimalMin(value = "0", message = "Giá trị đơn tối thiểu không hợp lệ")
    private BigDecimal minOrderAmount;

    private boolean applyAllProducts = true;
    private Set<@NotNull Long> productIds = Set.of();

    @NotNull(message = "Thời gian bắt đầu không được để trống")
    private OffsetDateTime startsAt;

    @NotNull(message = "Thời gian kết thúc không được để trống")
    private OffsetDateTime expiresAt;

    @NotNull(message = "Số lượt sử dụng không được để trống")
    @Min(value = 1, message = "Số lượt sử dụng phải từ 1 trở lên")
    @Max(value = 1000000, message = "Số lượt sử dụng quá lớn")
    private Integer usageLimit;
}
