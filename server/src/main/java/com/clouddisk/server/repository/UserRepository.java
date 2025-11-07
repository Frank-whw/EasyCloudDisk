package com.clouddisk.server.repository;

import com.clouddisk.server.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.Optional;


/**
 * 用户数据访问层接口
 * 继承 JpaRepository 提供基础CURD操作
 */
@Repository
public interface UserRepository extends JpaRepository<User, String> {
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
     * 通过用户Id查找用户
     * @param user_id 用户Id
     * @return 用户对象（可选）
     */
    @Query("SELECT u from User u WHERE u.user_id = :user_id")
    Optional<User> findByUserId(String user_id);


}
