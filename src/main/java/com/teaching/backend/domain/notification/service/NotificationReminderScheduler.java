package com.teaching.backend.domain.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationReminderScheduler {

    static final ZoneId REMINDER_ZONE = ZoneId.of("Asia/Seoul");

    private final NotificationService notificationService;

    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    public void createTeachingMapReminders() {
        int createdCount = notificationService.createTeachingMapReminders(LocalDateTime.now(REMINDER_ZONE));
        log.info("Teaching map reminder notifications created. count={}", createdCount);
    }
}
