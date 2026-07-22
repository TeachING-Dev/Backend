package com.teaching.backend.domain.term.repository;

import com.teaching.backend.domain.term.entity.UserTerm;
import com.teaching.backend.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserTermRepository extends JpaRepository<UserTerm, Long> {

    List<UserTerm> findAllByUser(User user);
}