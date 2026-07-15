package com.teaching.backend.user.repository;

import com.teaching.backend.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    /** 본인(id)을 제외하고 같은 닉네임이 이미 존재하는지 (닉네임 중복 검사용) */
    boolean existsByNicknameAndIdNot(String nickname, Long id);
}
