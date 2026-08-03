package com.teaching.backend.domain.material.repository;

import com.teaching.backend.domain.material.entity.Material;
import com.teaching.backend.domain.material.entity.MaterialChunk;
import com.teaching.backend.domain.material.enums.PlatformType;
import com.teaching.backend.domain.user.entity.User;
import com.teaching.backend.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// findByUserIdAndChunkTextContaining는 chunkText(@Lob MEDIUMTEXT)에 LOWER(CAST(... AS string))를
// 씌우는 JPQL이라, Mockito 단위 테스트만으로는 실제 DB에서 쿼리 자체가 유효한지 검증할 수 없다
// (CAST 없이 LOWER를 바로 씌웠다가 컨텍스트 로딩이 깨진 적이 있음). 실제 DB에 저장/조회까지 해서 검증한다.
@SpringBootTest
@Transactional
class MaterialChunkRepositoryTest {

    @Autowired
    private MaterialChunkRepository materialChunkRepository;

    @Autowired
    private MaterialRepository materialRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void findsChunkByCaseInsensitiveKeywordContainedInChunkText() {
        User user = userRepository.save(user("owner"));
        Material material = materialRepository.save(material(user, "Node.js 강의"));
        MaterialChunk chunk = materialChunkRepository.save(
                chunk(material, 0, "Node.js는 비동기 이벤트 기반 런타임이다", "point-1"));
        flushAndClear();

        List<MaterialChunk> result = materialChunkRepository
                .findByUserIdAndChunkTextContaining(user.getId(), "node.js", PageRequest.of(0, 5));

        assertThat(result).extracting(MaterialChunk::getId).containsExactly(chunk.getId());
    }

    @Test
    void doesNotFindChunkWhenKeywordAbsentFromChunkText() {
        User user = userRepository.save(user("owner"));
        Material material = materialRepository.save(material(user, "Node.js 강의"));
        materialChunkRepository.save(chunk(material, 0, "Node.js는 비동기 이벤트 기반 런타임이다", "point-2"));
        flushAndClear();

        List<MaterialChunk> result = materialChunkRepository
                .findByUserIdAndChunkTextContaining(user.getId(), "리액트", PageRequest.of(0, 5));

        assertThat(result).isEmpty();
    }

    @Test
    void doesNotFindOtherUsersChunkEvenIfKeywordMatches() {
        User owner = userRepository.save(user("owner"));
        User other = userRepository.save(user("other"));
        Material material = materialRepository.save(material(owner, "Node.js 강의"));
        materialChunkRepository.save(chunk(material, 0, "Node.js는 비동기 이벤트 기반 런타임이다", "point-3"));
        flushAndClear();

        List<MaterialChunk> result = materialChunkRepository
                .findByUserIdAndChunkTextContaining(other.getId(), "node.js", PageRequest.of(0, 5));

        assertThat(result).isEmpty();
    }

    @Test
    void limitsResultsToPageableSizeEvenWhenMoreChunksMatch() {
        User user = userRepository.save(user("owner"));
        Material material = materialRepository.save(material(user, "Node.js 강의"));
        for (int i = 0; i < 5; i++) {
            materialChunkRepository.save(chunk(material, i, "Node.js 관련 내용 " + i, "point-limit-" + i));
        }
        flushAndClear();

        List<MaterialChunk> result = materialChunkRepository
                .findByUserIdAndChunkTextContaining(user.getId(), "node.js", PageRequest.of(0, 3));

        assertThat(result).hasSize(3);
    }

    private User user(String suffix) {
        return User.create(
                "material-chunk-repository-" + suffix + "@example.com",
                "chunk-repo-" + suffix,
                null,
                null,
                null
        );
    }

    private Material material(User user, String title) {
        return Material.create(user, null, title, "https://example.com/" + title, PlatformType.WEB);
    }

    private MaterialChunk chunk(Material material, int chunkIndex, String text, String qdrantPointId) {
        return MaterialChunk.create(material, chunkIndex, text, qdrantPointId, null, null);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
