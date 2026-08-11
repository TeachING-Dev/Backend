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
import org.springframework.transaction.annotation.Transactional;

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

    /** 휴지통 폴더 목록의 materialCount 계산에 쓰이는 쿼리 — 삭제된 자료만, 요청한 유저 소유만, 폴더별로 정확히 집계되는지 검증. */
    @Test
    void countDeletedByFolderIdsAndUserIdGroupsDeletedMaterialsPerFolderScopedToOwner() {
        User owner = userRepository.save(user("trash-count-owner"));
        User other = userRepository.save(user("trash-count-other"));
        Folder folderWithTwoDeleted = folderRepository.save(Folder.create(owner, "Folder X"));
        Folder folderWithOneDeleted = folderRepository.save(Folder.create(owner, "Folder Y"));
        Folder otherUsersFolder = folderRepository.save(Folder.create(other, "Folder Z"));

        Material activeInFolderX = materialRepository.save(material(owner, folderWithTwoDeleted, "https://example.com/active"));
        Material deletedInFolderX1 = materialRepository.save(material(owner, folderWithTwoDeleted, "https://example.com/x1"));
        Material deletedInFolderX2 = materialRepository.save(material(owner, folderWithTwoDeleted, "https://example.com/x2"));
        Material deletedInFolderY = materialRepository.save(material(owner, folderWithOneDeleted, "https://example.com/y"));
        Material deletedInOthersFolder = materialRepository.save(material(other, otherUsersFolder, "https://example.com/z"));
        deletedInFolderX1.delete();
        deletedInFolderX2.delete();
        deletedInFolderY.delete();
        deletedInOthersFolder.delete();
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
