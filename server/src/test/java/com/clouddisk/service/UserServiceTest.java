package com.clouddisk.service;

import com.clouddisk.dto.AuthRequest;
import com.clouddisk.dto.AuthResponse;
import com.clouddisk.dto.RegisterRequest;
import com.clouddisk.entity.User;
import com.clouddisk.exception.BusinessException;
import com.clouddisk.exception.ErrorCode;
import com.clouddisk.repository.UserRepository;
import com.clouddisk.security.JwtTokenProvider;
import com.clouddisk.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * UserService 单元测试
 * 测试用户注册、登录等认证相关业务逻辑
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private UserService userService;

    private String userId;
    private User testUser;
    private RegisterRequest registerRequest;
    private AuthRequest authRequest;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID().toString();
        testUser = new User();
        testUser.setUserId(userId);
        testUser.setEmail("test@example.com");
        testUser.setPasswordHash("encodedPassword");

        registerRequest = new RegisterRequest();
        registerRequest.setEmail("test@example.com");
        registerRequest.setPassword("password123");

        authRequest = new AuthRequest();
        authRequest.setEmail("test@example.com");
        authRequest.setPassword("password123");
    }

    @Test
    void testRegister_Success() {
        // Given
        when(userRepository.findByEmailIgnoreCase("test@example.com"))
                .thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            if (user.getUserId() == null) {
                user.setUserId(UUID.randomUUID().toString());
            }
            return user;
        });
        when(tokenProvider.generateToken(anyString(), any(Map.class))).thenReturn("testToken");
        when(tokenProvider.generateRefreshToken(anyString())).thenReturn("refreshToken");

        // When
        AuthResponse result = userService.register(registerRequest);

        // Then
        assertNotNull(result);
        assertNotNull(result.getToken());
        assertNotNull(result.getUserId());
        verify(userRepository, times(1)).findByEmailIgnoreCase("test@example.com");
        verify(passwordEncoder, times(1)).encode("password123");
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void testRegister_EmailAlreadyExists_ThrowsException() {
        // Given
        when(userRepository.findByEmailIgnoreCase("test@example.com"))
                .thenReturn(Optional.of(testUser));

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            userService.register(registerRequest);
        });
        assertEquals(ErrorCode.EMAIL_EXISTS, exception.getErrorCode());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testRegister_EmailCaseInsensitive() {
        // Given
        registerRequest.setEmail("TEST@EXAMPLE.COM");
        when(userRepository.findByEmailIgnoreCase("test@example.com"))
                .thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setUserId(UUID.randomUUID().toString());
            return user;
        });
        when(tokenProvider.generateToken(anyString(), any(Map.class))).thenReturn("testToken");
        when(tokenProvider.generateRefreshToken(anyString())).thenReturn("refreshToken");

        // When
        AuthResponse result = userService.register(registerRequest);

        // Then
        assertNotNull(result);
        // 验证邮箱被转换为小写
        verify(userRepository).findByEmailIgnoreCase("test@example.com");
    }

    @Test
    void testLogin_Success() {
        // Given
        Authentication authentication = mock(Authentication.class);
        User user = new User();
        user.setUserId(userId);
        user.setEmail("test@example.com");
        UserPrincipal principal = new UserPrincipal(user);
        
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(tokenProvider.generateToken(anyString(), any(Map.class))).thenReturn("testToken");
        when(tokenProvider.generateRefreshToken(anyString())).thenReturn("refreshToken");

        // When
        AuthResponse result = userService.login(authRequest);

        // Then
        assertNotNull(result);
        assertNotNull(result.getToken());
        assertEquals("testToken", result.getToken());
        assertEquals(userId, result.getUserId());
        verify(authenticationManager, times(1))
                .authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    void testLogin_InvalidCredentials_ThrowsException() {
        // Given
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            userService.login(authRequest);
        });
        assertEquals(ErrorCode.INVALID_CREDENTIALS, exception.getErrorCode());
        verify(userRepository, never()).findById(anyString());
    }

    @Test
    void testLogin_EmailCaseInsensitive() {
        // Given
        authRequest.setEmail("TEST@EXAMPLE.COM");
        Authentication authentication = mock(Authentication.class);
        User user = new User();
        user.setUserId(userId);
        user.setEmail("test@example.com");
        UserPrincipal principal = new UserPrincipal(user);
        
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(tokenProvider.generateToken(anyString(), any(Map.class))).thenReturn("testToken");
        when(tokenProvider.generateRefreshToken(anyString())).thenReturn("refreshToken");

        // When
        AuthResponse result = userService.login(authRequest);

        // Then
        assertNotNull(result);
        // 验证邮箱被转换为小写进行认证
        verify(authenticationManager).authenticate(argThat(token -> 
            token.getPrincipal().equals("test@example.com")));
    }
}

