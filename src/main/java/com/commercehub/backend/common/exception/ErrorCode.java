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
    INVALID_STATUS(400, "Trạng thái không hợp lệ! (Chỉ chấp nhận ACTIVE, INACTIVE, BANNED)", HttpStatus.BAD_REQUEST);


    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}