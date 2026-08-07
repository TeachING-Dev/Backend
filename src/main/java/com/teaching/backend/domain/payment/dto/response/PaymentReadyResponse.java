package com.teaching.backend.domain.payment.dto.response;

public record PaymentReadyResponse(
        String redirectUrl
) {
    public static PaymentReadyResponse of(String redirectUrl) {
        return new PaymentReadyResponse(redirectUrl);
    }
}
