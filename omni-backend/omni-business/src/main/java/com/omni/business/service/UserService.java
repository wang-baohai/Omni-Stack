package com.omni.business.service;

import com.omni.common.result.PageResult;

import java.util.Map;

/**
 * User service interface.
 * Defines user-related business operations.
 */
public interface UserService {

    /**
     * Get a user by ID.
     *
     * @param id user ID
     * @return user data map
     */
    Map<String, Object> getUserById(Long id);

    /**
     * List users with pagination.
     *
     * @param page page number (1-based)
     * @param size page size
     * @return paginated user list
     */
    PageResult<Map<String, Object>> listUsers(int page, int size);

    /**
     * Create a new user.
     *
     * @param username username
     * @param email    email address
     */
    void createUser(String username, String email);
}
