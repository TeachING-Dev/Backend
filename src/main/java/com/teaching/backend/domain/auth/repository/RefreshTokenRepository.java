package com.teaching.backend.domain.auth.repository;

import com.teaching.backend.domain.auth.entity.RefreshToken;
import com.teaching.backend.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    Optional<RefreshToken> findByUser(User user);
    void deleteByUser_Id(Long userId);

    /** 조회 없이 단일 DELETE 쿼리로 삭제해, 동시 로그아웃 요청에서 0건 삭제가 StaleStateException 으로 이어지는 것을 방지한다. */
    @Modifying
    @Query("delete from RefreshToken r where r.tokenHash = :tokenHash")
    void deleteByTokenHash(@Param("tokenHash") String tokenHash);
}
