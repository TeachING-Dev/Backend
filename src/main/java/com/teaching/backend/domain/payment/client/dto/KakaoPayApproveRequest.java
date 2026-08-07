package com.teaching.backend.domain.payment.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KakaoPayApproveRequest(
        String cid,
        String tid,
        @JsonProperty("partner_order_id")
        String partnerOrderId,
        @JsonProperty("partner_user_id")
        String partnerUserId,
        @JsonProperty("pg_token")
        String pgToken
) {
}
