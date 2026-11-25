package com.tracci.module.auth.repository;

import com.tracci.module.auth.entity.UserAuthIdentity;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class UserAuthIdentityIncludeDeletedJdbc {

    private final JdbcTemplate jdbcTemplate;

    public List<UserAuthIdentity> findByUserId(String userId) {
        return jdbcTemplate.query(
                "SELECT * FROM user_auth_identities WHERE user_id = ?",
                new BeanPropertyRowMapper<>(UserAuthIdentity.class),
                userId);
    }
}
