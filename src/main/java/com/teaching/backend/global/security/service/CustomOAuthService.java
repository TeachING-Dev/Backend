package com.teaching.backend.global.security.service;

import com.teaching.backend.domain.auth.exception.AuthErrorCode;
import com.teaching.backend.domain.auth.exception.AuthException;
import com.teaching.backend.global.security.dto.GoogleDTO;
import com.teaching.backend.global.security.dto.KakaoDTO;
import com.teaching.backend.global.security.dto.OAuthDTO;
import com.teaching.backend.global.security.entity.OAuthMember;
import com.teaching.backend.domain.user.entity.Account;
import com.teaching.backend.domain.user.entity.User;
import com.teaching.backend.domain.user.enums.Provider;
import com.teaching.backend.domain.user.exception.UserErrorCode;
import com.teaching.backend.domain.user.exception.UserException;
import com.teaching.backend.domain.user.repository.AccountRepository;
import com.teaching.backend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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
            throw new AuthException(AuthErrorCode.NOT_SUPPORT_SOCIAL_PROVIDER);
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
                if (idAttribute == null) {
                    throw new AuthException(AuthErrorCode.SOCIAL_INFO_NOT_FOUND);
                }
                String socialUid = String.valueOf(idAttribute);

                String email = (String) kakaoAccount.get("email");
                String nickname = profile != null ? (String) profile.get("nickname") : null;

                if (email == null) {
                    throw new UserException(UserErrorCode.EMAIL_CONSENT_REQUIRED);
                }


                if (nickname == null || nickname.isBlank()) {
                    nickname = "사용자" + socialUid.substring(0, Math.min(6, socialUid.length()));
                }


                yield new KakaoDTO(socialUid, email, nickname);
            }
            case GOOGLE -> {
                Object subAttribute = oAuth2User.getAttribute("sub");
                if (subAttribute == null) {
                    throw new AuthException(AuthErrorCode.SOCIAL_INFO_NOT_FOUND);
                }
                String socialUid = String.valueOf(subAttribute);

                String email = oAuth2User.getAttribute("email");
                String nickname = oAuth2User.getAttribute("name");

                if (email == null) {
                    throw new UserException(UserErrorCode.EMAIL_CONSENT_REQUIRED);
                }

                if (nickname == null || nickname.isBlank()) {
                    nickname = "사용자" + socialUid.substring(0, Math.min(6, socialUid.length()));
                }

                yield new GoogleDTO(socialUid, email, nickname);
            }

            default -> throw new AuthException(AuthErrorCode.NOT_SUPPORT_SOCIAL_PROVIDER);
        };
    }

    private User registerNewUser(Provider provider, OAuthDTO dto) {
        User user = userRepository.findByEmail(dto.getEmail())
                .orElseGet(() -> userRepository.save(
                        User.create(dto.getEmail(), dto.getNickname(), null, null, null)
                ));

        try {
            Account account = Account.create(user, provider, dto.getProviderId());
            accountRepository.save(account);
        } catch (DataIntegrityViolationException e) {
            return accountRepository.findByProviderAndProviderAccountId(provider, dto.getProviderId())
                    .map(Account::getUser)
                    .orElseThrow(() -> e);
        }

        return user;
    }
}
