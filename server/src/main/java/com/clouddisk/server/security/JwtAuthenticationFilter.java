package com.clouddisk.server.security;

import com.clouddisk.server.service.CustomUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT认证过滤器
 * 拦截HTTP请求，验证JWT并设置安全上下文
 */
@Component // 注册为组件
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService customUserDetailsService;
    @Autowired // 自动注入
    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider,
                                   CustomUserDetailsService customUserDetailsService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.customUserDetailsService = customUserDetailsService;
    }

    /**
     * 过滤器方法
     * @param request 请求
     * @param response 响应
     * @param filterChain 过滤器链
     * @throws ServletException
     * @throws IOException
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            // 从请求中解析JWT令牌
            String token = getJwtFromRequest( request);

            // 如果令牌存在且有效
            if (token != null && jwtTokenProvider.validateToken(token)) {
                // 从令牌中获取用户ID
                String userId = jwtTokenProvider.getUserIdFromToken(token);

                // 加载用户详情
                UserDetails userDetails = customUserDetailsService.loadUserById(userId);

                // 创建认证对象
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

                // 设置认证详情
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // 设置Spring Security上下文
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception e) {
            logger.error("无法设置用户认证", e);
        }

        // 继续执行过滤器链
        filterChain.doFilter(request, response);

    }
    /**
     * 从HTTP请求中提取JWT令牌
     * @param request HTTP请求
     * @return JWT令牌
     */
    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
