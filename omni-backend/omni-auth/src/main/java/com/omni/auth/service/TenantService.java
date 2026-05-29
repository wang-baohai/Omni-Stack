package com.omni.auth.service;

import com.omni.auth.dto.TenantOption;

import java.util.List;

/**
 * Tenant listing service.
 */
public interface TenantService {

    /**
     * List all active tenants.
     *
     * @return list of tenant options for the login selector
     */
    List<TenantOption> listActiveTenants();
}
