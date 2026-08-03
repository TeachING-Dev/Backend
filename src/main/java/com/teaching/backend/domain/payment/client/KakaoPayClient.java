package com.teaching.backend.domain.payment.client;

import com.teaching.backend.domain.payment.client.dto.KakaoPayApproveRequest;
import com.teaching.backend.domain.payment.client.dto.KakaoPayApproveResponse;
import com.teaching.backend.domain.payment.client.dto.KakaoPayReadyRequest;
import com.teaching.backend.domain.payment.client.dto.KakaoPayReadyResponse;
import com.teaching.backend.domain.payment.exception.PaymentErrorCode;
import com.teaching.backend.domain.payment.exception.PaymentException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

/** 카카오페이 온라인 결제(단건결제) Open API를 직접 호출하는 클라이언트. */
@Slf4j
@Component
public class KakaoPayClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final WebClient webClient;
    private final String cid;

    public KakaoPayClient(
            @Value("${kakaopay.base-url}") String baseUrl,
            @Value("${kakaopay.secret-key}") String secretKey,
            @Value("${kakaopay.cid}") String cid
    ) {
        this.cid = cid;
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "SECRET_KEY " + secretKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public KakaoPayReadyResponse ready(
            String partnerOrderId,
            String partnerUserId,
            String itemName,
            int amount,
            String approvalUrl,
            String cancelUrl,
            String failUrl
    ) {
        KakaoPayReadyRequest request = new KakaoPayReadyRequest(
                cid,
                partnerOrderId,
                partnerUserId,
                itemName,
                1,
                amount,
                0,
                approvalUrl,
                cancelUrl,
                failUrl
        );

        return call(webClient.post()
                .uri("/online/v1/payment/ready")
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::mapError)
                .bodyToMono(KakaoPayReadyResponse.class));
    }

    public KakaoPayApproveResponse approve(
            String tid,
            String partnerOrderId,
            String partnerUserId,
            String pgToken
    ) {
        KakaoPayApproveRequest request = new KakaoPayApproveRequest(cid, tid, partnerOrderId, partnerUserId, pgToken);

        return call(webClient.post()
                .uri("/online/v1/payment/approve")
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::mapError)
                .bodyToMono(KakaoPayApproveResponse.class));
    }

    private Mono<Throwable> mapError(ClientResponse response) {
        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(body -> {
                    log.warn("KakaoPay request failed. status={}, body={}", response.statusCode().value(), body);
                    return new PaymentException(PaymentErrorCode.KAKAOPAY_REQUEST_FAILED);
                });
    }

    private <T> T call(Mono<T> mono) {
        try {
            return mono.block(TIMEOUT);
        } catch (PaymentException e) {
            throw e;
        } catch (Exception e) {
            log.warn("KakaoPay request failed. reason={}, message={}", e.getClass().getSimpleName(), e.getMessage());
            throw new PaymentException(PaymentErrorCode.KAKAOPAY_REQUEST_FAILED);
        }
    }
}
