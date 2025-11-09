package com.clouddisk.server.service;

import com.clouddisk.exception.BusinessException;
import com.clouddisk.server.dto.AuthRequest;
import com.clouddisk.server.dto.AuthResponse;
import com.clouddisk.server.entity.User;
import com.clouddisk.server.repository.UserRepository;
import com.clouddisk.server.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * UserService单元测试
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private UserService userService;

    private AuthRequest validAuthRequest;
    private User testUser;
    private String testUserId;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID().toString();
        validAuthRequest = new AuthRequest("test@example.com", "password123");
        testUser = new User("test@example.com", "encodedPassword");
        testUser.setUser_id(testUserId);
    }

    @Test
    void register_ShouldSuccess_WhenEmailNotExists() {
        // 准备
        when(userRepository.existsByEmail(validAuthRequest.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(validAuthRequest.getPassword())).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(jwtTokenProvider.generateToken(testUserId)).thenReturn("jwtToken");

        // 执行
        AuthResponse response = userService.register(validAuthRequest);

        // 验证
        assertNotNull(response);
        assertEquals("jwtToken", response.getToken());
        assertEquals(testUserId, response.getUserId());
        assertEquals("test@example.com", response.getEmail());
        
        verify(userRepository).existsByEmail(validAuthRequest.getEmail());
        verify(passwordEncoder).encode(validAuthRequest.getPassword());
        verify(userRepository).save(any(User.class));
        verify(jwtTokenProvider).generateToken(testUserId);
    }

    @Test
    void register_ShouldThrowException_WhenEmailExists() {
        // 准备
        when(userRepository.existsByEmail(validAuthRequest.getEmail())).thenReturn(true);

        // 执行和验证
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            userService.register(validAuthRequest);
        });

        assertEquals("邮箱已存在", exception.getMessage());
        assertEquals(409, exception.getCode());
        
        verify(userRepository).existsByEmail(validAuthRequest.getEmail());
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void login_ShouldSuccess_WhenCredentialsValid() {
        // 准备
        when(userRepository.findByEmail(validAuthRequest.getEmail())).thenReturn(Optional.of(testUser));
        when(jwtTokenProvider.generateToken(testUserId)).thenReturn("jwtToken");
        
        // 执行
        AuthResponse response = userService.login(validAuthRequest);

        // 验证
        assertNotNull(response);
        assertEquals("jwtToken", response.getToken());
        assertEquals(testUserId, response.getUserId());
        assertEquals("test@example.com", response.getEmail());
        
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(userRepository).findByEmail(validAuthRequest.getEmail());
        verify(jwtTokenProvider).generateToken(testUserId);
    }

    @Test
    void login_ShouldThrowException_WhenUserNotFound() {
        // 准备
        when(userRepository.findByEmail(validAuthRequest.getEmail())).thenReturn(Optional.empty());

        // 执行和验证
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            userService.login(validAuthRequest);
        });

        assertEquals("用户不存在", exception.getMessage());
        assertEquals(404, exception.getCode());
        
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(userRepository).findByEmail(validAuthRequest.getEmail());
        verify(jwtTokenProvider, never()).generateToken(anyString());
    }

    @Test
    void findByUserId_ShouldReturnUser_WhenUserExists() {
        // 准备
        when(userRepository.findByUserId(testUserId)).thenReturn(Optional.of(testUser));

        // 执行
        Optional<User> result = userService.findByUserId(testUserId);

        // 验证
        assertTrue(result.isPresent());
        assertEquals(testUser, result.get());
        verify(userRepository).findByUserId(testUserId);
    }

    @Test
    void findByUserId_ShouldReturnEmpty_WhenUserNotExists() {
        // 准备
        when(userRepository.findByUserId(testUserId)).thenReturn(Optional.empty());

        // 执行
        Optional<User> result = userService.findByUserId(testUserId);

        // 验证
        assertFalse(result.isPresent());
        verify(userRepository).findByUserId(testUserId);
    }

    @Test
    void findByEmail_ShouldReturnUser_WhenUserExists() {
        // 准备
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

        // 执行
        Optional<User> result = userService.findByEmail("test@example.com");

        // 验证
        assertTrue(result.isPresent());
        assertEquals(testUser, result.get());
        verify(userRepository).findByEmail("test@example.com");
    }

    @Test
    void findByEmail_ShouldReturnEmpty_WhenUserNotExists() {
        // 准备
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());

        // 执行
        Optional<User> result = userService.findByEmail("test@example.com");

        // 验证
        assertFalse(result.isPresent());
        verify(userRepository).findByEmail("test@example.com");
    }
}