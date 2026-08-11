package com.teaching.backend.domain.folder.service;

import com.teaching.backend.domain.folder.entity.Folder;
import com.teaching.backend.domain.folder.dto.request.FolderIdsRequest;
import com.teaching.backend.domain.folder.repository.FolderRepository;
import com.teaching.backend.domain.material.entity.Material;
import com.teaching.backend.domain.material.enums.PlatformType;
import com.teaching.backend.domain.material.repository.MaterialRepository;
import com.teaching.backend.domain.trash.service.TrashService;
import com.teaching.backend.domain.user.entity.User;
import com.teaching.backend.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FolderService.restoreFolder / TrashService.restoreFolders 가 실제 DB 위에서
 * "폴더 자신을 먼저 복구한 뒤 자료를 복구"하는 순서로 동작해도, materialRepository의
 * 자료 복구/카운트 쿼리가 정상 동작하는지 검증한다 (mock 테스트는 호출 순서와 무관하게
 * 항상 같은 값을 리턴하므로 이 클래스의 버그를 못 잡는다 — 반드시 실제 DB 왕복이 필요).
 */
@SpringBootTest
@Transactional
class FolderRestoreIntegrationTest {

    @Autowired
    private FolderService folderService;

    @Autowired
    private TrashService trashService;

    @Autowired
    private FolderRepository folderRepository;

    @Autowired
    private MaterialRepository materialRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void restoreFolderRestoresMaterialsTrashedTogetherWithFolderEvenThoughFolderIsRestoredFirst() {
        User user = userRepository.save(User.create("folder-restore-owner@example.com", "owner", null, null, null));
        Folder folder = folderRepository.save(Folder.create(user, "Folder"));
        Material material = materialRepository.save(material(user, folder));
        flushAndClear();

        folderService.moveFolderToTrash(user.getId(), folder.getId());
        flushAndClear();

        folderService.restoreFolder(user.getId(), folder.getId());
        flushAndClear();

        long stillDeletedCount = materialRepository.countDeletedByMaterialIdsAndUserId(
                java.util.List.of(material.getId()), user.getId());
        assertThat(stillDeletedCount).isZero();
    }

    @Test
    void restoreFoldersBatchRestoresMaterialsTrashedTogetherWithFolderEvenThoughFolderIsRestoredFirst() {
        User user = userRepository.save(User.create("folder-batch-restore-owner@example.com", "owner", null, null, null));
        Folder folder = folderRepository.save(Folder.create(user, "Folder"));
        Material material = materialRepository.save(material(user, folder));
        flushAndClear();

        folderService.moveFolderToTrash(user.getId(), folder.getId());
        flushAndClear();

        trashService.restoreFolders(user.getId(), new FolderIdsRequest(java.util.List.of(folder.getId())));
        flushAndClear();

        long stillDeletedCount = materialRepository.countDeletedByMaterialIdsAndUserId(
                java.util.List.of(material.getId()), user.getId());
        assertThat(stillDeletedCount).isZero();
    }

    private Material material(User user, Folder folder) {
        return Material.create(user, folder, "Title", "https://example.com/" + System.identityHashCode(folder), PlatformType.WEB);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
