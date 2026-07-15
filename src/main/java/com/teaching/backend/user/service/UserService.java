package com.teaching.backend.user.service;

import com.teaching.backend.global.exception.ErrorCode;
import com.teaching.backend.global.exception.GeneralException;
import com.teaching.backend.user.dto.NotificationUpdateRequestDto;
import com.teaching.backend.user.dto.NotificationUpdateResponseDto;
import com.teaching.backend.user.dto.UserInfoResponseDto;
import com.teaching.backend.user.dto.UserUpdateRequestDto;
import com.teaching.backend.user.dto.UserUpdateResponseDto;
import com.teaching.backend.user.entity.Account;
import com.teaching.backend.user.entity.User;
import com.teaching.backend.user.repository.AccountRepository;
import com.teaching.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 마이페이지 도메인 서비스.
 *
 * main 규약에 맞춘 부분:
 *  - 예외는 GeneralException(ErrorCode) 로 던진다. (기존 CustomException + errorDetail 폐기)
 *    → 닉네임 중복 시 {field:"nickname"} 상세는 main GeneralException 이 detail 을
 *      담지 못하므로 빠졌다. 필요해지면 GeneralException 확장이 별도로 필요.
 *  - "탈퇴 사용자 제외" 는 main User 의 soft-delete(@SQLRestriction "deleted_at IS NULL")
 *    로 이미 처리되므로, findById 가 탈퇴 사용자를 반환하지 않는다.
 *    별도 isWithdrawn 플래그 검사는 두지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;

    /** 닉네임: 2~10자의 한글/영문/숫자 */
    private static final Pattern NICKNAME_PATTERN = Pattern.compile("^[가-힣a-zA-Z0-9]{2,10}$");

    /** [GET] /users/me */
    public UserInfoResponseDto getMyInfo(Long userId) {
        User user = getActiveUser(userId);
        List<Account> accounts = accountRepository.findAllByUser(user);
        return UserInfoResponseDto.from(user, accounts);
    }

    /** [PATCH] /users/me — 전달된 필드만 부분 수정 */
    @Transactional
    public UserUpdateResponseDto updateProfile(Long userId, UserUpdateRequestDto request) {
        if (request.isEmpty()) {
            throw new GeneralException(ErrorCode.PROFILE_NO_UPDATE_FIELD);
        }

        User user = getActiveUser(userId);

        if (request.nickname() != null) {
            String nickname = request.nickname();
            if (!NICKNAME_PATTERN.matcher(nickname).matches()) {
                throw new GeneralException(ErrorCode.NICKNAME_INVALID_FORMAT);
            }
            if (userRepository.existsByNicknameAndIdNot(nickname, userId)) {
                throw new GeneralException(ErrorCode.NICKNAME_DUPLICATED);
            }
            user.changeNickname(nickname);
            // 동시 요청으로 위 존재 여부 검사를 함께 통과했을 경우를 대비해 즉시 flush 하여
            // DB unique 제약 위반을 여기서 확실하게 잡는다.
            try {
                userRepository.saveAndFlush(user);
            } catch (DataIntegrityViolationException e) {
                throw new GeneralException(ErrorCode.NICKNAME_DUPLICATED);
            }
        }

        if (request.profileImageUrl() != null) {
            String url = request.profileImageUrl();
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                throw new GeneralException(ErrorCode.PROFILE_IMAGE_INVALID);
            }
            user.changeProfileImageUrl(url);
        }

        // 영속 상태 엔티티라 변경 감지(dirty checking)로 트랜잭션 커밋 시 반영된다.
        return UserUpdateResponseDto.from(user);
    }

    /** [PATCH] /users/me/notifications */
    @Transactional
    public NotificationUpdateResponseDto updateNotification(Long userId, NotificationUpdateRequestDto request) {
        if (request.pushEnabled() == null) {
            throw new GeneralException(ErrorCode.NOTIFICATION_INVALID);
        }

        User user = getActiveUser(userId);
        user.changeNotificationEnabled(request.pushEnabled());

        return NotificationUpdateResponseDto.of(user.getNotificationsEnabled());
    }

    /**
     * 활성 사용자 조회.
     * soft-delete 로 탈퇴 사용자는 조회 자체가 안 되므로, 없으면 USER_NOT_FOUND.
     */
    private User getActiveUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(ErrorCode.USER_NOT_FOUND));
    }
}
