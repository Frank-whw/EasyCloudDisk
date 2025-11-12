package com.clouddisk.server.service;

import com.clouddisk.server.entity.User;
import com.clouddisk.server.exception.BusinessException;
import com.clouddisk.server.exception.ErrorCode;
import com.clouddisk.server.repository.UserRepository;
import com.clouddisk.server.security.UserPrincipal;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Cacheable(value = "users", key = "#root.args[0]")
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String normalized = username == null ? null : username.toLowerCase();
        User user = userRepository.findById(username)
                .or(() -> userRepository.findByEmailIgnoreCase(normalized))
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return new UserPrincipal(user);
    }
}
