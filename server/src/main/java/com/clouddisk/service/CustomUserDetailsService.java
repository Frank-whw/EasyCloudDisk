package com.clouddisk.service;

import com.clouddisk.entity.User;
import com.clouddisk.exception.BusinessException;
import com.clouddisk.exception.ErrorCode;
import com.clouddisk.repository.UserRepository;
import com.clouddisk.security.UserPrincipal;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Spring Security 所需的用户信息加载服务。
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Cacheable(value = "users", key = "#root.args[0]")
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (username == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        
        User user = null;

        // 如果包含@，认为是邮箱（登录流程）
        if (username.contains("@")) {
            user = userRepository.findByEmailIgnoreCase(username.toLowerCase())
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        } else {
            // 否则认为是userId（JWT认证流程）
            // 注意：如果username不是有效的UUID格式，findById可能会抛出异常，所以这里可以加个try-catch或者让它抛出
            try {
                user = userRepository.findById(username)
                        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
            } catch (IllegalArgumentException e) {
                // 如果不是有效的UUID，且不包含@，可能是一个无效的用户名
                throw new BusinessException(ErrorCode.USER_NOT_FOUND);
            }
        }
        
        return new UserPrincipal(user);
    }
}
