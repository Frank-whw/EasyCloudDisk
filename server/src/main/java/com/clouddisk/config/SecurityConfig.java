package com.clouddisk.config;

import com.clouddisk.security.JwtAuthenticationEntryPoint;
import com.clouddisk.security.JwtAuthenticationFilter;
import com.clouddisk.security.RestAccessDeniedHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Spring Security 核心配置，负责配置 JWT 认证、CORS 以及常规安全拦截策略。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;
    private final String allowedOrigins;
    private final boolean allowCredentials;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          JwtAuthenticationEntryPoint authenticationEntryPoint,
                          RestAccessDeniedHandler accessDeniedHandler,
                          @Value("${app.cors.allowed-origins:http://localhost:3000}") String allowedOrigins,
                          @Value("${app.cors.allow-credentials:false}") boolean allowCredentials) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.allowedOrigins = allowedOrigins;
        this.allowCredentials = allowCredentials;
    }

    /**
     * 定义系统的安全过滤器链，接入 JWT 过滤器并配置授权规则。
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/auth/**", "/health", "/actuator/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/files/directories").authenticated()
                        .requestMatchers("/files/**").authenticated()
                        .anyRequest().authenticated())
                .logout(logout -> logout.logoutUrl("/auth/logout").logoutSuccessHandler((request, response, authentication) -> response.setStatus(200)))
                .headers(headers -> headers.frameOptions(frame -> frame.disable()));

        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * 使用 BCrypt 作为密码编码器。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 从 Spring Security 提供的配置中暴露 {@link AuthenticationManager}。
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    /**
     * 根据配置构建 CORS 规则，支持通配符与凭证控制。
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
        if (origins.isEmpty()) {
            origins = List.of("http://localhost:3000");
        }

        boolean usesPattern = origins.stream().anyMatch(origin -> origin.contains("*"));
        if (usesPattern) {
            configuration.setAllowedOriginPatterns(origins);
            configuration.setAllowCredentials(false);
        } else {
            configuration.setAllowedOrigins(origins);
            configuration.setAllowCredentials(allowCredentials);
        }

        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
        configuration.setExposedHeaders(List.of("Content-Disposition"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
