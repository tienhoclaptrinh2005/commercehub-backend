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
    USERNAME_CHANGE_LIMIT_REACHED(409, "Username chỉ được đổi một lần duy nhất!", HttpStatus.CONFLICT),
    ACCOUNT_LOCKED(403, "Tài khoản của bạn đã bị khóa!", HttpStatus.FORBIDDEN),
    CANNOT_CHANGE_GOOGLE_PASSWORD(400, "Tài khoản liên kết với Google không thể thực hiện đổi mật khẩu tại đây!", HttpStatus.BAD_REQUEST),
    PASSWORD_SAME_AS_OLD( 400, "Mật khẩu mới không được trùng mật khẩu cũ!", HttpStatus.BAD_REQUEST),



    // Lỗi Danh Mục
    CATEGORY_NOT_FOUND(404, "Không tìm thấy danh mục này!", HttpStatus.NOT_FOUND),
    CATEGORY_ALREADY_EXISTS(400, "Tên danh mục hoặc đường dẫn (slug) đã tồn tại!", HttpStatus.BAD_REQUEST),
    CATEGORY_PARENT_INVALID(400, "Danh mục cha không hợp lệ. Hệ thống chỉ hỗ trợ tối đa 2 cấp danh mục!", HttpStatus.BAD_REQUEST),
    CATEGORY_MUST_BE_LEAF(400, "Sản phẩm chỉ được gắn với danh mục con!", HttpStatus.BAD_REQUEST),

    // Lỗi Shop
    SHOP_NOT_FOUND(404, "Không tìm thấy gian hàng!", HttpStatus.NOT_FOUND),
    SHOP_ALREADY_EXISTS(400, "Tên gian hàng đã tồn tại, vui lòng chọn tên khác!", HttpStatus.BAD_REQUEST),
    SHOP_CREATION_NOT_ALLOWED(403, "Cấp độ tài khoản của bạn chưa đủ điều kiện để mở gian hàng!", HttpStatus.FORBIDDEN),
    SHOP_CREATION_ROLE_NOT_ALLOWED(403, "Chỉ tài khoản BUYER được phép đăng ký mở gian hàng!", HttpStatus.FORBIDDEN),
    SHOP_LIMIT_REACHED(400, "Bạn đã đạt số lượng gian hàng tối đa cho phép của cấp độ hiện tại!", HttpStatus.BAD_REQUEST),
    INVALID_STATUS(400, "Trạng thái không hợp lệ!", HttpStatus.BAD_REQUEST),
    SHOP_UNAUTHORIZED(403, "Gian hàng của bạn đang bị khóa hoặc chưa được phê duyệt!", HttpStatus.FORBIDDEN),
    USER_ALREADY_HAS_SHOP(400, "Mỗi tài khoản chỉ được phép mở duy nhất 1 gian hàng!", HttpStatus.BAD_REQUEST),
    SHOP_STATUS_TRANSITION_INVALID(409, "Không thể chuyển gian hàng giữa hai trạng thái này!", HttpStatus.CONFLICT),
    SHOP_REVIEW_CONFLICT(409, "Hồ sơ gian hàng vừa được xử lý bởi quản trị viên khác. Vui lòng tải lại!", HttpStatus.CONFLICT),
    SELLER_IDENTITY_LOCKED(409, "Tên hiển thị, username và tên gian hàng của seller không thể thay đổi!", HttpStatus.CONFLICT),

    // product
    PRODUCT_ALREADY_EXISTS(400, "Sản phẩm này đã tồn tại trong gian hàng của bạn!", HttpStatus.BAD_REQUEST),
    PRODUCT_NOT_FOUND(404, "Sản phẩm không tồn tại hoặc đã bị xóa!", HttpStatus.NOT_FOUND),
    PRODUCT_LIMIT_REACHED(403, "Gian hàng của bạn đã đạt giới hạn số lượng sản phẩm tối đa cho phép của cấp độ hiện tại!", HttpStatus.FORBIDDEN),
    VARIANT_ALREADY_EXISTS(400, "Tên gói/biến thể đã tồn tại trong sản phẩm này!", HttpStatus.BAD_REQUEST),
    PRODUCT_VARIANT_LIMIT_REACHED(400, "Mỗi sản phẩm chỉ được tạo tối đa 5 biến thể!", HttpStatus.BAD_REQUEST),
    VARIANT_INVALID_STATUS(400, "Trạng thái biến thể không hợp lệ! Chỉ chấp nhận ACTIVE hoặc INACTIVE.", HttpStatus.BAD_REQUEST),
    PRE_ORDER_CONFIG_NOT_FOUND(404, "Chưa thiết lập cấu hình đặt trước!", HttpStatus.NOT_FOUND),
    RECORD_NOT_FOUND(404 , "Không tìm thấy bản ghi!", HttpStatus.NOT_FOUND),
    CANNOT_DELETE_SOLD_ASSET (400, "Tài khoản đã được bán, không thể xóa khỏi lịch sử!",HttpStatus.BAD_REQUEST),
    INVALID_DELIVERY_TYPE_FOR_ASSET(400, "Bạn chỉ có thể nạp kho cho sản phẩm có hình thức Giao hàng tức thì (INSTANT)!" , HttpStatus.BAD_REQUEST),
    ACCOUNT_IN_TRANSACTION_OR_LOCKED(400,"Tài khoản đang giao dịch hoặc bị khóa, không thể xóa lúc này!",HttpStatus.BAD_REQUEST),
    ASSET_LINE_INVALID(400, "Mỗi tài khoản/key trong kho phải nằm trên đúng một dòng!", HttpStatus.BAD_REQUEST),
    INVALID_DELIVERY_TYPE_FOR_CONFIG(400, "Sản phẩm không thuộc loại đặt trước (PRE_ORDER) nên không thể cấu hình.", HttpStatus.BAD_REQUEST),
    PRODUCT_NOT_PURCHASED(403, "Bạn chưa mua sản phẩm này hoặc đơn hàng chưa được giao thành công!",HttpStatus.FORBIDDEN),
    IMAGE_STORAGE_UNAVAILABLE(503, "Kho ảnh chưa được cấu hình hoặc đang tạm thời không khả dụng!", HttpStatus.SERVICE_UNAVAILABLE),
    IMAGE_UPLOAD_INVALID_TYPE(400, "Ảnh tải lên phải là WebP hợp lệ!", HttpStatus.BAD_REQUEST),
    IMAGE_UPLOAD_TOO_LARGE(413, "Dung lượng ảnh vượt quá giới hạn cho phép!", HttpStatus.PAYLOAD_TOO_LARGE),
    IMAGE_UPLOAD_INVALID_DIMENSIONS(400, "Ảnh sản phẩm phải có tỷ lệ 4:3 và kích thước 1200 x 900 px!", HttpStatus.BAD_REQUEST),
    IMAGE_UPLOAD_METADATA_NOT_ALLOWED(400, "Ảnh sản phẩm không được chứa EXIF/XMP hoặc dữ liệu ảnh động!", HttpStatus.BAD_REQUEST),
    IMAGE_UPLOAD_NOT_FOUND(404, "Không tìm thấy ảnh vừa tải lên kho lưu trữ!", HttpStatus.NOT_FOUND),
    IMAGE_UPLOAD_REFERENCE_INVALID(400, "Tham chiếu ảnh sản phẩm không hợp lệ!", HttpStatus.BAD_REQUEST),
    IMAGE_UPLOAD_ACCESS_DENIED(403, "Bạn không có quyền sử dụng ảnh này!", HttpStatus.FORBIDDEN),
    IMAGE_UPLOAD_TOO_FAST(429, "Bạn tạo yêu cầu tải ảnh quá nhanh. Vui lòng chờ vài giây!", HttpStatus.TOO_MANY_REQUESTS),
    IMAGE_UPLOAD_RATE_LIMITED(429, "Bạn đã tạo quá nhiều yêu cầu tải ảnh trong 15 phút!", HttpStatus.TOO_MANY_REQUESTS),
    IMAGE_UPLOAD_RATE_LIMIT_UNAVAILABLE(503, "Hệ thống chống spam tải ảnh đang tạm thời không khả dụng!", HttpStatus.SERVICE_UNAVAILABLE),


    // Review
    CANNOT_REVIEW_OWN_PRODUCT(400, "Bạn không thể đánh giá hoặc mua sản phẩm của chính mình!", HttpStatus.BAD_REQUEST),
    REVIEW_ALREADY_EXISTS(400, "Bạn đã đánh giá sản phẩm này rồi!", HttpStatus.BAD_REQUEST),
    REVIEW_NOT_FOUND(404, "Không tìm thấy đánh giá!", HttpStatus.NOT_FOUND),


    // ví
    WALLET_NOT_FOUND(400 , "Không tìm thấy ví người dùng !", HttpStatus.BAD_REQUEST),
    INSUFFICIENT_BALANCE(400,"Số dư không đủ !" , HttpStatus.BAD_REQUEST),
    INVALID_AMOUNT(400,"Số tiền không hợp lệ !" , HttpStatus.BAD_REQUEST),
    INSUFFICIENT_HOLD_BALANCE(400, "Số dư giữ không đủ ", HttpStatus.BAD_REQUEST),
    WALLET_INACTIVE(403, "Ví không hoạt động !", HttpStatus.FORBIDDEN),
    DEPOSIT_AMOUNT_MISMATCH(400, "Số tiền cổng thanh toán báo về không khớp với số tiền của đơn nạp!", HttpStatus.BAD_REQUEST),
    INVALID_PAYMENT_NOTIFICATION(400, "Thông báo thanh toán không hợp lệ!", HttpStatus.BAD_REQUEST),
    PAYMENT_WEBHOOK_UNAUTHORIZED(401, "Webhook thanh toán không được xác thực!", HttpStatus.UNAUTHORIZED),
    PAYMENT_TRANSACTION_ALREADY_PROCESSED(409, "Giao dịch từ cổng thanh toán đã được xử lý!", HttpStatus.CONFLICT),
    DEPOSIT_ACTIVE_EXISTS(409, "Bạn đang có một mã QR nạp tiền còn hiệu lực. Vui lòng hoàn tất hoặc chờ mã hết hạn!", HttpStatus.CONFLICT),
    DEPOSIT_CREATE_TOO_FAST(429, "Bạn thao tác quá nhanh. Vui lòng chờ vài giây rồi thử lại!", HttpStatus.TOO_MANY_REQUESTS),
    DEPOSIT_RATE_LIMITED(429, "Bạn đã tạo quá nhiều mã QR trong 15 phút. Vui lòng thử lại sau!", HttpStatus.TOO_MANY_REQUESTS),
    DEPOSIT_DAILY_LIMIT_REACHED(429, "Bạn đã đạt giới hạn tạo mã QR nạp tiền trong ngày!", HttpStatus.TOO_MANY_REQUESTS),

    //oder
    SHOP_SUSPENDED(403, "Gian hàng hiện đang bị tạm khóa, không thể đặt hàng!", HttpStatus.FORBIDDEN),
    PRODUCT_NOT_AVAILABLE(400, "Sản phẩm hiện không đủ số lượng hoặc đã hết hàng!", HttpStatus.BAD_REQUEST),
    ORDER_NOT_FOUND(404, "Không tìm thấy đơn hàng!", HttpStatus.NOT_FOUND),
    CANNOT_BUY_OWN_PRODUCT(400, "Bạn không thể mua sản phẩm của chính mình!", HttpStatus.BAD_REQUEST),


    INVALID_REQUEST(400, "Dữ liệu yêu cầu không hợp lệ!", HttpStatus.BAD_REQUEST),
    INVALID_DELIVERY_TYPE(400, "Loại hình giao hàng không hợp lệ! Chỉ chấp nhận INSTANT hoặc PRE_ORDER.", HttpStatus.BAD_REQUEST),


    ORDER_ACCESS_DENIED(403, "Bạn không có quyền thao tác trên đơn hàng này!", HttpStatus.FORBIDDEN),

    // Giỏ hàng
    CART_ITEM_NOT_FOUND(404, "Không tìm thấy sản phẩm này trong giỏ hàng của bạn!", HttpStatus.NOT_FOUND),
    CART_EMPTY(400, "Giỏ hàng của bạn đang trống!", HttpStatus.BAD_REQUEST),

    ORDER_NOT_WAITING_APPROVAL(400, "Đơn hàng không ở trạng thái chờ duyệt!", HttpStatus.BAD_REQUEST),
    ORDER_APPROVAL_TIMEOUT(400, "Đơn hàng đã quá thời hạn duyệt!", HttpStatus.BAD_REQUEST),


    VARIANT_INACTIVE(400, "Phân loại sản phẩm không hoạt động hoặc đã bị khóa", HttpStatus.BAD_REQUEST),
    OUT_OF_STOCK(400, "Sản phẩm này hiện đã hết hàng!", HttpStatus.BAD_REQUEST),
    ORDER_CANNOT_CANCEL(400, "Không thể hủy đơn hàng ở trạng thái này!", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED_ACTION(403, "Bạn không có quyền thực hiện hành động này!", HttpStatus.FORBIDDEN),
    ORDER_NOT_PROCESSING(400, "Đơn hàng không ở trạng thái đang xử lý!", HttpStatus.BAD_REQUEST),
    ITEMS_MUST_BE_SAME_SHOP(400, "Tất cả sản phẩm trong đơn hàng phải thuộc cùng một gian hàng!", HttpStatus.BAD_REQUEST),
    IDEMPOTENCY_KEY_REUSED(409, "Idempotency key đã được dùng với nội dung checkout khác!", HttpStatus.CONFLICT),
    CHECKOUT_ALREADY_PROCESSING(409, "Checkout với idempotency key này đang được xử lý!", HttpStatus.CONFLICT),

    // Hệ thống & Cấu hình
    SYSTEM_CONFIG_ERROR(500, "Lỗi cấu hình hệ thống! Vui lòng liên hệ quản trị viên.", HttpStatus.INTERNAL_SERVER_ERROR),
    HOLD_RELEASE_INVALID_STATUS(400, "Trạng thái giữ tiền không hợp lệ để thực hiện thao tác này!", HttpStatus.BAD_REQUEST),


    // Fee Module
    FEE_CONFIG_NOT_FOUND(500, "Không tìm thấy cấu hình phí sàn đang hoạt động!", HttpStatus.INTERNAL_SERVER_ERROR),
    FEE_LEDGER_NOT_FOUND(404, "Không tìm thấy bản ghi phí sàn!", HttpStatus.NOT_FOUND),
    FEE_LEDGER_ALREADY_PROCESSED(400, "Bản ghi phí này đã được xử lý, không thể thay đổi!", HttpStatus.BAD_REQUEST),
    FEE_LEDGER_INVALID_STATUS(400, "Trạng thái phí sàn không hợp lệ để thực hiện thao tác này!", HttpStatus.BAD_REQUEST),
    FEE_CONFIG_ALREADY_ACTIVE(400, "Đã có cấu hình phí đang hoạt động. Hãy deactivate cấu hình cũ trước!", HttpStatus.BAD_REQUEST),
    FEE_CALCULATION_ERROR(500, "Lỗi tính toán phí sàn, vui lòng kiểm tra cấu hình!", HttpStatus.INTERNAL_SERVER_ERROR),
    FEE_INVARIANT_VIOLATED(500, "Lỗi bất biến phí sàn: holdAmount ≠ feeAmount + sellerNetAmount!", HttpStatus.INTERNAL_SERVER_ERROR),
    SHOP_FEE_SUMMARY_NOT_FOUND(404, "Không tìm thấy tổng hợp phí sàn của shop!", HttpStatus.NOT_FOUND),


    // Complaint / Dispute Module
    HOLD_RELEASE_NOT_FOUND(404, "Không tìm thấy bản ghi giữ tiền cho sản phẩm này!", HttpStatus.NOT_FOUND),
    ORDER_ITEM_NOT_FOUND(404, "Không tìm thấy dòng sản phẩm trong đơn hàng!", HttpStatus.NOT_FOUND),
    COMPLAINT_NOT_ALLOWED(400, "Không thể khiếu nại lúc này! Sản phẩm phải đang ở trạng thái HOLDING và chưa hết thời hạn.", HttpStatus.BAD_REQUEST),
    HOLD_RELEASE_NOT_COMPLAINED(400, "Sản phẩm này chưa ở trạng thái khiếu nại (COMPLAINED)!", HttpStatus.BAD_REQUEST),
    HOLD_RELEASE_NOT_WARRANTY(400, "Sản phẩm này không đang trong quá trình bảo hành (WARRANTY_IN_PROGRESS)!", HttpStatus.BAD_REQUEST),
    HOLD_RELEASE_NOT_DISPUTED(400, "Sản phẩm này chưa ở trạng thái tranh chấp (DISPUTED) để Admin phán xử!", HttpStatus.BAD_REQUEST),


    DISPUTE_NOT_FOUND(404, "Không tìm thấy tranh chấp!", HttpStatus.NOT_FOUND),
    DISPUTE_ALREADY_EXISTS(409, "OrderItem này đã có hồ sơ khiếu nại/tranh chấp!", HttpStatus.CONFLICT),
    DISPUTE_INVALID_STATUS(400, "Trạng thái tranh chấp không hợp lệ để thực hiện thao tác này!", HttpStatus.BAD_REQUEST),
    DISPUTE_ACCESS_DENIED(403, "Bạn không có quyền truy cập hoặc thao tác tranh chấp này!", HttpStatus.FORBIDDEN),
    DISPUTE_WITHDRAW_NOT_ALLOWED(409, "Chỉ có thể tự hủy khiếu nại đang chờ seller hoặc đang được bảo hành. Mỗi sản phẩm chỉ được khiếu nại một lần!", HttpStatus.CONFLICT),
    DISPUTE_SELLER_RESPONSE_DEADLINE_EXPIRED(409, "Seller đã hết thời hạn phản hồi. Hệ thống sẽ tự động hoàn tiền cho buyer!", HttpStatus.CONFLICT),
    DISPUTE_WARRANTY_DEADLINE_EXPIRED(409, "Thời hạn bảo hành đã hết. Hệ thống sẽ tự động hoàn tiền cho buyer!", HttpStatus.CONFLICT),
    DISPUTE_BUYER_CONFIRMATION_DEADLINE_EXPIRED(409, "Buyer đã hết thời hạn xác nhận. Hệ thống sẽ tự động đóng khiếu nại!", HttpStatus.CONFLICT),



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
