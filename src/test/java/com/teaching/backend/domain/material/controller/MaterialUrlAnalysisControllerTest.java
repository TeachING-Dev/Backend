package com.teaching.backend.domain.material.controller;

import com.teaching.backend.domain.material.dto.request.MaterialAnalyzeRequest;
import com.teaching.backend.domain.material.dto.response.MaterialAnalyzeResponse;
import com.teaching.backend.domain.material.enums.MaterialAnalyzeResultType;
import com.teaching.backend.domain.material.service.MaterialUrlAnalysisService;
import com.teaching.backend.domain.user.entity.User;
import com.teaching.backend.global.apiPayload.code.GlobalErrorCode;
import com.teaching.backend.global.exception.GeneralException;
import com.teaching.backend.global.response.ApiResponse;
import com.teaching.backend.global.security.entity.AuthMember;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaterialUrlAnalysisControllerTest {

    private static final Long USER_ID = 1L;

    @Mock
    private MaterialUrlAnalysisService materialUrlAnalysisService;

    @InjectMocks
    private MaterialUrlAnalysisController materialUrlAnalysisController;

    @Test
    void analyzeUsesAuthenticatedUserIdAndReturnsApiResponse() {
        MaterialAnalyzeRequest request = new MaterialAnalyzeRequest("https://example.com", 10L, false);
        MaterialAnalyzeResponse serviceResponse = MaterialAnalyzeResponse.analysisRequired(
                "https://example.com",
                null
        );
        when(materialUrlAnalysisService.analyze(USER_ID, request)).thenReturn(serviceResponse);

        ResponseEntity<ApiResponse<MaterialAnalyzeResponse>> response = materialUrlAnalysisController.analyze(
                AuthMember.from(user(USER_ID)),
                request
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("ANALYSIS2103");
        assertThat(response.getBody().getResult().resultType()).isEqualTo(MaterialAnalyzeResultType.ANALYSIS_REQUIRED);
        verify(materialUrlAnalysisService).analyze(USER_ID, request);
    }

    @Test
    void analyzeRejectsMissingAuthentication() {
        MaterialAnalyzeRequest request = new MaterialAnalyzeRequest("https://example.com", 10L, false);

        assertThatThrownBy(() -> materialUrlAnalysisController.analyze(null, request))
                .isInstanceOf(GeneralException.class)
                .extracting("errorCode")
                .isEqualTo(GlobalErrorCode.UNAUTHORIZED);
    }

    private User user(Long userId) {
        User user = User.create("user" + userId + "@example.com", "user" + userId, null, null, null);
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }
}
