package com.commercehub.backend.common.exception;

import com.commercehub.backend.common.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(value = AppException.class)
    public ResponseEntity<ApiResponse<Void>> handlingAppException(AppException exception) {
        ErrorCode errorCode = exception.getErrorCode();

        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(false)
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .build();

        return ResponseEntity.status(errorCode.getHttpStatus()).body(response);
    }

    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handlingValidation(MethodArgumentNotValidException exception) {

        String errorMessage = exception.getBindingResult().getAllErrors().get(0).getDefaultMessage();

        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(false)
                .code(400)
                .message(errorMessage)
                .build();

        return ResponseEntity.status(org.springframework.http.HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(value = BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handlingBadCredentialsException(BadCredentialsException exception) {
            ApiResponse<Void> response = ApiResponse.<Void>builder()
                    .success(false)
                    .code(ErrorCode.INVALID_CREDENTIALS.getCode())
                    .message(ErrorCode.INVALID_CREDENTIALS.getMessage())
                    .build();

        return ResponseEntity.status(ErrorCode.INVALID_CREDENTIALS.getHttpStatus()).body(response);
    }



    @ExceptionHandler(value = RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handlingRuntimeException(RuntimeException exception) {

        exception.printStackTrace();

        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .success(false)
                .code(ErrorCode.UNCATEGORIZED_EXCEPTION.getCode())
                .message(ErrorCode.UNCATEGORIZED_EXCEPTION.getMessage())
                .build();

        return ResponseEntity.status(ErrorCode.UNCATEGORIZED_EXCEPTION.getHttpStatus()).body(response);
    }
}