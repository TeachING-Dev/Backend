package com.teaching.backend.domain.material.service;

import com.teaching.backend.domain.material.exception.MaterialErrorCode;
import com.teaching.backend.domain.material.exception.MaterialException;
import com.teaching.backend.domain.material.repository.MaterialRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FolderMaterialCapacityValidatorTest {

    private static final Long FOLDER_ID = 10L;

    @Mock
    private MaterialRepository materialRepository;

    @InjectMocks
    private FolderMaterialCapacityValidator validator;

    @Test
    void allowsFifteenthActiveMaterialWhenFolderHasFourteenActiveMaterials() {
        when(materialRepository.countByFolder_Id(FOLDER_ID)).thenReturn(14L);

        assertThatCode(() -> validator.validateCanAdd(FOLDER_ID, 1))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsSixteenthActiveMaterialWhenFolderHasFifteenActiveMaterials() {
        when(materialRepository.countByFolder_Id(FOLDER_ID)).thenReturn(15L);

        assertThatThrownBy(() -> validator.validateCanAdd(FOLDER_ID, 1))
                .isInstanceOf(MaterialException.class)
                .extracting("errorCode")
                .isEqualTo(MaterialErrorCode.FOLDER_MATERIAL_LIMIT_EXCEEDED);
    }

    @Test
    void allowsWhenActiveCountIsUnderLimitEvenIfDeletedRowsExistOutsideCount() {
        when(materialRepository.countByFolder_Id(FOLDER_ID)).thenReturn(13L);

        assertThatCode(() -> validator.validateCanAdd(FOLDER_ID, 2))
                .doesNotThrowAnyException();
    }

    @Test
    void ignoresNonIncreasingOperations() {
        validator.validateCanAdd(FOLDER_ID, 0);

        verify(materialRepository, never()).countByFolder_Id(FOLDER_ID);
    }
}
