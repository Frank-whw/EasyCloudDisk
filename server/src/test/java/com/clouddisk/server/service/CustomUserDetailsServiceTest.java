package com.clouddisk.user.service;

import com.clouddisk.user.entity.User;
import com.clouddisk.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService customUserDetailsService;

    private User testUser;
    private String testEmail;
    private String testUserId;

    @BeforeEach
    void setUp() {
        testEmail = "test@example.com";
        testUserId = UUID.randomUUID().toString();
        
        testUser = new User(
            testEmail,
            "password123",
            "Test User"
        );
        testUser.setUserId(testUserId);
    }

    @Test
    void loadUserByUsername_ShouldReturnUserDetails_WhenUserExists() {
        // Arrange
        when(userRepository.findByEmail(testEmail))
            .thenReturn(Optional.of(testUser));

        // Act
        UserDetails result = customUserDetailsService.loadUserByUsername(testEmail);

        // Assert
        assertNotNull(result);
        assertEquals(testEmail, result.getUsername());
        assertEquals("password123", result.getPassword());
        
        verify(userRepository).findByEmail(testEmail);
    }

    @Test
    void loadUserByUsername_ShouldThrowUsernameNotFoundException_WhenUserNotFound() {
        // Arrange
        when(userRepository.findByEmail(testEmail))
            .thenReturn(Optional.empty());

        // Act & Assert
        UsernameNotFoundException exception = assertThrows(UsernameNotFoundException.class, () ->
            customUserDetailsService.loadUserByUsername(testEmail)
        );
        
        assertEquals("用户不存在", exception.getMessage());
        
        verify(userRepository).findByEmail(testEmail);
    }

    @Test
    void loadUserByUsername_ShouldThrowUsernameNotFoundException_WhenEmailIsNull() {
        // Act & Assert
        UsernameNotFoundException exception = assertThrows(UsernameNotFoundException.class, () ->
            customUserDetailsService.loadUserByUsername(null)
        );
        
        assertEquals("用户不存在", exception.getMessage());
        
        verify(userRepository).findByEmail(null);
    }

    @Test
    void loadUserByUsername_ShouldThrowUsernameNotFoundException_WhenEmailIsEmpty() {
        // Act & Assert
        UsernameNotFoundException exception = assertThrows(UsernameNotFoundException.class, () ->
            customUserDetailsService.loadUserByUsername("")
        );
        
        assertEquals("用户不存在", exception.getMessage());
        
        verify(userRepository).findByEmail("");
    }

    @Test
    void loadUserById_ShouldReturnUserDetails_WhenUserExists() {
        // Arrange
        when(userRepository.findByUserId(testUserId))
            .thenReturn(Optional.of(testUser));

        // Act
        UserDetails result = customUserDetailsService.loadUserById(testUserId);

        // Assert
        assertNotNull(result);
        assertEquals(testEmail, result.getUsername());
        assertEquals("password123", result.getPassword());
        
        verify(userRepository).findByUserId(testUserId);
    }

    @Test
    void loadUserById_ShouldThrowUsernameNotFoundException_WhenUserNotFound() {
        // Arrange
        when(userRepository.findByUserId(testUserId))
            .thenReturn(Optional.empty());

        // Act & Assert
        UsernameNotFoundException exception = assertThrows(UsernameNotFoundException.class, () ->
            customUserDetailsService.loadUserById(testUserId)
        );
        
        assertEquals("用户不存在", exception.getMessage());
        
        verify(userRepository).findByUserId(testUserId);
    }

    @Test
    void loadUserById_ShouldThrowUsernameNotFoundException_WhenUserIdIsNull() {
        // Act & Assert
        UsernameNotFoundException exception = assertThrows(UsernameNotFoundException.class, () ->
            customUserDetailsService.loadUserById(null)
        );
        
        assertEquals("用户不存在", exception.getMessage());
        
        verify(userRepository).findByUserId(null);
    }

    @Test
    void loadUserById_ShouldThrowUsernameNotFoundException_WhenUserIdIsEmpty() {
        // Act & Assert
        UsernameNotFoundException exception = assertThrows(UsernameNotFoundException.class, () ->
            customUserDetailsService.loadUserById("")
        );
        
        assertEquals("用户不存在", exception.getMessage());
        
        verify(userRepository).findByUserId("");
    }

    @Test
    void loadUserByUsername_ShouldHandleSpecialCharactersInEmail() {
        // Arrange
        String specialEmail = "test+special@example.com";
        User specialUser = new User(specialEmail, "password123", "Special User");
        specialUser.setUserId(UUID.randomUUID().toString());
        
        when(userRepository.findByEmail(specialEmail))
            .thenReturn(Optional.of(specialUser));

        // Act
        UserDetails result = customUserDetailsService.loadUserByUsername(specialEmail);

        // Assert
        assertNotNull(result);
        assertEquals(specialEmail, result.getUsername());
        
        verify(userRepository).findByEmail(specialEmail);
    }

    @Test
    void loadUserById_ShouldHandleUUIDFormat() {
        // Arrange
        String uuidUserId = UUID.randomUUID().toString();
        User uuidUser = new User("uuid@example.com", "password123", "UUID User");
        uuidUser.setUserId(uuidUserId);
        
        when(userRepository.findByUserId(uuidUserId))
            .thenReturn(Optional.of(uuidUser));

        // Act
        UserDetails result = customUserDetailsService.loadUserById(uuidUserId);

        // Assert
        assertNotNull(result);
        assertEquals("uuid@example.com", result.getUsername());
        
        verify(userRepository).findByUserId(uuidUserId);
    }
}