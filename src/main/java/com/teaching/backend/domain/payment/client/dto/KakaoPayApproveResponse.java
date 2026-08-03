package com.teaching.backend.domain.payment.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoPayApproveResponse(
        String aid,
        String tid,
        @JsonProperty("approved_at")
        String approvedAt
) {
}
