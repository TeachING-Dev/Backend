package com.teaching.backend.global.init;

import com.teaching.backend.user.entity.Account;
import com.teaching.backend.user.entity.User;
import com.teaching.backend.user.enums.Gender;
import com.teaching.backend.user.enums.Provider;
import com.teaching.backend.user.repository.AccountRepository;
import com.teaching.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 개발용 초기 데이터.
 *
 * 아직 회원가입/로그인이 없어서 조회할 사용자가 없으므로,
 * local 프로파일에서 users 테이블이 비어 있으면 데모 사용자(id=1)를 하나 만든다.
 * DevCurrentUserProvider 가 id=1 을 현재 사용자로 반환하므로, 이 사용자가 /users/me 로 조회된다.
 *
 * main User.create 는 gender 가 필수이고, main 에는 User→Account 컬렉션/cascade 가 없으므로
 * 계정은 AccountRepository 로 직접 저장한다. Account.create 는 providerAccountId 가 필수라 더미 값을 넣는다.
 *
 * TODO: 회원가입 기능이 붙으면 이 클래스는 삭제.
 */
@Component
@Profile("local")
@RequiredArgsConstructor
public class DevDataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.count() > 0) {
            return;
        }

        User user = User.create(
                "devtalk@teaching.com",
                "김태이",
                LocalDate.of(2001, 3, 15),
                Gender.MALE,
                "https://cdn.teaching.com/profile/100.png"
        );
        userRepository.save(user);

        accountRepository.save(Account.create(user, Provider.GOOGLE, "google-dev-100"));
        accountRepository.save(Account.create(user, Provider.KAKAO, "kakao-dev-100"));
    }
}
