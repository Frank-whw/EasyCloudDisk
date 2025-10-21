package com.clouddisk.server.service;

import com.clouddisk.server.dto.AuthResponse;
import com.clouddisk.server.dto.AuthRequest;
import com.clouddisk.server.entity.User;
import com.clouddisk.server.repository.UserRepository;
import com.clouddisk.server.security.JwtTokenProvider;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 用户服务类
 * 处理用户认证相关的业务逻辑
 */
@Service
@Transactional // 开启事务
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    @Autowired
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtTokenProvider jwtTokenProvider, AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.authenticationManager = authenticationManager;
    }
    /**
     * 注册
     * @param authRequest 注册请求
     * @return 注册响应
     * @throws Exception 注册失败
     */
    public AuthResponse register(AuthRequest authRequest) throws  Exception{
        // 1. 检查邮箱是否已存在
        if (userRepository.existsByEmail(authRequest.getEmail())) {
            throw new Exception("邮箱已存在");
        }
        // 2. 创建新用户并加密密码
        String encodedPassword = passwordEncoder.encode(authRequest.getPassword());
        User user = new User(authRequest.getEmail(), encodedPassword);
        // 3. 保存用户到数据库
        User savedUser = userRepository.save(user);
        // 4. 生成JWT令牌
        String token = jwtTokenProvider.generateToken(savedUser.getUser_id().toString());
        // 5. 返回认证响应
        return new AuthResponse(token, savedUser.getUser_id().toString(), savedUser.getEmail());
    }
    /**
     * 登录
     * @param authRequest 登录请求
     * @return 登录响应
     * @throws Exception 登录失败
     */
    public AuthResponse login(AuthRequest authRequest) throws Exception {
        // 1. 验证用户凭据
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        authRequest.getEmail(),
                        authRequest.getPassword()
                )
        );
        // 2. 获取用户信息
        User user = userRepository.findByEmail(authRequest.getEmail())
                .orElseThrow(() -> new Exception("用户不存在"));
        // 3. 生成JWT令牌
        String token = jwtTokenProvider.generateToken(user.getUser_id().toString());
        // 4. 返回认证响应
        return new AuthResponse(token, user.getUser_id().toString(), user.getEmail());
    }
    /**
     * 获取当前用户信息
     * @param userId 用户ID
     * @return 用户信息
     * @throws Exception 用户不存在
     */
    @Transactional(readOnly = true) // 开启事务
    public Optional<User> findByUserId(String userId) throws Exception {
        return userRepository.findByUserId(userId);
    }
    /**
     * 根据邮箱查找用户
     * @param email 邮箱
     * @return 用户对象（可选）
     */
    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

}
