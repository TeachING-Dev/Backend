package com.teaching.backend.domain.term.repository;

import com.teaching.backend.domain.term.entity.Term;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TermRepository extends JpaRepository<Term, Long> {

    List<Term> findAllByIsRequiredTrue();
}