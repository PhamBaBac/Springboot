package com.bacpham.kanban_service.helper.exception;

import com.bacpham.kanban_service.dto.request.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.Map;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(value = Exception.class)
    ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("Unhandled Exception [{}]: {}", e.getClass().getSimpleName(), e.getMessage());
        ApiResponse<Void> apiResponse = new ApiResponse<>();
        apiResponse.setCode(ErrorCode.UNCATEGORIZED.getCode());
        apiResponse.setMessage("Đã xảy ra lỗi trong quá trình xử lý. Vui lòng thử lại sau.");

        return ResponseEntity.internalServerError().body(apiResponse);
    }

    @ExceptionHandler(value = AppException.class)
    ResponseEntity<ApiResponse<Void>> handleAppException(AppException appException) {
        ErrorCode errorCode = appException.getErrorCode();
        log.warn("AppException [{}]: {}", errorCode != null ? errorCode.getCode() : "N/A", appException.getMessage());
        ApiResponse<Void> apiResponse = new ApiResponse<>();
        apiResponse.setCode(errorCode != null ? errorCode.getCode() : ErrorCode.UNCATEGORIZED.getCode());
        apiResponse.setMessage(errorCode != null ? errorCode.getMessage() : appException.getMessage());

        return ResponseEntity.status(errorCode != null ? errorCode.getStatusCode() : HttpStatus.INTERNAL_SERVER_ERROR)
                .body(apiResponse);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex) {
        log.warn("Bad credentials: {}", ex.getMessage());
        ErrorCode errorCode = ErrorCode.INVALID_CREDENTIALS;
        ApiResponse<Void> response = new ApiResponse<>();
        response.setCode(errorCode.getCode());
        response.setMessage(errorCode.getMessage());
        return ResponseEntity.status(errorCode.getStatusCode()).body(response);
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleUsernameNotFound(UsernameNotFoundException ex) {
        log.warn("User not found: {}", ex.getMessage());
        ErrorCode errorCode = ErrorCode.USER_NOT_FOUND;
        ApiResponse<Void> response = new ApiResponse<>();
        response.setCode(errorCode.getCode());
        response.setMessage(errorCode.getMessage());
        return ResponseEntity.status(errorCode.getStatusCode()).body(response);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(AccessDeniedException exception) {
        log.warn("Access Denied: {}", exception.getMessage());
        ErrorCode errorCode = ErrorCode.UNAUTHORIZED;
        ApiResponse<Void> apiResponse = new ApiResponse<>();
        apiResponse.setCode(errorCode.getCode());
        apiResponse.setMessage(errorCode.getMessage());
        return ResponseEntity.status(errorCode.getStatusCode()).body(apiResponse);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handlingValidation(MethodArgumentNotValidException exception) {
        ErrorCode errorCode = ErrorCode.INVALID_KEY;
        Map<String, Object> attributes = null;

        for (var error : exception.getBindingResult().getFieldErrors()) {
            String messageKey = error.getDefaultMessage();
            try {
                errorCode = ErrorCode.valueOf(messageKey);
                var constraintDescriptor = error.unwrap(jakarta.validation.ConstraintViolation.class)
                        .getConstraintDescriptor();
                attributes = constraintDescriptor.getAttributes();
                break;
            } catch (Exception e) {
                // messageKey is not an ErrorCode enum name, use default or raw message
            }
        }

        String finalMessage = (attributes != null)
                ? mapAttribute(errorCode.getMessage(), attributes)
                : errorCode.getMessage();

        log.warn("Validation failed: {}", finalMessage);

        ApiResponse<Void> apiResponse = new ApiResponse<>();
        apiResponse.setCode(errorCode.getCode());
        apiResponse.setMessage(finalMessage);

        return ResponseEntity.badRequest().body(apiResponse);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException e) {
        log.warn("Constraint violation: {}", e.getMessage());
        ApiResponse<Void> response = new ApiResponse<>();
        response.setCode(ErrorCode.INVALID_KEY.getCode());
        response.setMessage("Dữ liệu không hợp lệ: " + e.getMessage());
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        log.warn("Malformed JSON request: {}", ex.getMessage());
        ApiResponse<Void> response = new ApiResponse<>();
        response.setCode(ErrorCode.INVALID_INPUT.getCode());
        response.setMessage("Dữ liệu gửi lên không đúng định dạng JSON.");
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        log.warn("HTTP Method not supported: {}", ex.getMessage());
        ApiResponse<Void> response = new ApiResponse<>();
        response.setCode(ErrorCode.INVALID_KEY.getCode());
        response.setMessage("Phương thức " + ex.getMethod() + " không được hỗ trợ.");
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(response);
    }

    private String mapAttribute(String message, Map<String, Object> attributes) {
        if (message == null || attributes == null) return message;

        String result = message;

        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            String key = entry.getKey();
            String value = String.valueOf(entry.getValue());

            if (result.contains("{" + key + "}")) {
                result = result.replace("{" + key + "}", value);
            }
        }

        return result;
    }
}
