package com.teaching.backend.domain.material.repository;

import com.teaching.backend.domain.folder.entity.Folder;
import com.teaching.backend.domain.folder.repository.FolderRepository;
import com.teaching.backend.domain.material.entity.Material;
import com.teaching.backend.domain.material.enums.AiStatus;
import com.teaching.backend.domain.material.enums.PlatformType;
import com.teaching.backend.domain.user.entity.User;
import com.teaching.backend.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest
@Transactional
class MaterialRepositoryTest {

    @Autowired
    private MaterialRepository materialRepository;

    @Autowired
    private FolderRepository folderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void findsActiveMaterialBySameUserAndSameOriginalUrl() {
        User user = userRepository.save(user("owner"));
        Folder folder = folderRepository.save(Folder.create(user, "Folder A"));
        Material material = materialRepository.save(material(user, folder, "https://example.com/article"));
        flushAndClear();

        List<Material> result = materialRepository.findAllByUser_IdAndOriginalUrlOrderByCreatedAtDescIdDesc(
                user.getId(),
                "https://example.com/article"
        );

        assertThat(result).extracting(Material::getId)
                .containsExactly(material.getId());
    }

    @Test
    void findsMultipleActiveMaterialsWithSameOriginalUrlWithoutSingleResultFailure() {
        User user = userRepository.save(user("owner"));
        Folder folder = folderRepository.save(Folder.create(user, "Folder E"));
        Material older = materialRepository.save(material(user, folder, "https://example.com/article"));
        Material newer = materialRepository.save(material(user, folder, "https://example.com/article"));
        flushAndClear();

        List<Material> result = materialRepository.findAllByUser_IdAndOriginalUrlOrderByCreatedAtDescIdDesc(
                user.getId(),
                "https://example.com/article"
        );

        assertThat(result).extracting(Material::getId)
                .containsExactly(newer.getId(), older.getId());
    }

    @Test
    void doesNotFindDifferentOriginalUrlForSameUser() {
        User user = userRepository.save(user("owner"));
        Folder folder = folderRepository.save(Folder.create(user, "Folder B"));
        materialRepository.save(material(user, folder, "https://example.com/article"));
        flushAndClear();

        List<Material> result = materialRepository.findAllByUser_IdAndOriginalUrlOrderByCreatedAtDescIdDesc(
                user.getId(),
                "https://example.com/other"
        );

        assertThat(result).isEmpty();
    }

    @Test
    void doesNotFindSameOriginalUrlForDifferentUser() {
        User owner = userRepository.save(user("owner"));
        User other = userRepository.save(user("other"));
        Folder folder = folderRepository.save(Folder.create(owner, "Folder C"));
        materialRepository.save(material(owner, folder, "https://example.com/article"));
        flushAndClear();

        List<Material> result = materialRepository.findAllByUser_IdAndOriginalUrlOrderByCreatedAtDescIdDesc(
                other.getId(),
                "https://example.com/article"
        );

        assertThat(result).isEmpty();
    }

    @Test
    void doesNotFindDeletedMaterialWithSameOriginalUrl() {
        User user = userRepository.save(user("owner"));
        Folder folder = folderRepository.save(Folder.create(user, "Folder D"));
        Material material = materialRepository.save(material(user, folder, "https://example.com/article"));
        material.delete();
        flushAndClear();

        List<Material> result = materialRepository.findAllByUser_IdAndOriginalUrlOrderByCreatedAtDescIdDesc(
                user.getId(),
                "https://example.com/article"
        );

        assertThat(result).isEmpty();
    }

    @Test
    void findHomeRecentMaterialsExcludesDeletedMaterials() {
        User user = userRepository.save(user("home-owner"));
        Folder folder = folderRepository.save(Folder.create(user, "HomeFolder"));
        Material active = materialRepository.save(material(user, folder, "https://example.com/active"));
        active.markAnalysisCompleted();
        Material deleted = materialRepository.save(material(user, folder, "https://example.com/deleted"));
        deleted.markAnalysisCompleted();
        deleted.delete();
        flushAndClear();

        List<Material> result = materialRepository.findHomeRecentMaterials(
                user.getId(),
                AiStatus.COMPLETED,
                PageRequest.of(0, 5)
        ).getContent();

        assertThat(result).extracting(Material::getId)
                .containsExactly(active.getId());
    }

