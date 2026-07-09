package com.teaching.backend.global.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ApiResponse<T> {

    private Boolean isSuccess;
    private String code;
    private String message;
    private T result;
    private Object error;

    public static <T> ApiResponse<T> onSuccess(T result) {
        return new ApiResponse<>(
                true,
                "COMMON200",
                "요청에 성공했습니다.",
                result,
                null
        );
    }

    public static <T> ApiResponse<T> onSuccess(SuccessCode successCode, T result) {
        return new ApiResponse<>(
                true,
                successCode.getCode(),
                successCode.getMessage(),
                result,
                null
        );
    }

    public static <T> ApiResponse<T> onFailure(String code, String message, Object error) {
        return new ApiResponse<>(
                false,
                code,
                message,
                null,
                error
        );
    }
}