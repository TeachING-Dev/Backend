package com.teaching.backend.global.exception;

import com.teaching.backend.global.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(GeneralException.class)
    public ResponseEntity<ApiResponse<Object>> handleGeneralException(GeneralException e) {
        ErrorCode errorCode = e.getErrorCode();

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.onFailure(
                        errorCode.getCode(),
                        errorCode.getMessage(),
                        null
                ));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> handleNotReadable(HttpMessageNotReadableException e) {
        ErrorCode errorCode = ErrorCode.BAD_REQUEST;

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.onFailure(
                        errorCode.getCode(),
                        errorCode.getMessage(),
                        null
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleException(Exception e) {
        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.onFailure(
                        errorCode.getCode(),
                        errorCode.getMessage(),
                        e.getMessage()
                ));
    }
}