package com.teaching.backend.domain.material.repository;

import com.teaching.backend.domain.folder.entity.Folder;
import com.teaching.backend.domain.folder.repository.FolderRepository;
import com.teaching.backend.domain.material.entity.Material;
import com.teaching.backend.domain.material.enums.PlatformType;
import com.teaching.backend.domain.user.entity.User;
import com.teaching.backend.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

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

        Optional<Material> result = materialRepository.findByUser_IdAndOriginalUrl(
                user.getId(),
                "https://example.com/article"
        );

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(material.getId());
    }

    @Test
    void doesNotFindDifferentOriginalUrlForSameUser() {
        User user = userRepository.save(user("owner"));
        Folder folder = folderRepository.save(Folder.create(user, "Folder B"));
        materialRepository.save(material(user, folder, "https://example.com/article"));
        flushAndClear();

        Optional<Material> result = materialRepository.findByUser_IdAndOriginalUrl(
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

        Optional<Material> result = materialRepository.findByUser_IdAndOriginalUrl(
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

        Optional<Material> result = materialRepository.findByUser_IdAndOriginalUrl(
                user.getId(),
                "https://example.com/article"
        );

        assertThat(result).isEmpty();
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
