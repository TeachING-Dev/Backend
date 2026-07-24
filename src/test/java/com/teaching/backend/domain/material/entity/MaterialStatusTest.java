package com.teaching.backend.domain.material.entity;

import com.teaching.backend.domain.folder.entity.Folder;
import com.teaching.backend.domain.material.enums.AiStatus;
import com.teaching.backend.domain.material.enums.PlatformType;
import com.teaching.backend.domain.user.entity.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MaterialStatusTest {

    @Test
    void createInitializesPendingStatus() {
        Material material = material();

        assertThat(material.getAiStatus()).isEqualTo(AiStatus.PENDING);
    }

    @Test
    void markAnalysisCompletedChangesStatusToCompleted() {
        Material material = material();

        material.markAnalysisCompleted();

        assertThat(material.getAiStatus()).isEqualTo(AiStatus.COMPLETED);
    }

    @Test
    void completeAnalysisSetsTitleDifficultyAndCompletedStatus() {
        Material material = material();

        material.completeAnalysis("Analysis Title", 3);

        assertThat(material.getAnalysisTitle()).isEqualTo("Analysis Title");
        assertThat(material.getDifficulty()).isEqualTo(3);
        assertThat(material.getAiStatus()).isEqualTo(AiStatus.COMPLETED);
    }

    @Test
    void failAnalysisChangesStatusToFailed() {
        Material material = material();

        material.failAnalysis();

        assertThat(material.getAiStatus()).isEqualTo(AiStatus.FAILED);
    }

    private Material material() {
        User user = User.create("user@example.com", "user", null, null, null);
        Folder folder = Folder.create(user, "Folder");
        return Material.create(user, folder, "Title", "https://example.com", PlatformType.WEB);
    }
}
