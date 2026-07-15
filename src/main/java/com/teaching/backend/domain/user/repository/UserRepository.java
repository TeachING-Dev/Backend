package com.teaching.backend.domain.user.repository;

import com.teaching.backend.domain.user.entity.User;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface UserRepository extends CrudRepository<User, Long> {
    // 카카오/구글 최초 가입 시 기존 유저인지 체크
    Optional<User> findByEmail(String email);
}
