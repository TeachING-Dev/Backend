package com.teaching.backend.domain.term.service;

import com.teaching.backend.domain.term.dto.TermResponse;
import com.teaching.backend.domain.term.repository.TermRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
@Service
@RequiredArgsConstructor
public class TermService {
    private final TermRepository termRepository;

    @Transactional(readOnly = true)
    public List<TermResponse> getTerms() {
        return termRepository.findAllByDeletedAtIsNull().stream()
                .map(TermResponse::from)
                .toList();
    }
}
