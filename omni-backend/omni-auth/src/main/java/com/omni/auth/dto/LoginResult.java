package com.omni.auth.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * Login response containing JWT access token.
 */
@Data
@Builder
public class LoginResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private String accessToken;
    private String tokenType;
    private long expiresIn;
}
