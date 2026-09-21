package com.bacpham.kanban_service.helper.exception;

import com.bacpham.kanban_service.dto.request.ApiResponse;
import com.mongodb.MongoException;
import com.mongodb.MongoTimeoutException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Map;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(value = Exception.class)
    ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        if (isConnectionException(e)) {
            log.error("Unhandled wrapped connection error [{}]: {}", e.getClass().getSimpleName(), e.getMessage());
            ErrorCode errorCode = isTimeoutException(e)
                    ? ErrorCode.CONNECTION_TIMEOUT
                    : ErrorCode.DATABASE_CONNECTION_ERROR;
            ApiResponse<Void> apiResponse = new ApiResponse<>();
            apiResponse.setCode(errorCode.getCode());
            apiResponse.setMessage(errorCode.getMessage());
            return ResponseEntity.status(errorCode.getStatusCode()).body(apiResponse);
        }

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

    // ==================================================
    // HTTP Request Parameter, Path, Header & Media Type Handlers
    // ==================================================

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("Method argument type mismatch: parameter '{}', value '{}'", ex.getName(), ex.getValue());
        ApiResponse<Void> response = new ApiResponse<>();
        response.setCode(ErrorCode.INVALID_INPUT.getCode());
        response.setMessage(String.format("Tham số '%s' nhận giá trị '%s' không đúng định dạng (yêu cầu kiểu %s).",
                ex.getName(), ex.getValue(), ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "hợp lệ"));
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingServletRequestParameter(MissingServletRequestParameterException ex) {
        log.warn("Missing request parameter: {}", ex.getParameterName());
        ApiResponse<Void> response = new ApiResponse<>();
        response.setCode(ErrorCode.INVALID_INPUT.getCode());
        response.setMessage(String.format("Thiếu tham số bắt buộc trong yêu cầu: '%s' (kiểu %s).",
                ex.getParameterName(), ex.getParameterType()));
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(MissingPathVariableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingPathVariable(MissingPathVariableException ex) {
        log.warn("Missing path variable: {}", ex.getVariableName());
        ApiResponse<Void> response = new ApiResponse<>();
        response.setCode(ErrorCode.INVALID_KEY.getCode());
        response.setMessage(String.format("Thiếu biến đường dẫn bắt buộc: '%s'.", ex.getVariableName()));
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingRequestHeader(MissingRequestHeaderException ex) {
        log.warn("Missing request header: {}", ex.getHeaderName());
        ApiResponse<Void> response = new ApiResponse<>();
        response.setCode(ErrorCode.INVALID_INPUT.getCode());
        response.setMessage(String.format("Thiếu HTTP Header bắt buộc: '%s'.", ex.getHeaderName()));
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        log.warn("Media type not supported: {}", ex.getContentType());
        ErrorCode errorCode = ErrorCode.UNSUPPORTED_MEDIA_TYPE;
        ApiResponse<Void> response = new ApiResponse<>();
        response.setCode(errorCode.getCode());
        response.setMessage(String.format("Định dạng dữ liệu '%s' không được hỗ trợ. Vui lòng sử dụng đúng Content-Type yêu cầu.",
                ex.getContentType()));
        return ResponseEntity.status(errorCode.getStatusCode()).body(response);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMediaTypeNotAcceptable(HttpMediaTypeNotAcceptableException ex) {
        log.warn("Media type not acceptable: {}", ex.getMessage());
        ApiResponse<Void> response = new ApiResponse<>();
        response.setCode(ErrorCode.INVALID_INPUT.getCode());
        response.setMessage("Máy chủ không thể đáp ứng định dạng dữ liệu (Accept header) được yêu cầu từ client.");
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).body(response);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(NoResourceFoundException ex) {
        log.warn("Resource not found (404): {} {}", ex.getHttpMethod(), ex.getResourcePath());
        ErrorCode errorCode = ErrorCode.RESOURCE_NOT_FOUND;
        ApiResponse<Void> response = new ApiResponse<>();
        response.setCode(errorCode.getCode());
        response.setMessage(String.format("Không tìm thấy đường dẫn hoặc tài nguyên: %s", ex.getResourcePath()));
        return ResponseEntity.status(errorCode.getStatusCode()).body(response);
    }

    // ==================================================
    // File Upload / Multipart Handlers
    // ==================================================

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        log.warn("File upload size exceeded: {}", ex.getMessage());
        ErrorCode errorCode = ErrorCode.FILE_TOO_LARGE;
        ApiResponse<Void> response = new ApiResponse<>();
        response.setCode(errorCode.getCode());
        response.setMessage(errorCode.getMessage());
        return ResponseEntity.status(errorCode.getStatusCode()).body(response);
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMultipartException(MultipartException ex) {
        log.warn("Multipart request error: {}", ex.getMessage());
        ApiResponse<Void> response = new ApiResponse<>();
        response.setCode(ErrorCode.INVALID_INPUT.getCode());
        response.setMessage("Yêu cầu tải lên tệp (Multipart) không hợp lệ hoặc dữ liệu tệp bị gián đoạn.");
        return ResponseEntity.badRequest().body(response);
    }

    // ==================================================
    // General Spring Security Authentication Handler
    // ==================================================

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(AuthenticationException ex) {
        log.warn("Authentication failure [{}]: {}", ex.getClass().getSimpleName(), ex.getMessage());
        ErrorCode errorCode = ErrorCode.UNAUTHENTICATED;
        ApiResponse<Void> response = new ApiResponse<>();
        response.setCode(errorCode.getCode());
        response.setMessage(errorCode.getMessage());
        return ResponseEntity.status(errorCode.getStatusCode()).body(response);
    }

    // ==================================================
    // Database & Connection Exception Handlers
    // ==================================================

    @ExceptionHandler(CannotCreateTransactionException.class)
    public ResponseEntity<ApiResponse<Void>> handleCannotCreateTransaction(CannotCreateTransactionException ex) {
        log.error("Database connection failure (Transaction creation): {}", ex.getMessage());
        ErrorCode errorCode = ErrorCode.DATABASE_CONNECTION_ERROR;
        ApiResponse<Void> response = new ApiResponse<>();
        response.setCode(errorCode.getCode());
        response.setMessage(errorCode.getMessage());
        return ResponseEntity.status(errorCode.getStatusCode()).body(response);
    }

    @ExceptionHandler(DataAccessResourceFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataAccessResourceFailure(DataAccessResourceFailureException ex) {
        log.error("Database resource failure [{}]: {}", ex.getClass().getSimpleName(), ex.getMessage());
        ErrorCode errorCode = isTimeoutException(ex) ? ErrorCode.CONNECTION_TIMEOUT : ErrorCode.DATABASE_CONNECTION_ERROR;
        ApiResponse<Void> response = new ApiResponse<>();
        response.setCode(errorCode.getCode());
        response.setMessage(errorCode.getMessage());
        return ResponseEntity.status(errorCode.getStatusCode()).body(response);
    }

    @ExceptionHandler(QueryTimeoutException.class)
    public ResponseEntity<ApiResponse<Void>> handleQueryTimeout(QueryTimeoutException ex) {
        log.error("Database query timeout: {}", ex.getMessage());
        ErrorCode errorCode = ErrorCode.CONNECTION_TIMEOUT;
        ApiResponse<Void> response = new ApiResponse<>();
        response.setCode(errorCode.getCode());
        response.setMessage(errorCode.getMessage());
        return ResponseEntity.status(errorCode.getStatusCode()).body(response);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMessage());
        ApiResponse<Void> response = new ApiResponse<>();
        response.setCode(ErrorCode.INVALID_INPUT.getCode());
        response.setMessage("Dữ liệu vi phạm ràng buộc toàn vẹn hoặc đã tồn tại trong hệ thống.");
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(MongoException.class)
    public ResponseEntity<ApiResponse<Void>> handleMongoException(MongoException ex) {
        log.error("MongoDB error [{}]: {}", ex.getClass().getSimpleName(), ex.getMessage());
        ErrorCode errorCode = (ex instanceof MongoTimeoutException || isTimeoutException(ex))
                ? ErrorCode.CONNECTION_TIMEOUT
                : ErrorCode.DATABASE_CONNECTION_ERROR;
        ApiResponse<Void> response = new ApiResponse<>();
        response.setCode(errorCode.getCode());
        response.setMessage(errorCode.getMessage());
        return ResponseEntity.status(errorCode.getStatusCode()).body(response);
    }

    @ExceptionHandler({
            SocketException.class,
            ConnectException.class,
            UnknownHostException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleNetworkSocketException(Exception ex) {
        log.error("Network/Socket connection error [{}]: {}", ex.getClass().getSimpleName(), ex.getMessage());
        ErrorCode errorCode = ErrorCode.NETWORK_CONNECTION_ERROR;
        ApiResponse<Void> response = new ApiResponse<>();
        response.setCode(errorCode.getCode());
        response.setMessage(errorCode.getMessage());
        return ResponseEntity.status(errorCode.getStatusCode()).body(response);
    }

    @ExceptionHandler(SocketTimeoutException.class)
    public ResponseEntity<ApiResponse<Void>> handleSocketTimeoutException(SocketTimeoutException ex) {
        log.error("Socket timeout error: {}", ex.getMessage());
        ErrorCode errorCode = ErrorCode.CONNECTION_TIMEOUT;
        ApiResponse<Void> response = new ApiResponse<>();
        response.setCode(errorCode.getCode());
        response.setMessage(errorCode.getMessage());
        return ResponseEntity.status(errorCode.getStatusCode()).body(response);
    }

    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceAccessException(ResourceAccessException ex) {
        log.error("External service/resource access error: {}", ex.getMessage());
        ErrorCode errorCode = isTimeoutException(ex)
                ? ErrorCode.CONNECTION_TIMEOUT
                : ErrorCode.EXTERNAL_SERVICE_ERROR;
        ApiResponse<Void> response = new ApiResponse<>();
        response.setCode(errorCode.getCode());
        response.setMessage(errorCode.getMessage());
        return ResponseEntity.status(errorCode.getStatusCode()).body(response);
    }

    private boolean isConnectionException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SocketException
                    || current instanceof ConnectException
                    || current instanceof UnknownHostException
                    || current instanceof MongoException
                    || current instanceof DataAccessResourceFailureException
                    || current instanceof CannotCreateTransactionException
                    || current instanceof ResourceAccessException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isTimeoutException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SocketTimeoutException
                    || current instanceof MongoTimeoutException
                    || current instanceof QueryTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
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
