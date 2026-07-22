package com.teaching.backend.domain.auth.service;

import com.teaching.backend.domain.auth.dto.SignupRequest;
import com.teaching.backend.domain.auth.exception.AuthErrorCode;
import com.teaching.backend.domain.auth.exception.AuthException;
import com.teaching.backend.domain.term.entity.Term;
import com.teaching.backend.domain.term.entity.UserTerm;
import com.teaching.backend.domain.term.exception.TermErrorCode;
import com.teaching.backend.domain.term.exception.TermException;
import com.teaching.backend.domain.term.repository.TermRepository;
import com.teaching.backend.domain.term.repository.UserTermRepository;
import com.teaching.backend.domain.user.entity.User;
import com.teaching.backend.domain.user.exception.UserErrorCode;
import com.teaching.backend.domain.user.exception.UserException;
import com.teaching.backend.domain.user.repository.UserRepository;
import com.teaching.backend.global.security.entity.AuthMember;
import com.teaching.backend.global.security.util.JwtUtil;
import com.teaching.backend.domain.auth.entity.RefreshToken;
import com.teaching.backend.domain.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final JwtUtil jwtUtil;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenHasher tokenHasher;

    private final UserRepository userRepository;
    private final TermRepository termRepository;
    private final UserTermRepository userTermRepository;

    @Transactional
    public String reissueAccessToken(String refreshToken) {
        if (!jwtUtil.isValid(refreshToken)) {
            throw new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        String tokenHash = tokenHasher.hash(refreshToken);

        RefreshToken savedToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new AuthException(AuthErrorCode.REFRESH_TOKEN_NOT_FOUND));

        if (savedToken.isExpired()) {
            throw new AuthException(AuthErrorCode.EXPIRED_REFRESH_TOKEN);
        }

        AuthMember authMember = AuthMember.from(savedToken.getUser());
        return jwtUtil.createAccessToken(authMember);
    }

    /**
     * 로그아웃: 쿠키로 전달된 refreshToken에 해당하는 row 삭제. 없거나 이미 삭제된 경우도 정상 처리(멱등).
     * 조회 후 삭제하는 대신 조건부 DELETE 쿼리를 바로 실행해, 동시 로그아웃 요청에서 0건 삭제가
     * StaleStateException 으로 이어지는 것을 방지한다.
     */
    @Transactional
    public void logout(String refreshToken) {
        String tokenHash = tokenHasher.hash(refreshToken);
        refreshTokenRepository.deleteByTokenHash(tokenHash);
    }

    /** 회원 탈퇴 시 해당 사용자의 refreshToken을 무효화한다. */
    @Transactional
    public void revokeRefreshToken(Long userId) {
        refreshTokenRepository.deleteByUser_Id(userId);
    }

    //닉네임 검증 로직

    public void validateNickname(String nickname) {
        if (nickname == null || nickname.isBlank() || nickname.length() > 10) {
            throw new UserException(UserErrorCode.NICKNAME_INVALID_FORMAT);
        }
        if (userRepository.existsByNickname(nickname)) {
            throw new UserException(UserErrorCode.NICKNAME_DUPLICATED);
        }
    }

    //회원가입
    @Transactional
    public void signup(Long userId, SignupRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));

        validateNickname(request.nickname());

        List<Term> requiredTerms = termRepository.findAllByIsRequiredTrue();
        List<Long> requiredTermIds = requiredTerms.stream().map(Term::getId).toList();

        if (!request.agreedTermIds().containsAll(requiredTermIds)) {
            throw new TermException(TermErrorCode.REQUIRED_TERM_NOT_AGREED);
        }

        user.changeNickname(request.nickname());

        List<Term> agreedTerms = termRepository.findAllById(request.agreedTermIds());
        for (Term term : agreedTerms) {
            userTermRepository.save(UserTerm.agree(user, term));
        }
    }
}