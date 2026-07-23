package com.teaching.backend.domain.trash.util;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 휴지통 목록의 삭제 시각을 상대 시간으로 표시한다.
 * 1시간 미만 "방금 전", 24시간 미만 "N시간 전", 그 이후 "N일 전"(24시간 단위).
 */
public final class RelativeTimeFormatter {

    private RelativeTimeFormatter() {
    }

    public static String format(LocalDateTime time) {
        if (time == null) {
            return null;
        }

        long hours = Duration.between(time, LocalDateTime.now()).toHours();
        if (hours < 1) {
            return "방금 전";
        }
        if (hours < 24) {
            return hours + "시간 전";
        }
        return (hours / 24) + "일 전";
    }
}
