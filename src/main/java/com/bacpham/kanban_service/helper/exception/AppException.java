package com.bacpham.kanban_service.helper.exception;

import lombok.Getter;

@Getter
public class AppException extends RuntimeException {
    private final ErrorCode errorCode;
    public AppException(ErrorCode errorCode) {
        super(errorCode != null ? errorCode.getMessage() : "Unknown error");
        this.errorCode = errorCode;
    }

}
