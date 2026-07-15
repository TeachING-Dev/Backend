package com.teaching.backend.global.security.service;

import com.teaching.backend.global.security.entity.AuthMember;
import com.teaching.backend.user.entity.User;
import com.teaching.backend.user.exception.UserErrorCode;
import com.teaching.backend.user.exception.UserException;
import com.teaching.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String userId) throws UsernameNotFoundException {
        User user = userRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
        return AuthMember.from(user);
    }
}

