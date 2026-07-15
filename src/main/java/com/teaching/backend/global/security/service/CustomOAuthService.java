package com.teaching.backend.global.security.service;

import com.teaching.backend.global.security.dto.KakaoDTO;
import com.teaching.backend.global.security.dto.OAuthDTO;
import com.teaching.backend.global.security.entity.OAuthMember;
import com.teaching.backend.user.entity.Account;
import com.teaching.backend.user.entity.User;
import com.teaching.backend.user.enums.Provider;
import com.teaching.backend.user.exception.UserErrorCode;
import com.teaching.backend.user.exception.UserException;
import com.teaching.backend.user.repository.AccountRepository;
import com.teaching.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class CustomOAuthService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        Provider provider;
        try {
            provider = Provider.valueOf(
                    userRequest.getClientRegistration().getRegistrationId().toUpperCase()
            );
        } catch (IllegalArgumentException e) {
            throw new UserException(UserErrorCode.NOT_SUPPORT_SOCIAL_PROVIDER);
        }

        OAuthDTO dto = extractDTO(provider, oAuth2User);

        User user = accountRepository.findByProviderAndProviderAccountId(provider, dto.getProviderId())
                .map(Account::getUser)
                .orElseGet(() -> registerNewUser(provider, dto));

        return OAuthMember.from(user, oAuth2User.getAttributes());
    }

    private OAuthDTO extractDTO(Provider provider, OAuth2User oAuth2User) {
        return switch (provider) {
            case KAKAO -> {
                Map<String, Object> kakaoAccount = oAuth2User.getAttribute("kakao_account");
                if (kakaoAccount == null) {
                    throw new UserException(UserErrorCode.SOCIAL_INFO_NOT_FOUND);
                }
                Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile");

                Object idAttribute = oAuth2User.getAttribute("id");
                String socialUid = String.valueOf(idAttribute);

                String email = (String) kakaoAccount.get("email");
                String nickname = profile != null ? (String) profile.get("nickname") : null;

                if (email == null) {
                    throw new UserException(UserErrorCode.EMAIL_CONSENT_REQUIRED);
                }

                yield new KakaoDTO(socialUid, email, nickname);
            }
            // case GOOGLE -> { ... GoogleDTO 추가 시 여기 확장 ... }
            default -> throw new UserException(UserErrorCode.NOT_SUPPORT_SOCIAL_PROVIDER);
        };
    }

    private User registerNewUser(Provider provider, OAuthDTO dto) {
        // 다른 provider로 이미 가입된 email이면 계정 병합 여부 정책 필요
        User user = userRepository.findByEmail(dto.getEmail())
                .orElseGet(() -> userRepository.save(
                        User.create(dto.getEmail(), dto.getNickname(), null, null, null)
                        // birthday, gender, profileImageUrl은 카카오/구글이 안 주는 경우가 많아
                        // 회원가입 추가정보 입력 단계에서 채우는 흐름이면 여기선 null 허용
                ));

        Account account = Account.create(user, provider, dto.getProviderId());
        accountRepository.save(account);

        return user;
    }
}