    @Test
    void countByFolderIdExcludesDeletedMaterialsBySqlRestriction() {
        User user = userRepository.save(user("capacity-count"));
        Folder folder = folderRepository.save(Folder.create(user, "Capacity"));

        for (int index = 0; index < 14; index++) {
            materialRepository.save(material(user, folder, "https://example.com/active-" + index));
        }
        for (int index = 0; index < 2; index++) {
            Material deleted = materialRepository.save(material(user, folder, "https://example.com/deleted-" + index));
            deleted.delete();
        }
        flushAndClear();

        long count = materialRepository.countByFolder_Id(folder.getId());

        assertThat(count).isEqualTo(14L);
    }

    @Test
    void countDeletedByMaterialIdsAndUserIdCountsDeletedMaterialsRegardlessOfOriginalFolder() {
        User user = userRepository.save(user("restore-count"));
        Folder targetFolder = folderRepository.save(Folder.create(user, "Target"));
        Folder otherFolder = folderRepository.save(Folder.create(user, "Other"));
        Material targetDeleted = materialRepository.save(material(user, targetFolder, "https://example.com/target"));
        Material otherDeleted = materialRepository.save(material(user, otherFolder, "https://example.com/other"));
        targetDeleted.delete();
        otherDeleted.delete();
        flushAndClear();

        long count = materialRepository.countDeletedByMaterialIdsAndUserId(
                List.of(targetDeleted.getId(), otherDeleted.getId()),
                user.getId()
        );

        assertThat(count).isEqualTo(2L);
    }

    @Test
    void findFolderNamesByIdsReturnsFolderNameForEachRequestedMaterial() {
        User user = userRepository.save(user("folder-names"));
        Folder folderA = folderRepository.save(Folder.create(user, "Folder A"));
        Folder folderB = folderRepository.save(Folder.create(user, "Folder B"));
        Material materialInA = materialRepository.save(material(user, folderA, "https://example.com/a"));
        Material materialInB = materialRepository.save(material(user, folderB, "https://example.com/b"));
        flushAndClear();

        List<MaterialFolderNameProjection> result = materialRepository.findFolderNamesByIds(
                List.of(materialInA.getId(), materialInB.getId()),
                user.getId()
        );

        assertThat(result)
                .extracting(MaterialFolderNameProjection::getMaterialId, MaterialFolderNameProjection::getFolderName)
                .containsExactlyInAnyOrder(
                        tuple(materialInA.getId(), "Folder A"),
                        tuple(materialInB.getId(), "Folder B")
                );
    }

    @Test
    void findFolderNamesByIdsExcludesMaterialsOwnedByOtherUsers() {
        User owner = userRepository.save(user("folder-names-owner"));
        User other = userRepository.save(user("folder-names-other"));
        Folder folder = folderRepository.save(Folder.create(owner, "Owner Folder"));
        Material material = materialRepository.save(material(owner, folder, "https://example.com/owned"));
        flushAndClear();

        List<MaterialFolderNameProjection> result = materialRepository.findFolderNamesByIds(
                List.of(material.getId()),
                other.getId()
        );

        assertThat(result).isEmpty();
    }

