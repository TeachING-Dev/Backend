package com.teaching.backend.domain.payment.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KakaoPayReadyRequest(
        String cid,
        @JsonProperty("partner_order_id")
        String partnerOrderId,
        @JsonProperty("partner_user_id")
        String partnerUserId,
        @JsonProperty("item_name")
        String itemName,
        int quantity,
        @JsonProperty("total_amount")
        int totalAmount,
        @JsonProperty("tax_free_amount")
        int taxFreeAmount,
        @JsonProperty("approval_url")
        String approvalUrl,
        @JsonProperty("cancel_url")
        String cancelUrl,
        @JsonProperty("fail_url")
        String failUrl
) {
}
