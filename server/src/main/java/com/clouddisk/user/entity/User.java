package com.clouddisk.user.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.GrantedAuthority;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Collection;
import java.util.UUID;

/**
 *  用户实体类
 */
@Entity // 映射为数据库表
@Table(name = "users") // 指定表名
@Data // 自动生成getter和setter方法
public class User implements UserDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_id", length = 36)
    private String user_id;

    @Column(unique = true, nullable = false, name = "email")
    private String email;

    @Column(nullable = false, name = "password_hash")
    private String password_hash;

    @Column(name = "created_at")
    private LocalDateTime created_at;

    @Column(name = "updated_at")
    private LocalDateTime updated_at;

    public User() {}
    public User(String email, String password_hash){
        this.user_id = UUID.randomUUID().toString();
        this.email = email;
        this.password_hash = password_hash;
        this.created_at = LocalDateTime.now();
        this.updated_at = LocalDateTime.now();
    }
    @PrePersist
    protected void onCreate() {
        if (this.user_id == null) {
            this.user_id = UUID.randomUUID().toString();
        }
        this.created_at = LocalDateTime.now();
        this.updated_at = LocalDateTime.now();
    }
    @PreUpdate
    protected void onUpdate() {
        this.updated_at = LocalDateTime.now();
    }

    // UserDetails接口实现
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.emptyList(); // 暂时不实现角色权限
    }

    @Override
    public String getPassword() {
        return password_hash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}