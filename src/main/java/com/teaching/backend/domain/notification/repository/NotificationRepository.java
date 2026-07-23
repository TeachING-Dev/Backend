package com.teaching.backend.domain.notification.repository;

import com.teaching.backend.domain.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findAllByUser_Id(Long userId, Sort sort);

    Page<Notification> findAllByUser_Id(Long userId, Pageable pageable);
}
