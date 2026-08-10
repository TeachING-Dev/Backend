package com.teaching.backend.domain.material.service;

import com.teaching.backend.domain.material.exception.MaterialErrorCode;
import com.teaching.backend.domain.material.exception.MaterialException;
import com.teaching.backend.domain.material.repository.MaterialRepository;
import com.teaching.backend.domain.user.entity.User;
import com.teaching.backend.domain.user.enums.MembershipType;
import com.teaching.backend.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FolderMaterialCapacityValidatorTest {

    private static final Long FOLDER_ID = 10L;
    private static final Long USER_ID = 1L;

    @Mock
    private MaterialRepository materialRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private FolderMaterialCapacityValidator validator;

    @Test
    void allowsFifteenthActiveMaterialWhenFolderHasFourteenActiveMaterials() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(MembershipType.FREE)));
        when(materialRepository.countByFolder_Id(FOLDER_ID)).thenReturn(14L);

        assertThatCode(() -> validator.validateCanAdd(USER_ID, FOLDER_ID, 1))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsSixteenthActiveMaterialWhenFolderHasFifteenActiveMaterials() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(MembershipType.FREE)));
        when(materialRepository.countByFolder_Id(FOLDER_ID)).thenReturn(15L);

        assertThatThrownBy(() -> validator.validateCanAdd(USER_ID, FOLDER_ID, 1))
                .isInstanceOf(MaterialException.class)
                .extracting("errorCode")
                .isEqualTo(MaterialErrorCode.FOLDER_MATERIAL_LIMIT_EXCEEDED);
    }

    @Test
    void rejectsWhenAdditionalMaterialsWouldExceedFifteenActiveMaterials() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(MembershipType.FREE)));
        when(materialRepository.countByFolder_Id(FOLDER_ID)).thenReturn(14L);

        assertThatThrownBy(() -> validator.validateCanAdd(USER_ID, FOLDER_ID, 2))
                .isInstanceOf(MaterialException.class)
                .extracting("errorCode")
                .isEqualTo(MaterialErrorCode.FOLDER_MATERIAL_LIMIT_EXCEEDED);
    }

    @Test
    void allowsWhenActiveCountIsUnderLimitEvenIfDeletedRowsExistOutsideCount() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(MembershipType.FREE)));
        when(materialRepository.countByFolder_Id(FOLDER_ID)).thenReturn(13L);

        assertThatCode(() -> validator.validateCanAdd(USER_ID, FOLDER_ID, 2))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsNullMembershipUserLikeFreeUser() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(null)));
        when(materialRepository.countByFolder_Id(FOLDER_ID)).thenReturn(15L);

        assertThatThrownBy(() -> validator.validateCanAdd(USER_ID, FOLDER_ID, 1))
                .isInstanceOf(MaterialException.class)
                .extracting("errorCode")
                .isEqualTo(MaterialErrorCode.FOLDER_MATERIAL_LIMIT_EXCEEDED);
    }

    @Test
    void skipsFolderMaterialLimitForPremiumUser() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(MembershipType.PREMIUM)));

        assertThatCode(() -> validator.validateCanAdd(USER_ID, FOLDER_ID, 100))
                .doesNotThrowAnyException();

        verify(materialRepository, never()).countByFolder_Id(FOLDER_ID);
    }

    @Test
    void ignoresNonIncreasingOperations() {
        validator.validateCanAdd(USER_ID, FOLDER_ID, 0);

        verify(materialRepository, never()).countByFolder_Id(FOLDER_ID);
    }

    private User user(MembershipType membershipType) {
        User user = User.create("user@example.com", "user", null, null, null);
        ReflectionTestUtils.setField(user, "id", USER_ID);
        ReflectionTestUtils.setField(user, "membershipType", membershipType);
        return user;
    }
}
