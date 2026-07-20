package com.teaching.backend.domain.user.repository;

import com.teaching.backend.domain.user.entity.WithdrawalHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WithdrawalHistoryRepository extends JpaRepository<WithdrawalHistory, Long> {
}
