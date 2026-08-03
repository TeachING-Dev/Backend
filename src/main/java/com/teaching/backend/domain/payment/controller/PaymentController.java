package com.teaching.backend.domain.payment.controller;

import com.teaching.backend.domain.payment.code.PaymentSuccessCode;
import com.teaching.backend.domain.payment.dto.response.PaymentReadyResponse;
import com.teaching.backend.domain.payment.service.PaymentService;
import com.teaching.backend.global.response.ApiResponse;
import com.teaching.backend.global.security.entity.AuthMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@Tag(name = "Payment", description = "카카오페이 결제(TeachING Plus 구독) 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(
            summary = "구독 결제 준비",
            description = "TeachING Plus 구독 결제를 위해 카카오페이 결제 준비 API를 호출하고, 리다이렉트 URL을 반환합니다."
    )
    @PostMapping("/ready")
    public ApiResponse<PaymentReadyResponse> ready(
            @AuthenticationPrincipal AuthMember authMember
    ) {
        PaymentReadyResponse response = paymentService.readyPayment(authMember.getUserId());
        return ApiResponse.onSuccess(PaymentSuccessCode.PAYMENT_READY_SUCCESS, response);
    }

    @Operation(
            summary = "결제 성공 콜백",
            description = "카카오페이 결제 승인 완료 후 리다이렉트되는 콜백입니다. 결제 승인 처리 후 구독 완료 화면으로 리다이렉트합니다."
    )
    @GetMapping("/success")
    public void success(
            @RequestParam String orderId,
            @RequestParam("pg_token") String pgToken,
            HttpServletResponse httpResponse
    ) throws IOException {
        String redirectUrl = paymentService.approvePayment(orderId, pgToken);
        httpResponse.sendRedirect(redirectUrl);
    }

    @Operation(
            summary = "결제 취소 콜백",
            description = "결제 취소 또는 결제창 닫기 시 리다이렉트되는 콜백입니다. 구독 페이지로 되돌아가며 취소 토스트가 노출됩니다."
    )
    @GetMapping("/cancel")
    public void cancel(
            @RequestParam String orderId,
            HttpServletResponse httpResponse
    ) throws IOException {
        String redirectUrl = paymentService.cancelPayment(orderId);
        httpResponse.sendRedirect(redirectUrl);
    }

    @Operation(
            summary = "결제 실패 콜백",
            description = "결제 실패 시 리다이렉트되는 콜백입니다."
    )
    @GetMapping("/fail")
    public void fail(
            @RequestParam String orderId,
            HttpServletResponse httpResponse
    ) throws IOException {
        String redirectUrl = paymentService.failPayment(orderId);
        httpResponse.sendRedirect(redirectUrl);
    }
}
