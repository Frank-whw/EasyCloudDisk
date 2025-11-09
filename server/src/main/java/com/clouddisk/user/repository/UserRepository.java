package com.clouddisk.user.repository;

import com.clouddisk.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
    @Query("SELECT u from User u WHERE u.email = :email")
    Optional<User> findByEmail(@Param("email") String email);
    /**
     * 通过邮箱判断用户是否存在
     * @param email 邮箱
     * @return 用户是否存在
     */
    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM User u WHERE u.email = :email")
    boolean existsByEmail(@Param("email") String email);

    /**
     * 通过用户Id查找用户
     * @param user_id 用户Id
     * @return 用户对象（可选）
     */
    @Query("SELECT u from User u WHERE u.user_id = :user_id")
    Optional<User> findByUserId(@Param("user_id") String user_id);


}