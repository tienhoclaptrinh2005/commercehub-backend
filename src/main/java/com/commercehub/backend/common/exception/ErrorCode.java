package com.commercehub.backend.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    // -- 1. Lỗi Hệ Thống Chung --
    UNCATEGORIZED_EXCEPTION(500, "Lỗi hệ thống không xác định, vui lòng thử lại sau!", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_KEY(400, "Mã lỗi không tồn tại trong hệ thống!", HttpStatus.BAD_REQUEST),

    // -- 2. Lỗi Xác Thực & Phân Quyền (Auth/User) --
    UNAUTHENTICATED(401, "Vui lòng đăng nhập để tiếp tục!", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(403, "Bạn không có quyền thực hiện hành động này!", HttpStatus.FORBIDDEN),
    EMAIL_ALREADY_EXISTS(400, "Email này đã được sử dụng!", HttpStatus.BAD_REQUEST),
    USER_NOT_FOUND(404, "Không tìm thấy người dùng!", HttpStatus.NOT_FOUND),
    INVALID_CREDENTIALS(400, "Email hoặc mật khẩu không chính xác!", HttpStatus.BAD_REQUEST),
    PASSWORD_NOT_MATCH(400, "Mật khẩu xác nhận không khớp!", HttpStatus.BAD_REQUEST),

    // -- 3. Lỗi Danh Mục (Chuẩn bị cho bước tiếp theo) --
    CATEGORY_NOT_FOUND(404, "Không tìm thấy danh mục này!", HttpStatus.NOT_FOUND),
    CATEGORY_ALREADY_EXISTS(400, "Tên danh mục hoặc đường dẫn (slug) đã tồn tại!", HttpStatus.BAD_REQUEST);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}