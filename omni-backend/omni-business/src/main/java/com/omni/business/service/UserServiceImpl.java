package com.omni.business.service;

import com.omni.common.result.PageResult;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User service implementation.
 * Provides stub implementations until database integration is added.
 * When adding a database layer (e.g., spring-boot-starter-jdbc), add
 * {@code @Transactional(readOnly = true)} on read methods and
 * {@code @Transactional} on write methods.
 */
@Service
public class UserServiceImpl implements UserService {

    @Override
    public Map<String, Object> getUserById(Long id) {
        // TODO: [business] Replace with actual database query
        Map<String, Object> user = new HashMap<>(16);
        user.put("id", id);
        user.put("username", "demo");
        user.put("email", "demo@example.com");
        return user;
    }

    @Override
    public PageResult<Map<String, Object>> listUsers(int page, int size) {
        // TODO: [business] Replace with actual database query
        Map<String, Object> user = new HashMap<>(16);
        user.put("id", 1L);
        user.put("username", "demo");
        user.put("email", "demo@example.com");
        return new PageResult<>(List.of(user), 1, size, page);
    }

    @Override
    public void createUser(String username, String email) {
        // TODO: [business] Replace with actual database insert
    }
}
