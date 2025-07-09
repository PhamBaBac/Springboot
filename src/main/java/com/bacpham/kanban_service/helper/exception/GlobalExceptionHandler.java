package com.bacpham.kanban_service.helper.exception;

import com.bacpham.kanban_service.dto.request.ApiResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
//import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.Map;
import java.util.Objects;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    private static final String MIN_ATTRIBUTE = "min";

    @ExceptionHandler(value = Exception.class)
    ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("Exception: ", e);
        ApiResponse<Void> apiResponse = new ApiResponse<>();
        apiResponse.setCode(ErrorCode.UNCATEGORIZED.getCode());
        apiResponse.setMessage(ErrorCode.UNCATEGORIZED.getMessage());

        return ResponseEntity.badRequest().body(apiResponse);
    }

    @ExceptionHandler(value = AppException.class)
    ResponseEntity<ApiResponse<Void>> handleAppException(AppException appException) {
        log.error("AppException: ", appException);
        ErrorCode errorCode = appException.getErrorCode();
        ApiResponse<Void> apiResponse = new ApiResponse<>();
        apiResponse.setCode(errorCode.getCode());
        apiResponse.setMessage(errorCode.getMessage());

        return ResponseEntity.status(errorCode.getStatusCode()).body(apiResponse);
    }
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(AccessDeniedException exception) {
        log.error("Access Denied Exception: ", exception);
        ErrorCode errorCode = ErrorCode.UNAUTHORIZED;
        ApiResponse<Void> apiResponse = new ApiResponse<>();
        apiResponse.setCode(errorCode.getCode());
        apiResponse.setMessage(errorCode.getMessage());
        return ResponseEntity.status(errorCode.getStatusCode()).body(apiResponse);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handlingValidation(MethodArgumentNotValidException exception) {
        log.error("MethodArgumentNotValidException: ", exception);

        ErrorCode errorCode = ErrorCode.INVALID_KEY;
        Map<String, Object> attributes = null;

        for (var error : exception.getBindingResult().getFieldErrors()) {
            String messageKey = error.getDefaultMessage(); // e.g. "INVALID_SIZE_FIRST_NAME"
            try {
                errorCode = ErrorCode.valueOf(messageKey);

                // Lấy thuộc tính từ annotation như {max}, {min}, {value}...
                var constraintDescriptor = error.unwrap(jakarta.validation.ConstraintViolation.class)
                        .getConstraintDescriptor();
                attributes = constraintDescriptor.getAttributes();

                break; // chỉ lấy lỗi đầu tiên
            } catch (Exception e) {
                log.warn("Cannot resolve ErrorCode for key '{}', using default INVALID_KEY", messageKey);
            }
        }

        String finalMessage = (attributes != null)
                ? mapAttribute(errorCode.getMessage(), attributes)
                : errorCode.getMessage();

        ApiResponse<Void> apiResponse = new ApiResponse<>();
        apiResponse.setCode(errorCode.getCode());
        apiResponse.setMessage(finalMessage);

        return ResponseEntity.badRequest().body(apiResponse);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException e) {
        ApiResponse<Void> response = new ApiResponse<>();
        response.setCode(ErrorCode.INVALID_KEY.getCode());
        response.setMessage("Invalid input: " + e.getMessage());
        return ResponseEntity.badRequest().body(response);
    }
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex) {
        log.error("Bad credentials: ", ex);
        ErrorCode errorCode = ErrorCode.INVALID_CREDENTIALS;
        ApiResponse<Void> response = new ApiResponse<>();
        response.setCode(errorCode.getCode());
        response.setMessage(errorCode.getMessage());
        return ResponseEntity.status(errorCode.getStatusCode()).body(response);
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
