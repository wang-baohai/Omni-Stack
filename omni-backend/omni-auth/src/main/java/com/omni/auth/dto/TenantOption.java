package com.omni.auth.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * Tenant option for the login tenant selector.
 */
@Data
@Builder
public class TenantOption implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private String code;
}
