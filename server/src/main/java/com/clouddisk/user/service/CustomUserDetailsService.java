package com.clouddisk.user.service;

import com.clouddisk.user.repository.UserRepository;
import jakarta.persistence.Id;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * 自定义用户详情服务
 * 实现Spring Security的UserDetailsService接口
 * 用于从数据库加载用户信息进行认证
 */
@Service
@Transactional(readOnly = true)
public class CustomUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository;
    @Autowired
    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    /**
     * 根据用户邮箱加载用户详情
     * @param email 用户邮箱
     * @return 用户详情
     */
    @Override
    public UserDetails loadUserByUsername(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在"));
    }
    /**
     * 根据用户ID加载用户详情
     * @param user_id 用户ID
     * @return 用户详情
     */
    public UserDetails loadUserById(String user_id) {
        return userRepository.findByUserId(user_id)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在"));
    }

}
