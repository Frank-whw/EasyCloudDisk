package com.clouddisk.common.config;

import com.clouddisk.common.security.JwtAuthenticationFilter;
import com.clouddisk.user.service.CustomUserDetailsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import org.springframework.http.HttpMethod;

/**
 * Spring Security 安全配置类
 * 配置认证、授权、CORS 等
 */
@EnableWebSecurity // 启用 Web 安全性
@Configuration // 启用 Spring Security
public class SecurityConfig {
    private final CustomUserDetailsService customUserDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Autowired // 自动注入
    public SecurityConfig(CustomUserDetailsService customUserDetailsService, JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.customUserDetailsService = customUserDetailsService;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    /**
     * 密码编码器Bean
     * @return Bcrypt密码编码器
     */
    @Bean // 创建密码编码器 Bean
    public PasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder();
        // 返回 BCryptPasswordEncoder 实例，提供安全的密码哈希功能
    }

    /**
     * 认证提供者Bean
     * @return DAO认证提供者
     */
    @Bean
    public AuthenticationProvider authenticationProvider(){
        // 1. 创建DaoAuthenticationProvider
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        // 2. 设置UserDetailsService
        authProvider.setUserDetailsService(customUserDetailsService);
        // 3. 设置密码编码器
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    /**
     * 认证管理器Bean
     * @param config 认证配置
     * @return 认证管理器
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * 安全过滤器链Bean
     * @param http Http安全配置
     * @return 安全过滤器链
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 1. 禁用CSRF保护（REST API通常不需要）
                .csrf(csrf -> csrf.disable())
                // 2. 配置CORS
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // 3. 设置会话管理为无状态
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 4. 配置请求授权规则
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/auth/register").permitAll()
                        .requestMatchers("/auth/**").permitAll() // 允许所有人访问认证相关接口
                        .anyRequest().authenticated() // 其他所有请求都需要认证
                )
                // 5. 设置认证提供者
                .authenticationProvider(authenticationProvider())
                // 6. 添加JWT过滤器
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
    /**
     * CORS配置源Bean
     * @return CORS配置源
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // 1. 设置允许的源（可通过配置或环境变量注入；默认允许所有）
        configuration.setAllowedOriginPatterns(Arrays.asList("*"));
        // 2. 设置允许的HTTP方法
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        // 3. 设置允许的请求头
        configuration.setAllowedHeaders(Arrays.asList("*"));
        // 4. 设置是否允许携带凭证信息（如cookies）
        configuration.setAllowCredentials(true);
        // 5. 设置暴露的响应头（如Authorization）
        configuration.setExposedHeaders(Arrays.asList("Authorization", "Content-Disposition"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