    /**
     * 휴지통 폴더 목록의 materialCount 계산에 쓰이는 쿼리.
     * 폴더와 "함께" 삭제된 자료만 세야 하고, 폴더가 삭제되기 전에 이미 개별적으로
     * 휴지통에 있던 자료(deletedBeforeFolderTrashed)는 이 폴더의 개수에 섞이면 안 된다
     * — 섞이면 폴더 복구 시 그 자료까지 부활하는 버그로 이어진다(별도 테스트에서 검증).
     */
    @Test
    void countDeletedByFolderIdsAndUserIdOnlyCountsMaterialsDeletedTogetherWithTheFolder() {
        User owner = userRepository.save(user("trash-count-owner"));
        User other = userRepository.save(user("trash-count-other"));
        Folder folderWithTwoDeleted = folderRepository.save(Folder.create(owner, "Folder X"));
        Folder folderWithOneDeleted = folderRepository.save(Folder.create(owner, "Folder Y"));
        Folder otherUsersFolder = folderRepository.save(Folder.create(other, "Folder Z"));

        Material activeInFolderX = materialRepository.save(material(owner, folderWithTwoDeleted, "https://example.com/active"));
        Material deletedBeforeFolderTrashed = materialRepository.save(material(owner, folderWithTwoDeleted, "https://example.com/early"));
        Material deletedWithFolderX1 = materialRepository.save(material(owner, folderWithTwoDeleted, "https://example.com/x1"));
        Material deletedWithFolderX2 = materialRepository.save(material(owner, folderWithTwoDeleted, "https://example.com/x2"));
        Material deletedWithFolderY = materialRepository.save(material(owner, folderWithOneDeleted, "https://example.com/y"));
        Material deletedInOthersFolder = materialRepository.save(material(other, otherUsersFolder, "https://example.com/z"));

        LocalDateTime beforeFolderTrashed = LocalDateTime.now().minusDays(1);
        LocalDateTime folderTrashedAt = LocalDateTime.now();
        ReflectionTestUtils.setField(deletedBeforeFolderTrashed, "deletedAt", beforeFolderTrashed);
        ReflectionTestUtils.setField(folderWithTwoDeleted, "deletedAt", folderTrashedAt);
        ReflectionTestUtils.setField(folderWithOneDeleted, "deletedAt", folderTrashedAt);
        ReflectionTestUtils.setField(otherUsersFolder, "deletedAt", folderTrashedAt);
        ReflectionTestUtils.setField(deletedWithFolderX1, "deletedAt", folderTrashedAt);
        ReflectionTestUtils.setField(deletedWithFolderX2, "deletedAt", folderTrashedAt);
        ReflectionTestUtils.setField(deletedWithFolderY, "deletedAt", folderTrashedAt);
        ReflectionTestUtils.setField(deletedInOthersFolder, "deletedAt", folderTrashedAt);
        flushAndClear();

        List<FolderMaterialRestoreCountProjection> result = materialRepository.countDeletedByFolderIdsAndUserId(
                List.of(folderWithTwoDeleted.getId(), folderWithOneDeleted.getId(), otherUsersFolder.getId()),
                owner.getId()
        );

        assertThat(result)
                .extracting(FolderMaterialRestoreCountProjection::getFolderId, FolderMaterialRestoreCountProjection::getMaterialCount)
                .containsExactlyInAnyOrder(
                        tuple(folderWithTwoDeleted.getId(), 2L),
                        tuple(folderWithOneDeleted.getId(), 1L)
                );
    }

    /**
     * 폴더를 통째로 삭제했다가 복구할 때, 그 폴더 삭제 이전에 이미 개별적으로 휴지통에
     * 있던 자료까지 같이 부활하면 안 된다는 걸 검증하는 회귀 테스트.
     */
    @Test
    void restoreTrashedMaterialsByFolderDoesNotResurrectMaterialsDeletedBeforeTheFolderWasTrashed() {
        User owner = userRepository.save(user("restore-scope-owner"));
        Folder folder = folderRepository.save(Folder.create(owner, "Folder"));
        Material deletedBeforeFolderTrashed = materialRepository.save(material(owner, folder, "https://example.com/early"));
        Material deletedWithFolder = materialRepository.save(material(owner, folder, "https://example.com/with-folder"));

        LocalDateTime beforeFolderTrashed = LocalDateTime.now().minusDays(1);
        LocalDateTime folderTrashedAt = LocalDateTime.now();
        ReflectionTestUtils.setField(deletedBeforeFolderTrashed, "deletedAt", beforeFolderTrashed);
        ReflectionTestUtils.setField(folder, "deletedAt", folderTrashedAt);
        ReflectionTestUtils.setField(deletedWithFolder, "deletedAt", folderTrashedAt);
        flushAndClear();

        materialRepository.restoreTrashedMaterialsByFolder(folder.getId(), owner.getId());
        flushAndClear();

        // Material에도 @SQLRestriction("deleted_at IS NULL")이 걸려있어 findById로는 삭제 상태를
        // 확인할 수 없으므로(삭제된 행은 아예 안 보임), native 카운트 쿼리로 삭제 여부를 확인한다.
        boolean earlyDeletedMaterialStillInTrash = materialRepository.countDeletedByMaterialIdsAndUserId(
                List.of(deletedBeforeFolderTrashed.getId()), owner.getId()
        ) == 1;
        boolean cascadeDeletedMaterialWasRestored = materialRepository.countDeletedByMaterialIdsAndUserId(
                List.of(deletedWithFolder.getId()), owner.getId()
        ) == 0;

        assertThat(earlyDeletedMaterialStillInTrash).isTrue();
        assertThat(cascadeDeletedMaterialWasRestored).isTrue();
    }

    private User user(String suffix) {
        return User.create(
                "material-repository-" + suffix + "@example.com",
                "repo-" + suffix,
                null,
                null,
                null
        );
    }

    private Material material(User user, Folder folder, String originalUrl) {
        return Material.create(user, folder, "Title", originalUrl, PlatformType.WEB);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
