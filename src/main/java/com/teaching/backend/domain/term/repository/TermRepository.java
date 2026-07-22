package com.teaching.backend.domain.term.repository;

import com.teaching.backend.domain.term.entity.Term;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TermRepository extends JpaRepository<Term, Long> {

    // GET /terms 목록 조회용
    List<Term> findAllByDeletedAtIsNull();

    // 회원가입 시 필수 약관 검증용
    List<Term> findAllByIsRequiredTrueAndDeletedAtIsNull();

    // 회원가입 시 동의한 약관 조회용
    List<Term> findAllByIdInAndDeletedAtIsNull(List<Long> ids);
}