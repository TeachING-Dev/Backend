package com.teaching.backend.domain.user.repository;

import com.teaching.backend.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    // 카카오/구글 최초 가입 시 기존 유저인지 체크
    Optional<User> findByEmail(String email);

    /** 본인(id)을 제외하고 같은 닉네임이 이미 존재하는지 (닉네임 중복 검사용) */
    boolean existsByNicknameAndIdNot(String nickname, Long id);
}
