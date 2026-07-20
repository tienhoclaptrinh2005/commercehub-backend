package com.commercehub.backend.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    // Lỗi Hệ Thống Chung
    UNCATEGORIZED_EXCEPTION(500, "Lỗi hệ thống không xác định, vui lòng thử lại sau!", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_KEY(400, "Mã lỗi không tồn tại trong hệ thống!", HttpStatus.BAD_REQUEST),

    // Lỗi Xác Thực & Phân Quyền (Auth/User)
    UNAUTHENTICATED(401, "Vui lòng đăng nhập để tiếp tục!", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(403, "Bạn không có quyền thực hiện hành động này!", HttpStatus.FORBIDDEN),
    EMAIL_ALREADY_EXISTS(400, "Email này đã được sử dụng!", HttpStatus.BAD_REQUEST),
    PHONE_ALREADY_EXISTS(400, "Số điện thoại này đã được sử dụng!", HttpStatus.BAD_REQUEST),
    USER_NOT_FOUND(404, "Không tìm thấy người dùng!", HttpStatus.NOT_FOUND),
    INVALID_CREDENTIALS(400, "Email hoặc mật khẩu không chính xác!", HttpStatus.BAD_REQUEST),
    PASSWORD_NOT_MATCH(400, "Mật khẩu xác nhận không khớp!", HttpStatus.BAD_REQUEST),
    INVALID_REFRESH_TOKEN(400, "Refresh Token không hợp lệ hoặc đã bị thu hồi!", HttpStatus.BAD_REQUEST),
    REFRESH_TOKEN_EXPIRED(401, "Refresh Token đã hết hạn. Vui lòng đăng nhập lại!", HttpStatus.UNAUTHORIZED),
    USERNAME_ALREADY_EXISTS(400,"Tên đăng nhập (username) đã tồn tại!",HttpStatus.BAD_REQUEST),
    INVALID_USERNAME_FORMAT(400, "Username không hợp lệ! (Từ 3-100 ký tự, không chứa khoảng trắng và ký tự đặc biệt)", HttpStatus.BAD_REQUEST),
    ACCOUNT_LOCKED(403, "Tài khoản của bạn đã bị khóa!", HttpStatus.FORBIDDEN),
    CANNOT_CHANGE_GOOGLE_PASSWORD(400, "Tài khoản liên kết với Google không thể thực hiện đổi mật khẩu tại đây!", HttpStatus.BAD_REQUEST),
    PASSWORD_SAME_AS_OLD( 400, "Mật khẩu mới không được trùng mật khẩu cũ!", HttpStatus.BAD_REQUEST),



    // Lỗi Danh Mục
    CATEGORY_NOT_FOUND(404, "Không tìm thấy danh mục này!", HttpStatus.NOT_FOUND),
    CATEGORY_ALREADY_EXISTS(400, "Tên danh mục hoặc đường dẫn (slug) đã tồn tại!", HttpStatus.BAD_REQUEST),

    // Lỗi Shop
    SHOP_NOT_FOUND(404, "Không tìm thấy gian hàng!", HttpStatus.NOT_FOUND),
    SHOP_ALREADY_EXISTS(400, "Tên gian hàng đã tồn tại, vui lòng chọn tên khác!", HttpStatus.BAD_REQUEST),
    SHOP_CREATION_NOT_ALLOWED(403, "Cấp độ tài khoản của bạn chưa đủ điều kiện để mở gian hàng!", HttpStatus.FORBIDDEN),
    SHOP_LIMIT_REACHED(400, "Bạn đã đạt số lượng gian hàng tối đa cho phép của cấp độ hiện tại!", HttpStatus.BAD_REQUEST),
    INVALID_STATUS(400, "Trạng thái không hợp lệ! (Chỉ chấp nhận ACTIVE, INACTIVE, BANNED)", HttpStatus.BAD_REQUEST),
    SHOP_UNAUTHORIZED(403, "Gian hàng của bạn đang bị khóa hoặc chưa được phê duyệt!", HttpStatus.FORBIDDEN),
    USER_ALREADY_HAS_SHOP(400, "Mỗi tài khoản chỉ được phép mở duy nhất 1 gian hàng!", HttpStatus.BAD_REQUEST),

    // product
    PRODUCT_ALREADY_EXISTS(400, "Sản phẩm này đã tồn tại trong gian hàng của bạn!", HttpStatus.BAD_REQUEST),
    PRODUCT_NOT_FOUND(404, "Sản phẩm không tồn tại hoặc đã bị xóa!", HttpStatus.NOT_FOUND),
    PRODUCT_LIMIT_REACHED(403, "Gian hàng của bạn đã đạt giới hạn số lượng sản phẩm tối đa cho phép của cấp độ hiện tại!", HttpStatus.FORBIDDEN),
    PRE_ORDER_CONFIG_NOT_FOUND(404, "Chưa thiết lập cấu hình đặt trước!", HttpStatus.NOT_FOUND),
    RECORD_NOT_FOUND(404 , "Không tìm thấy bản ghi!", HttpStatus.NOT_FOUND),
    CANNOT_DELETE_SOLD_ASSET (400, "Tài khoản đã được bán, không thể xóa khỏi lịch sử!",HttpStatus.BAD_REQUEST),
    INVALID_DELIVERY_TYPE_FOR_ASSET(400, "Bạn chỉ có thể nạp kho cho sản phẩm có hình thức Giao hàng tức thì (INSTANT)!" , HttpStatus.BAD_REQUEST),
    ACCOUNT_IN_TRANSACTION_OR_LOCKED(400,"Tài khoản đang giao dịch hoặc bị khóa, không thể xóa lúc này!",HttpStatus.BAD_REQUEST),
    INVALID_DELIVERY_TYPE_FOR_CONFIG(400, "Sản phẩm không thuộc loại đặt trước (PRE_ORDER) nên không thể cấu hình.", HttpStatus.BAD_REQUEST),

    // Review
    CANNOT_REVIEW_OWN_PRODUCT(400, "Bạn không thể đánh giá hoặc mua sản phẩm của chính mình!", HttpStatus.BAD_REQUEST),
    REVIEW_ALREADY_EXISTS(400, "Bạn đã đánh giá sản phẩm này rồi!", HttpStatus.BAD_REQUEST),


    // ví
    WALLET_NOT_FOUND(400 , "Không tìm thấy ví người dùng !", HttpStatus.BAD_REQUEST),
    INSUFFICIENT_BALANCE(400,"Số dư không đủ !" , HttpStatus.BAD_REQUEST),
    INVALID_AMOUNT(400,"Số tiền không hợp lệ !" , HttpStatus.BAD_REQUEST),
    INSUFFICIENT_HOLD_BALANCE(400, "Số dư giữ không đủ ", HttpStatus.BAD_REQUEST),
    WALLET_INACTIVE(403, "Ví không hoạt động !", HttpStatus.FORBIDDEN),

    //oder
    SHOP_SUSPENDED(403, "Gian hàng hiện đang bị tạm khóa, không thể đặt hàng!", HttpStatus.FORBIDDEN),
    PRODUCT_NOT_AVAILABLE(400, "Sản phẩm hiện không đủ số lượng hoặc đã hết hàng!", HttpStatus.BAD_REQUEST),
    ORDER_NOT_FOUND(404, "Không tìm thấy đơn hàng!", HttpStatus.NOT_FOUND),
    CANNOT_BUY_OWN_PRODUCT(400, "Bạn không thể mua sản phẩm của chính mình!", HttpStatus.BAD_REQUEST),


    ;




    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}