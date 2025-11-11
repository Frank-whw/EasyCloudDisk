package com.clouddisk.user.repository;

import com.clouddisk.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;


/**
 * 用户数据访问层接口
 * 继承 JpaRepository 提供基础CURD操作
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    /**
     * 通过邮箱查找用户
     * @param email 邮箱
     * @return 用户
     */
    Optional<User> findByEmail(String email);
    
    /**
     * 通过邮箱判断用户是否存在
     * @param email 邮箱
     * @return 用户是否存在
     */
    boolean existsByEmail(String email);
    
    /**
     * 通过用户ID查找用户
     * @param userId 用户ID
     * @return 用户
     */
    Optional<User> findByUserId(UUID userId);


}