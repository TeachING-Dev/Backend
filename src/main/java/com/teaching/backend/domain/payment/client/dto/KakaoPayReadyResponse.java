package com.teaching.backend.domain.payment.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoPayReadyResponse(
        String tid,
        @JsonProperty("next_redirect_pc_url")
        String nextRedirectPcUrl,
        @JsonProperty("next_redirect_mobile_url")
        String nextRedirectMobileUrl
) {
}
