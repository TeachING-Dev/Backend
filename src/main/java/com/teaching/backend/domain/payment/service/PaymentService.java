package com.teaching.backend.domain.payment.service;

import com.teaching.backend.domain.payment.client.KakaoPayClient;
import com.teaching.backend.domain.payment.client.dto.KakaoPayApproveResponse;
import com.teaching.backend.domain.payment.client.dto.KakaoPayReadyResponse;
import com.teaching.backend.domain.payment.dto.response.PaymentReadyResponse;
import com.teaching.backend.domain.payment.entity.Payment;
import com.teaching.backend.domain.payment.exception.PaymentErrorCode;
import com.teaching.backend.domain.payment.exception.PaymentException;
import com.teaching.backend.domain.payment.repository.PaymentRepository;
import com.teaching.backend.domain.user.entity.User;
import com.teaching.backend.domain.user.enums.MembershipType;
import com.teaching.backend.domain.user.exception.UserErrorCode;
import com.teaching.backend.domain.user.exception.UserException;
import com.teaching.backend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 카카오페이 온라인 결제(단건결제)로 TeachING Plus 구독을 처리하는 서비스.
 *
 * 정기결제(빌링키 자동 구독)가 아니라, "구독하기" 버튼을 누를 때마다 단건결제 승인 흐름을
 * 새로 태워 membershipType 을 PREMIUM 으로 전환하는 방식이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final KakaoPayClient kakaoPayClient;

    @Value("${kakaopay.item-name}")
    private String itemName;

    @Value("${kakaopay.amount}")
    private int amount;

    @Value("${kakaopay.callback-base-url}")
    private String callbackBaseUrl;

    @Value("${kakaopay.frontend-base-url}")
    private String frontendBaseUrl;

    @Transactional
    public PaymentReadyResponse readyPayment(Long userId) {
        User user = getActiveUser(userId);
        if (user.getMembershipType() == MembershipType.PREMIUM) {
            throw new PaymentException(PaymentErrorCode.ALREADY_SUBSCRIBED);
        }

        String orderId = UUID.randomUUID().toString();

        KakaoPayReadyResponse readyResponse = kakaoPayClient.ready(
                orderId,
                String.valueOf(userId),
                itemName,
                amount,
                callbackUrl("success", orderId),
                callbackUrl("cancel", orderId),
                callbackUrl("fail", orderId)
        );

        paymentRepository.save(Payment.ready(user, orderId, readyResponse.tid(), amount));

        return PaymentReadyResponse.of(readyResponse.nextRedirectPcUrl());
    }

    @Transactional
    public String approvePayment(String orderId, String pgToken) {
        Payment payment = getPaymentByOrderId(orderId);

        KakaoPayApproveResponse approveResponse = kakaoPayClient.approve(
                payment.getTid(),
                orderId,
                String.valueOf(payment.getUser().getId()),
                pgToken
        );

        payment.approve();
        payment.getUser().changeMembershipType(MembershipType.PREMIUM);

        return frontendBaseUrl + "/subscribe/complete";
    }

    @Transactional
    public String cancelPayment(String orderId) {
        getPaymentByOrderId(orderId).cancel();
        return frontendBaseUrl + "/subscribe?toast=canceled";
    }

    @Transactional
    public String failPayment(String orderId) {
        getPaymentByOrderId(orderId).fail();
        return frontendBaseUrl + "/subscribe?toast=failed";
    }

    private Payment getPaymentByOrderId(String orderId) {
        return paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new PaymentException(PaymentErrorCode.PAYMENT_NOT_FOUND));
    }

    private User getActiveUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
    }

    private String callbackUrl(String path, String orderId) {
        return callbackBaseUrl + "/api/v1/payments/" + path + "?orderId=" + orderId;
    }
}
