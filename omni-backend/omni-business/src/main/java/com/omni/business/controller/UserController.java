package com.omni.business.controller;

import com.omni.common.result.R;
import com.omni.business.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * User management REST controller.
 * Provides CRUD endpoints for user resources.
 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/{id}")
    public R<Map<String, Object>> getUserById(@PathVariable Long id) {
        return R.ok(userService.getUserById(id));
    }

    @GetMapping("/list")
    public R<?> listUsers(@RequestParam(defaultValue = "1") int page,
                          @RequestParam(defaultValue = "10") int size) {
        return R.ok(userService.listUsers(page, size));
    }

    @PostMapping
    public R<Void> createUser(@Valid @RequestBody CreateUserRequest request) {
        userService.createUser(request.getUsername(), request.getEmail());
        return R.ok();
    }

    @Data
    public static class CreateUserRequest {
        @NotBlank(message = "Username is required")
        private String username;
        @NotBlank(message = "Email is required")
        private String email;
    }
}
