package com.teaching.backend.domain.chat.service;

import com.teaching.backend.domain.material.entity.Material;
import com.teaching.backend.domain.material.enums.PlatformType;
import com.teaching.backend.domain.material.repository.MaterialRepository;
import com.teaching.backend.domain.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AggregateQuestionAnswererTest {

    private static final Long USER_ID = 1L;

    @Mock
    private MaterialRepository materialRepository;

    @InjectMocks
    private AggregateQuestionAnswerer aggregateQuestionAnswerer;

    @Test
    void tryAnswerReturnsEmptyForNonAggregateQuestion() {
        Optional<String> result = aggregateQuestionAnswerer.tryAnswer("깃허브 관련된 파일 없어?", USER_ID);

        assertThat(result).isEmpty();
    }

    @Test
    void tryAnswerReportsCountWhenQuestionAsksHowMany() {
        when(materialRepository.countByUser_Id(USER_ID)).thenReturn(12L);

        Optional<String> result = aggregateQuestionAnswerer.tryAnswer("내 자료 총 몇 개야?", USER_ID);

        assertThat(result).contains("현재 저장하신 자료는 총 12개입니다.");
    }

    @Test
    void tryAnswerReportsMostRecentMaterialTitleWhenQuestionAsksForRecent() {
        Material material = material(101L, "Spring Boot REST API 개발 강의");
        when(materialRepository.findFirstByUser_IdOrderByCreatedAtDesc(USER_ID)).thenReturn(Optional.of(material));

        Optional<String> result = aggregateQuestionAnswerer.tryAnswer("가장 최근에 저장한 자료가 뭐야?", USER_ID);

        assertThat(result).contains("가장 최근에 저장하신 자료는 'Spring Boot REST API 개발 강의'입니다.");
    }

    @Test
    void tryAnswerReportsNoMaterialsWhenRecentAskedButNoneExist() {
        when(materialRepository.findFirstByUser_IdOrderByCreatedAtDesc(USER_ID)).thenReturn(Optional.empty());

        Optional<String> result = aggregateQuestionAnswerer.tryAnswer("최신 자료 알려줘", USER_ID);

        assertThat(result).contains("아직 저장하신 자료가 없습니다.");
    }

    @Test
    void tryAnswerPrefersRecentIntentOverCountWhenBothPatternsMatch() {
        Material material = material(202L, "JPA 연관관계 매핑 정리");
        when(materialRepository.findFirstByUser_IdOrderByCreatedAtDesc(USER_ID)).thenReturn(Optional.of(material));

        Optional<String> result = aggregateQuestionAnswerer.tryAnswer("가장 최근 자료 몇 개 저장했어?", USER_ID);

        assertThat(result).contains("가장 최근에 저장하신 자료는 'JPA 연관관계 매핑 정리'입니다.");
    }

    private Material material(Long materialId, String title) {
        User user = User.create("user1@example.com", "user1", null, null, null);
        ReflectionTestUtils.setField(user, "id", USER_ID);
        Material material = Material.create(user, null, title, "https://example.com", PlatformType.WEB);
        ReflectionTestUtils.setField(material, "id", materialId);
        return material;
    }
}
