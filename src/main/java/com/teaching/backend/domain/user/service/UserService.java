package com.teaching.backend.domain.user.service;

import com.teaching.backend.domain.auth.service.AuthService;
import com.teaching.backend.domain.user.dto.NotificationUpdateRequestDto;
import com.teaching.backend.domain.user.dto.NotificationUpdateResponseDto;
import com.teaching.backend.domain.user.dto.TeacherPersonaUpdateRequestDto;
import com.teaching.backend.domain.user.dto.TeacherPersonaUpdateResponseDto;
import com.teaching.backend.domain.user.dto.UserInfoResponseDto;
import com.teaching.backend.domain.user.dto.UserUpdateRequestDto;
import com.teaching.backend.domain.user.dto.UserUpdateResponseDto;
import com.teaching.backend.domain.user.dto.UserWithdrawRequestDto;
import com.teaching.backend.domain.user.entity.Account;
import com.teaching.backend.domain.user.entity.User;
import com.teaching.backend.domain.user.entity.WithdrawalHistory;
import com.teaching.backend.domain.user.enums.TeacherPersona;
import com.teaching.backend.domain.user.enums.WithdrawalReason;
import com.teaching.backend.domain.user.exception.UserErrorCode;
import com.teaching.backend.domain.user.exception.UserException;
import com.teaching.backend.domain.user.repository.AccountRepository;
import com.teaching.backend.domain.user.repository.UserRepository;
import com.teaching.backend.domain.user.repository.WithdrawalHistoryRepository;
import com.teaching.backend.global.storage.s3.S3Uploader;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 마이페이지 도메인 서비스.
 *
 * "탈퇴 사용자 제외" 는 User 의 soft-delete(@SQLRestriction "deleted_at IS NULL")
 * 로 이미 처리되므로, findById 가 탈퇴 사용자를 반환하지 않는다.
 * 별도 isWithdrawn 플래그 검사는 두지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final WithdrawalHistoryRepository withdrawalHistoryRepository;
    private final AuthService authService;
    private final S3Uploader s3Uploader;

    /** 탈퇴 사유 상세 최대 길이 */
    private static final int WITHDRAWAL_REASON_DETAIL_MAX_LENGTH = 500;

    /** S3 프로필 이미지 저장 디렉토리 */
    private static final String PROFILE_IMAGE_DIRECTORY = "profile-images";

    /** 닉네임: 2~10자의 한글/영문/숫자 */
    private static final Pattern NICKNAME_PATTERN = Pattern.compile("^[가-힣a-zA-Z0-9]{2,10}$");

    /** 업로드 허용 이미지 포맷 (서버에서 실제 바이트를 디코딩해 판별한 값만 통과) */
    private static final Set<String> ALLOWED_IMAGE_FORMATS = Set.of("png", "jpeg", "gif", "webp");

    /** [GET] /users/me */
    public UserInfoResponseDto getMyInfo(Long userId) {
        User user = getActiveUser(userId);
        List<Account> accounts = accountRepository.findAllByUser(user);
        return UserInfoResponseDto.from(user, accounts);
    }

    /** [PATCH] /users/me — 전달된 필드만 부분 수정 */
    @Transactional
    public UserUpdateResponseDto updateProfile(Long userId, UserUpdateRequestDto request) {
        if (request == null || request.isEmpty()) {
            throw new UserException(UserErrorCode.PROFILE_NO_UPDATE_FIELD);
        }

        User user = getActiveUser(userId);

        if (request.hasNickname()) {
            String nickname = request.nickname();
            if (!NICKNAME_PATTERN.matcher(nickname).matches()) {
                throw new UserException(UserErrorCode.NICKNAME_INVALID_FORMAT);
            }
            if (userRepository.existsByNicknameAndIdNot(nickname, userId)) {
                throw new UserException(UserErrorCode.NICKNAME_DUPLICATED);
            }
            user.changeNickname(nickname);
            // 동시 요청으로 위 존재 여부 검사를 함께 통과했을 경우를 대비해 즉시 flush 하여
            // DB unique 제약 위반을 여기서 확실하게 잡는다.
            try {
                userRepository.saveAndFlush(user);
            } catch (DataIntegrityViolationException e) {
                throw new UserException(UserErrorCode.NICKNAME_DUPLICATED);
            }
        }

        // S3 업로드는 되돌릴 수 없으므로, 나머지 필드 검증을 모두 마친 뒤 마지막에 실행한다.
        // (검증 실패로 트랜잭션이 롤백되어도 orphan 객체가 S3에 남지 않도록)
        String verifiedImageFormat = request.hasProfileImage()
                ? validateAndResolveImageFormat(request.profileImage())
                : null;

        LocalDate birthday = request.hasAnyBirthField() ? parseBirthday(request) : null;

        if (verifiedImageFormat != null) {
            String uploadedUrl = s3Uploader.upload(request.profileImage(), PROFILE_IMAGE_DIRECTORY, verifiedImageFormat);
            user.changeProfileImageUrl(uploadedUrl);
        }

        if (birthday != null) {
            user.changeBirthday(birthday);
        }

        // 영속 상태 엔티티라 변경 감지(dirty checking)로 트랜잭션 커밋 시 반영된다.
        return UserUpdateResponseDto.from(user);
    }

    /**
     * 클라이언트가 보낸 Content-Type 은 신뢰하지 않고, 실제 파일 바이트를 디코딩해
     * 이미지 포맷을 판별한다. 디코딩이 불가능하거나 허용 목록에 없으면 PROFILE_IMAGE_TYPE_INVALID.
     */
    private String validateAndResolveImageFormat(MultipartFile file) {
        try (ImageInputStream iis = ImageIO.createImageInputStream(file.getInputStream())) {
            if (iis == null) {
                throw new UserException(UserErrorCode.PROFILE_IMAGE_TYPE_INVALID);
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                throw new UserException(UserErrorCode.PROFILE_IMAGE_TYPE_INVALID);
            }
            String formatName = readers.next().getFormatName().toLowerCase();
            String format = "jpg".equals(formatName) ? "jpeg" : formatName;
            if (!ALLOWED_IMAGE_FORMATS.contains(format)) {
                throw new UserException(UserErrorCode.PROFILE_IMAGE_TYPE_INVALID);
            }
            return format;
        } catch (IOException e) {
            throw new UserException(UserErrorCode.PROFILE_IMAGE_TYPE_INVALID);
        }
    }

    /** 년/월/일 중 일부만 채워졌으면 BIRTHDATE_INCOMPLETE, 실존하지 않는 날짜면 BIRTHDATE_INVALID. */
    private LocalDate parseBirthday(UserUpdateRequestDto request) {
        if (!request.hasAllBirthFields()) {
            throw new UserException(UserErrorCode.BIRTHDATE_INCOMPLETE);
        }

        try {
            return LocalDate.of(request.birthYear(), request.birthMonth(), request.birthDay());
        } catch (DateTimeException e) {
            throw new UserException(UserErrorCode.BIRTHDATE_INVALID);
        }
    }

    /** [PATCH] /users/me/notifications */
    @Transactional
    public NotificationUpdateResponseDto updateNotification(Long userId, NotificationUpdateRequestDto request) {
        if (request == null || request.pushEnabled() == null) {
            throw new UserException(UserErrorCode.NOTIFICATION_INVALID);
        }

        User user = getActiveUser(userId);
        user.changeNotificationEnabled(request.pushEnabled());

        return NotificationUpdateResponseDto.of(user.getNotificationsEnabled());
    }

    /** [PATCH] /users/me/teacher-persona */
    @Transactional
    public TeacherPersonaUpdateResponseDto updateTeacherPersona(Long userId, TeacherPersonaUpdateRequestDto request) {
        if (request == null || request.persona() == null) {
            throw new UserException(UserErrorCode.TEACHER_PERSONA_INVALID);
        }

        TeacherPersona persona;
        try {
            persona = TeacherPersona.valueOf(request.persona());
        } catch (IllegalArgumentException e) {
            throw new UserException(UserErrorCode.TEACHER_PERSONA_INVALID);
        }

        User user = getActiveUser(userId);
        user.changeTeacherPersona(persona);

        return TeacherPersonaUpdateResponseDto.of(user.getTeacherPersona());
    }

    /**
     * [DELETE] /users/me — 회원 탈퇴 (soft-delete). 탈퇴 사유를 별도 이력 테이블에 저장한다.
     * refreshToken 무효화까지 같은 트랜잭션으로 묶어, 탈퇴 이후 토큰만 남는 상태를 방지한다
     * (쿠키 만료는 응답 단계의 부수 효과라 컨트롤러에서 별도로 처리한다).
     */
    @Transactional
    public void withdraw(Long userId, UserWithdrawRequestDto request) {
        WithdrawalReason reason = parseWithdrawalReason(request.reason());
        if (reason == WithdrawalReason.ETC
                && (request.reasonDetail() == null || request.reasonDetail().isBlank())) {
            throw new UserException(UserErrorCode.WITHDRAWAL_REASON_DETAIL_REQUIRED);
        }
        if (request.reasonDetail() != null && request.reasonDetail().length() > WITHDRAWAL_REASON_DETAIL_MAX_LENGTH) {
            throw new UserException(UserErrorCode.WITHDRAWAL_REASON_DETAIL_TOO_LONG);
        }
        if (!Boolean.TRUE.equals(request.isConfirmed())) {
            throw new UserException(UserErrorCode.WITHDRAWAL_NOT_CONFIRMED);
        }

        User user = getActiveUser(userId);
        withdrawalHistoryRepository.save(WithdrawalHistory.of(userId, reason, request.reasonDetail()));

        // 탈퇴 후 소셜 연동 정보 역시 삭제
        List<Account> accounts = accountRepository.findAllByUser(user);
        accountRepository.deleteAll(accounts);

        user.delete();
        authService.revokeRefreshToken(userId);
    }

    private WithdrawalReason parseWithdrawalReason(String reason) {
        if (reason == null) {
            throw new UserException(UserErrorCode.WITHDRAWAL_REASON_REQUIRED);
        }
        try {
            return WithdrawalReason.valueOf(reason);
        } catch (IllegalArgumentException e) {
            throw new UserException(UserErrorCode.WITHDRAWAL_REASON_REQUIRED);
        }
    }

    /**
     * 활성 사용자 조회.
     * soft-delete 로 탈퇴 사용자는 조회 자체가 안 되므로, 없으면 USER_NOT_FOUND.
     */
    private User getActiveUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
    }
}
