package com.tracci.module.user.repository;

import com.tracci.module.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 绕过 MyBatis-Plus 逻辑删除过滤，按 id 读取用户（含已注销）。
 */
@Repository
@RequiredArgsConstructor
public class UserIncludeDeletedJdbc {

    private final JdbcTemplate jdbcTemplate;

    public User findById(String userId) {
        List<User> rows = jdbcTemplate.query(
                "SELECT * FROM users WHERE id = ?",
                new BeanPropertyRowMapper<>(User.class),
                userId);
        return rows.isEmpty() ? null : rows.get(0);
    }
}
